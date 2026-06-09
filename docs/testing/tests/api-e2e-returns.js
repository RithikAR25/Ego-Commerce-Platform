const http = require('http');
const crypto = require('crypto');
const { execSync } = require('child_process');

const BASE_URL = 'http://localhost:8080/api/v1';
const WEBHOOK_SECRET = 'ego_razorpay_webhook_secret_2026';

async function fetchAPI(endpoint, method = 'GET', body = null, token = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL(`${BASE_URL}${endpoint}`);
        const options = {
            hostname: url.hostname, port: url.port, path: url.pathname + url.search, method,
            headers: { 'Content-Type': 'application/json', ...headers }
        };
        if (token) options.headers['Authorization'] = `Bearer ${token}`;
        const req = http.request(options, (res) => {
            let data = ''; res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try { resolve({ status: res.statusCode, data: data ? JSON.parse(data) : {} }); }
                catch(e) { resolve({ status: res.statusCode, data }); }
            });
        }).on('error', reject);
        if (body) req.write(typeof body === 'string' ? body : JSON.stringify(body));
        req.end();
    });
}

function generateSignature(payload, secret) {
    const hmac = crypto.createHmac('sha256', secret);
    hmac.update(payload, 'utf8');
    return hmac.digest('hex');
}

async function createOrder(userToken, adminToken, ts) {
    await fetchAPI('/cart', 'DELETE', null, userToken);
    await fetchAPI('/cart/add', 'POST', { variantId: 1, quantity: 1 }, userToken);
    let chkRes = await fetchAPI('/orders/checkout', 'POST', { shippingAddress: "123 St" }, userToken);
    let oId = chkRes.data.data.id;
    let pay = await fetchAPI('/payments/razorpay/create', 'POST', { orderId: oId }, userToken);
    let hook = JSON.stringify({
        event: "payment.captured",
        payload: { payment: { entity: { order_id: pay.data.data.razorpayOrderId, id: `pay_mock_${ts}` } } }
    });
    await fetchAPI('/webhooks/razorpay', 'POST', hook, null, { 'X-Razorpay-Signature': generateSignature(hook, WEBHOOK_SECRET) });
    return oId;
}

/** Set the DELIVERED status history entry for an order to N days ago */
function backdateDelivery(orderId, daysAgo) {
    execSync(`docker exec rawego mysql -uroot -proot rawego -e "UPDATE order_status_history SET created_at = DATE_SUB(NOW(), INTERVAL ${daysAgo} DAY) WHERE order_id = ${orderId} AND status = 'DELIVERED';"`);
}

