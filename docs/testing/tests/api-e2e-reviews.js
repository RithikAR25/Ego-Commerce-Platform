const http = require('http');
const crypto = require('crypto');

const BASE_URL = 'http://localhost:8080/api/v1';
const WEBHOOK_SECRET = 'ego_razorpay_webhook_secret_2026';

async function fetchAPI(endpoint, method = 'GET', body = null, token = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL(`${BASE_URL}${endpoint}`);
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: {
                'Content-Type': 'application/json',
                ...headers
            }
        };
        
        if (token) {
            options.headers['Authorization'] = `Bearer ${token}`;
        }
        
        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = data ? JSON.parse(data) : {};
                    resolve({ status: res.statusCode, data: parsed });
                } catch(e) {
                    resolve({ status: res.statusCode, data });
                }
            });
        }).on('error', reject);
        
        if (body) {
            req.write(typeof body === 'string' ? body : JSON.stringify(body));
        }
        req.end();
    });
}

function generateSignature(payload, secret) {
    const hmac = crypto.createHmac('sha256', secret);
    hmac.update(payload, 'utf8');
    return hmac.digest('hex');
}

async function runTests() {
    let passed = 0;
    let failed = 0;

    function assert(condition, message) {
        if (condition) {
            console.log(`✅ PASS: ${message}`);
            passed++;
        } else {
            console.error(`❌ FAIL: ${message}`);
            failed++;
        }
    }

    try {
        console.log("--- Setup: Creating DELIVERED Order ---");
        // 1. Login as Customer
        const loginRes = await fetchAPI('/auth/login', 'POST', { email: 'arikothanrithik@gmail.com', password: 'Rithik@1234' });
        const token = loginRes.data.data.accessToken;

        // 2. Login as Admin
        const adminLoginRes = await fetchAPI('/auth/login', 'POST', { email: 'Admin@ego.com', password: 'Admin@123' });
        const adminToken = adminLoginRes.data.data.accessToken;

        // 3. Clear Cart & Add Item
        await fetchAPI('/cart', 'DELETE', null, token);
        await fetchAPI('/cart/add', 'POST', { variantId: 1, quantity: 1 }, token);

        // 4. Checkout
        let checkoutRes = await fetchAPI('/orders/checkout', 'POST', { shippingAddressId: 1 }, token);
        if(checkoutRes.status !== 201) {
             // Let's create an address first just in case
             await fetchAPI('/addresses', 'POST', {
                fullName: "Test User", phone: "9876543210", addressLine1: "123 Main St", city: "Test", state: "TS", pinCode: "123456", country: "IN", isDefault: true
             }, token);
             checkoutRes = await fetchAPI('/orders/checkout', 'POST', { shippingAddressId: 1 }, token);
             // Note: Address ID might not be 1. We will use the free text address.
             if(checkoutRes.status !== 201) {
                  checkoutRes = await fetchAPI('/orders/checkout', 'POST', { shippingAddress: "123 Main St, New York" }, token);
             }
        }
        const egoOrderId = checkoutRes.data.data.id;
        const productId = 1;
        console.log(`Created Order: ${egoOrderId}, Product: ${productId}`);

        // 5. Create Razorpay Order
        const paymentRes = await fetchAPI('/payments/razorpay/create', 'POST', { orderId: egoOrderId }, token);
        
        // 6. Webhook
        const mockWebhookPayload = JSON.stringify({
            event: "payment.captured",
            payload: { payment: { entity: { order_id: paymentRes.data.data.razorpayOrderId, id: "pay_mock123" } } }
        });
        const validSignature = generateSignature(mockWebhookPayload, WEBHOOK_SECRET);
        await fetchAPI('/webhooks/razorpay', 'POST', mockWebhookPayload, null, { 'X-Razorpay-Signature': validSignature });

        // 7. Admin advances status to DELIVERED
        await fetchAPI(`/admin/orders/${egoOrderId}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${egoOrderId}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${egoOrderId}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);
        console.log(`Order status advanced to DELIVERED.`);

        console.log("\n--- Running Reviews API Tests ---");

        // REV-001: Verified Purchaser Can Review
        let revRes = await fetchAPI(`/products/${productId}/reviews`, 'POST', { rating: 5, reviewText: "Great product!" }, token);
        assert(revRes.status === 201, `REV-001: Verified Purchaser Can Review (Status ${revRes.status})`);
        const reviewId = revRes.data.data ? revRes.data.data.id : null;

        // REV-002: Non-Purchaser Cannot Review
        const ts = Date.now();
        await fetchAPI('/auth/register', 'POST', {
            email: `dummy${ts}@test.com`, password: 'Password@123', firstName: 'Dummy', lastName: 'User'
        });
        // verify email
        await fetchAPI(`/auth/login`, 'POST', {}); // wait
        const dummyTokenRes = await fetchAPI('/auth/login', 'POST', { email: `dummy${ts}@test.com`, password: 'Password@123' });
        const dummyToken = dummyTokenRes.data.data.accessToken;
        let revRes2 = await fetchAPI(`/products/${productId}/reviews`, 'POST', { rating: 4, reviewText: "Nice!" }, dummyToken);
        assert(revRes2.status === 403 || revRes2.status === 400 || revRes2.status === 409, `REV-002: Non-Purchaser Cannot Review (Status ${revRes2.status})`);

        // REV-003: Duplicate Review Rejected
        let revRes3 = await fetchAPI(`/products/${productId}/reviews`, 'POST', { rating: 4, reviewText: "Wait I want to review again." }, token);
        assert(revRes3.status === 409 || revRes3.status === 400, `REV-003: Duplicate Review Rejected (Status ${revRes3.status})`);

        // REV-004: Review Rating Bounds Enforcement
        // Since we can't delete and re-review easily, we'll try to review product 2 (not bought) with bad rating, or we can use another user.
        // Actually, schema validation fails before checking ownership.
        let revRes4 = await fetchAPI(`/products/${productId}/reviews`, 'POST', { rating: 6, reviewText: "Bad rating" }, dummyToken);
        assert(revRes4.status === 400, `REV-004: Review Rating Bounds Enforcement (Status ${revRes4.status})`);

        // REV-005: HtmlSanitizer Strips XSS
        const ts2 = Date.now() + 1;
        await fetchAPI('/auth/register', 'POST', {
            email: `buyer${ts2}@test.com`, password: 'Password@123', firstName: 'Buyer', lastName: 'User'
        });
        // verify user in db
        const { execSync } = require('child_process');
        execSync(`docker exec rawego mysql -uroot -proot rawego -e "UPDATE users SET is_email_verified = 1 WHERE email = 'buyer${ts2}@test.com';"`);
        
        const dummyTokenRes2 = await fetchAPI('/auth/login', 'POST', { email: `buyer${ts2}@test.com`, password: 'Password@123' });
        const dummyToken2 = dummyTokenRes2.data.data.accessToken;

        await fetchAPI('/cart/add', 'POST', { variantId: 1, quantity: 1 }, dummyToken2);
        let chkRes2 = await fetchAPI('/orders/checkout', 'POST', { shippingAddress: "456 Dummy St" }, dummyToken2);
        let o2 = chkRes2.data.data.id;
        let pay2 = await fetchAPI('/payments/razorpay/create', 'POST', { orderId: o2 }, dummyToken2);
        let hook2 = JSON.stringify({
            event: "payment.captured",
            payload: { payment: { entity: { order_id: pay2.data.data.razorpayOrderId, id: `pay_mock${ts2}` } } }
        });
        await fetchAPI('/webhooks/razorpay', 'POST', hook2, null, { 'X-Razorpay-Signature': generateSignature(hook2, WEBHOOK_SECRET) });
        await fetchAPI(`/admin/orders/${o2}/status`, 'PUT', { status: 'PROCESSING' }, adminToken);
        await fetchAPI(`/admin/orders/${o2}/status`, 'PUT', { status: 'SHIPPED' }, adminToken);
        await fetchAPI(`/admin/orders/${o2}/status`, 'PUT', { status: 'DELIVERED' }, adminToken);
        
        let revRes5 = await fetchAPI(`/products/${productId}/reviews`, 'POST', { rating: 5, reviewText: "Nice <script>alert(1)</script>" }, dummyToken2);
        assert(revRes5.status === 201 && revRes5.data.data.reviewText.indexOf('<script>') === -1, `REV-005: HtmlSanitizer Strips XSS`);

        // REV-006: Admin Deletes Review
        if(reviewId) {
            let delRes = await fetchAPI(`/admin/reviews/${reviewId}`, 'DELETE', null, adminToken);
            assert(delRes.status === 200, `REV-006: Admin Deletes Review (Status ${delRes.status})`);
        } else {
            assert(false, `REV-006: Could not find reviewId to delete`);
        }

        console.log(`\nResults: ${passed} passed, ${failed} failed.`);
    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runTests();
