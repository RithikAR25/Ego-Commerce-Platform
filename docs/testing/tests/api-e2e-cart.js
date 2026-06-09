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
                    const parsed = JSON.parse(data);
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

async function runCartTests() {
    console.log('\n--- Running Cart API Tests ---');
    
    try {
        // 1. Login as Customer
        const loginRes = await fetchAPI('/auth/login', 'POST', {
            email: 'arikothanrithik@gmail.com',
            password: 'Rithik@1234'
        });
        const token = loginRes.data.accessToken;
        
        // 2. Add Item to Cart
        let cartRes;
        try {
            cartRes = await fetchAPI('/cart/add', 'POST', {
                variantId: 1,
                quantity: 2
            }, token);
            assert(cartRes && cartRes.data, 'CART-001', 'Add Item to Cart works');
        } catch(e) {
            assert(false, 'CART-001', `Failed to add to cart: ${e.message}`);
        }

        // 3. Maximum 10 units per line
        try {
            await fetchAPI('/cart/add', 'POST', {
                variantId: 1,
                quantity: 11
            }, token);
            assert(false, 'CART-002', 'Maximum 10 Units Per Line restriction not enforced');
        } catch(e) {
            assert(e.message.includes('400') || e.message.includes('10'), 'CART-002', 'Maximum 10 Units Per Line enforced');
        }

        // 4. Update Cart Quantity
        try {
            const updateRes = await fetchAPI('/cart/items/1', 'PUT', {
                quantity: 3
            }, token);
            assert(updateRes.data.items[0].quantity === 3, 'CART-005', 'Update Cart Quantity works');
        } catch(e) {
            assert(false, 'CART-005', `Failed to update cart: ${e.message}`);
        }

        // 5. Remove Item from Cart
        try {
            const delRes = await fetchAPI('/cart/items/1', 'DELETE', null, token);
            assert(delRes.data.items.length === 0, 'CART-004', 'Remove Item from Cart works');
        } catch(e) {
            assert(false, 'CART-004', `Failed to remove from cart: ${e.message}`);
        }

        // 6. Guest Cannot Add to Cart
        try {
            await fetchAPI('/cart/add', 'POST', {
                variantId: 1,
                quantity: 1
            });
            assert(false, 'CART-007', 'Guest Cannot Add to Cart - Restriction failed');
        } catch (e) {
            assert(e.message.includes('401'), 'CART-007', 'Guest Cannot Add to Cart - 401 Returned');
        }

    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runCartTests();
