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
                    if (res.statusCode >= 400) {
                        reject(new Error(`API Error ${res.statusCode}: ${data}`));
                    } else {
                        resolve(parsed);
                    }
                } catch(e) {
                    if (res.statusCode >= 200 && res.statusCode < 300) {
                        resolve(data); // e.g. empty response
                    } else {
                        reject(new Error(`API Error ${res.statusCode}: ${data}`));
                    }
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
    console.log('\n--- Running Payment Webhook API Tests ---');
    
    try {
        // 1. Login as Customer
        const loginRes = await fetchAPI('/auth/login', 'POST', {
            email: 'arikothanrithik@gmail.com',
            password: 'Rithik@1234'
        });
        const token = loginRes.data.accessToken;

        // 2. Clear Cart & Add Item
        await fetchAPI('/cart', 'DELETE', null, token);
        await fetchAPI('/cart/add', 'POST', { variantId: 1, quantity: 1 }, token);

        // 3. Checkout
        let checkoutRes = await fetchAPI('/orders/checkout', 'POST', { shippingAddress: "123 Main St" }, token);
        const egoOrderId = checkoutRes.data.id;
        console.log(`✅ Created Order: ${egoOrderId}`);

        // 4. Create Razorpay Payment Order
        let paymentRes;
        try {
            paymentRes = await fetchAPI('/payments/razorpay/create', 'POST', { orderId: egoOrderId }, token);
            console.log(`✅ [PASS] PAY-001: Create Razorpay Order`);
        } catch(e) {
            console.log(`❌ [FAIL] PAY-001: ${e.message}`);
            return;
        }

        // 5. Test Webhook Validation (Invalid Signature)
        const mockWebhookPayload = JSON.stringify({
            event: "payment.captured",
            payload: {
                payment: {
                    entity: {
                        order_id: paymentRes.data.razorpayOrderId,
                        id: "pay_mock123"
                    }
                }
            }
        });

        try {
            await fetchAPI('/webhooks/razorpay', 'POST', mockWebhookPayload, null, {
                'X-Razorpay-Signature': 'invalid_signature'
            });
            console.log(`❌ [FAIL] PAY-004: Webhook Signature Verification failed (it allowed an invalid signature!)`);
        } catch(e) {
            if (e.message.includes('400')) {
                console.log(`✅ [PASS] PAY-004: Webhook Signature Verification correctly rejected invalid signature`);
            } else {
                console.log(`❌ [FAIL] PAY-004: Unexpected error: ${e.message}`);
            }
        }

        // 6. Test Valid Webhook
        const validSignature = generateSignature(mockWebhookPayload, WEBHOOK_SECRET);
        try {
            await fetchAPI('/webhooks/razorpay', 'POST', mockWebhookPayload, null, {
                'X-Razorpay-Signature': validSignature
            });
            console.log(`✅ [PASS] Webhook successfully processed`);
        } catch(e) {
            console.log(`❌ [FAIL] Valid webhook failed: ${e.message}`);
        }

        // 7. Verify Order Status advanced to CONFIRMED
        try {
            const orderRes = await fetchAPI(`/orders/${egoOrderId}`, 'GET', null, token);
            if (orderRes.data.status === 'CONFIRMED') {
                console.log(`✅ [PASS] Order status successfully advanced to CONFIRMED`);
            } else {
                console.log(`❌ [FAIL] Order status is still ${orderRes.data.status}`);
            }
        } catch(e) {
            console.log(`❌ [FAIL] Could not verify order status: ${e.message}`);
        }

        // 8. Test Webhook Idempotency (Send same valid webhook again)
        try {
            await fetchAPI('/webhooks/razorpay', 'POST', mockWebhookPayload, null, {
                'X-Razorpay-Signature': validSignature
            });
            console.log(`✅ [PASS] PAY-005: Webhook Idempotency (Duplicate Event handled safely)`);
        } catch(e) {
            console.log(`❌ [FAIL] PAY-005: Duplicate Webhook caused error: ${e.message}`);
        }

        // 9. Test Non-Captured events ignored
        const ignorePayload = JSON.stringify({
            event: "payment.failed",
            payload: {
                payment: {
                    entity: {
                        order_id: paymentRes.data.razorpayOrderId,
                        id: "pay_mock123"
                    }
                }
            }
        });
        const ignoreSig = generateSignature(ignorePayload, WEBHOOK_SECRET);
        try {
            await fetchAPI('/webhooks/razorpay', 'POST', ignorePayload, null, {
                'X-Razorpay-Signature': ignoreSig
            });
            console.log(`✅ [PASS] PAY-006: Non-Captured Events are Ignored gracefully`);
        } catch(e) {
            console.log(`❌ [FAIL] PAY-006: ${e.message}`);
        }

    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runTests();
