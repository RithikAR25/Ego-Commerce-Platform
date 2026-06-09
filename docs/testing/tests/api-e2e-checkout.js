const http = require('http');

const BASE_URL = 'http://localhost:8080/api/v1';

async function fetchAPI(endpoint, method = 'GET', body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(`${BASE_URL}${endpoint}`);
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: method,
            headers: {
                'Content-Type': 'application/json'
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
                    if (res.statusCode >= 400) {
                        reject(new Error(`API Error ${res.statusCode}: ${data}`));
                    } else {
                        resolve(parsed);
                    }
                } catch(e) {
                    reject(new Error(`JSON Parse Error: ${e.message} - Data: ${data}`));
                }
            });
        }).on('error', reject);
        
        if (body) {
            req.write(JSON.stringify(body));
        }
        req.end();
    });
}

function assert(condition, id, message) {
    if (condition) {
        console.log(`✅ [PASS] ${id}: ${message}`);
        return true;
    } else {
        console.error(`❌ [FAIL] ${id}: ${message}`);
        return false;
    }
}

async function runCheckoutTests() {
    console.log('\n--- Running Checkout API Tests ---');
    
    try {
        // 1. Login as Customer
        const loginRes = await fetchAPI('/auth/login', 'POST', {
            email: 'arikothanrithik@gmail.com',
            password: 'Rithik@1234'
        });
        const token = loginRes.data.accessToken;
        
        // 2. Clear Cart (in case of leftovers)
        await fetchAPI('/cart', 'DELETE', null, token);
        
        // 3. Add Item to Cart
        await fetchAPI('/cart/add', 'POST', { variantId: 1, quantity: 1 }, token);
        
        // 4. Checkout
        let checkoutRes;
        try {
            checkoutRes = await fetchAPI('/orders/checkout', 'POST', {
                shippingAddress: "123 Main St, Test City"
            }, token);
            assert(checkoutRes.data && checkoutRes.data.id, 'CHK-001', 'Complete Checkout works');
        } catch(e) {
            assert(false, 'CHK-001', `Failed to checkout: ${e.message}`);
            return;
        }

        const orderId = checkoutRes.data.id;

        // 5. Get Order Detail
        try {
            const orderRes = await fetchAPI(`/orders/${orderId}`, 'GET', null, token);
            assert(orderRes.data.id === orderId, 'CHK-003', 'View Order Details works');
        } catch(e) {
            assert(false, 'CHK-003', `Failed to get order details: ${e.message}`);
        }

        // 6. Login as Admin
        const adminLoginRes = await fetchAPI('/auth/login', 'POST', {
            email: 'Admin@ego.com',
            password: 'Admin@123'
        });
        const adminToken = adminLoginRes.data.accessToken;

        // 7. Update Order Status
        try {
            const statusRes = await fetchAPI(`/admin/orders/${orderId}/status`, 'PUT', {
                status: 'CONFIRMED'
            }, adminToken);
            assert(statusRes.data.status === 'CONFIRMED', 'ORD-001', 'Admin can update order status');
        } catch(e) {
            assert(false, 'ORD-001', `Admin failed to update order status: ${e.message}`);
        }

        // 8. Order List
        try {
            const ordersList = await fetchAPI('/orders', 'GET', null, token);
            assert(ordersList.data.content.length > 0, 'ORD-002', 'User can view order history');
        } catch(e) {
            assert(false, 'ORD-002', `Failed to view order history: ${e.message}`);
        }
        
    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runCheckoutTests();