async function runTests() {
    let passed = 0, failed = 0;
    function assert(condition, message) { if (condition) { console.log(`✅ PASS: ${message}`); passed++; } else { console.error(`❌ FAIL: ${message}`); failed++; } }

    try {
        const ts = Date.now();
        await fetchAPI('/auth/register', 'POST', { email: `ret${ts}@test.com`, password: 'Password@123', firstName: 'Ret', lastName: 'User' });
        execSync(`docker exec rawego mysql -uroot -proot rawego -e "UPDATE users SET is_email_verified = 1 WHERE email = 'ret${ts}@test.com';"`);
        
        const loginRes = await fetchAPI('/auth/login', 'POST', { email: `ret${ts}@test.com`, password: 'Password@123' });
        const token = loginRes.data.data.accessToken;

        const adminLoginRes = await fetchAPI('/auth/login', 'POST', { email: 'Admin@ego.com', password: 'Admin@123' });
        const adminToken = adminLoginRes.data.data.accessToken;

        // Restock inventory
        execSync('docker exec rawego mysql -uroot -proot rawego -e "UPDATE inventory_records SET quantity_available = 1000;"');

        console.log("--- Setup ---");

        // Order 1: CONFIRMED (not delivered) — for RET-002
        let o1 = await createOrder(token, adminToken, ts);
        
        // Order 2: DELIVERED (just now) — for RET-001, RET-004, RET-005, RET-007
        let o2 = await createOrder(token, adminToken, ts+1);
        await fetchAPI(`/admin/orders/${o2}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${o2}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${o2}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);

        // Order 3: DELIVERED 8 days ago — RET-003: expired (must reject)
        let o3 = await createOrder(token, adminToken, ts+2);
        await fetchAPI(`/admin/orders/${o3}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${o3}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${o3}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);
        backdateDelivery(o3, 8);

        // Order 5: DELIVERED 6 days ago — boundary: within window (must accept)
        let o5 = await createOrder(token, adminToken, ts+4);
        await fetchAPI(`/admin/orders/${o5}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${o5}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${o5}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);
        backdateDelivery(o5, 6);

        // Order 6: DELIVERED exactly 7 days ago — boundary edge: must accept (condition is > 7, not >= 7)
        let o6 = await createOrder(token, adminToken, ts+5);
        await fetchAPI(`/admin/orders/${o6}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${o6}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${o6}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);
        backdateDelivery(o6, 7);

        console.log("\n--- Running Returns API Tests ---");

        // RET-002: Cannot Return Non-Delivered Order
        let retRes2 = await fetchAPI(`/orders/${o1}/returns`, 'POST', { reason: 'DEFECTIVE', description: 'Broken' }, token);
        assert(retRes2.status === 409 || retRes2.status === 400, `RET-002: Cannot Return Non-Delivered Order (Status ${retRes2.status})`);

        // RET-003: Return Window Expired (8 days — must reject)
        let retRes3 = await fetchAPI(`/orders/${o3}/returns`, 'POST', { reason: 'SIZE_ISSUE', description: 'Too small' }, token);
        assert(retRes3.status === 409 || retRes3.status === 400, `RET-003: Return Window Expired - 8 days (Status ${retRes3.status})`);

        // RET-003b: Boundary — 6 days (must accept)
        let retRes3b = await fetchAPI(`/orders/${o5}/returns`, 'POST', { reason: 'SIZE_ISSUE', description: 'Too small' }, token);
        assert(retRes3b.status === 201 || retRes3b.status === 200, `RET-003b: Return Window - 6 days within window (Status ${retRes3b.status})`);

        // RET-003c: Boundary — exactly 7 days (must accept: condition is > 7, not >= 7)
        let retRes3c = await fetchAPI(`/orders/${o6}/returns`, 'POST', { reason: 'SIZE_ISSUE', description: 'Too small' }, token);
        assert(retRes3c.status === 201 || retRes3c.status === 200, `RET-003c: Return Window - exactly 7 days still within window (Status ${retRes3c.status})`);

        // RET-001: Initiate Return (Happy Path — just delivered)
        let retRes1 = await fetchAPI(`/orders/${o2}/returns`, 'POST', { reason: 'NOT_AS_DESCRIBED', description: 'Looks different' }, token);
        assert(retRes1.status === 201 || retRes1.status === 200, `RET-001: Initiate Return (Status ${retRes1.status})`);
        const returnId = retRes1.data.data ? retRes1.data.data.id : null;

        // RET-004: Duplicate Return Rejected
        let retRes4 = await fetchAPI(`/orders/${o2}/returns`, 'POST', { reason: 'DEFECTIVE', description: 'Wait returning again' }, token);
        assert(retRes4.status === 409 || retRes4.status === 400, `RET-004: Duplicate Return Rejected (Status ${retRes4.status})`);

        // RET-005: View Return Status
        let retRes5 = await fetchAPI(`/orders/${o2}/returns`, 'GET', null, token);
        assert(retRes5.status === 200 && retRes5.data.data.id !== undefined, `RET-005: View Return Status (Status ${retRes5.status})`);

        // RET-007: Admin: Reject Return
        if(returnId) {
            let retRes7 = await fetchAPI(`/admin/returns/${returnId}/review`, 'PUT', { approve: false, rejectionReason: "No" }, adminToken);
            assert(retRes7.status === 200, `RET-007: Admin: Reject Return (Status ${retRes7.status})`);
        } else {
            assert(false, `RET-007: Return ID not found`);
        }

        // Create order 4 for Approval (RET-006)
        execSync('docker exec rawego mysql -uroot -proot rawego -e "UPDATE inventory_records SET quantity_available = 1000;"');
        let o4 = await createOrder(token, adminToken, ts+3);
        await fetchAPI(`/admin/orders/${o4}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${o4}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${o4}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);
        let retRes6a = await fetchAPI(`/orders/${o4}/returns`, 'POST', { reason: 'DEFECTIVE', description: 'Broken' }, token);
        const returnId2 = retRes6a.data.data ? retRes6a.data.data.id : null;

        // RET-006: Admin: Approve Return and Initiate Refund
        if(returnId2) {
            let retRes6b = await fetchAPI(`/admin/returns/${returnId2}/review`, 'PUT', { approve: true, refundAmount: 2999 }, adminToken);
            assert(retRes6b.status === 200, `RET-006: Admin: Approve Return (Status ${retRes6b.status})`);
        } else {
            assert(false, `RET-006: Return ID not found`);
        }

        console.log(`\nResults: ${passed} passed, ${failed} failed.`);
    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runTests();
