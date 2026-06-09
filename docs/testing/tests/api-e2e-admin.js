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

async function runAdminTests() {
    console.log('\n--- Running Admin API Tests (Category Depth) ---');
    
    try {
        // 1. Login as Admin
        const loginRes = await fetchAPI('/auth/login', 'POST', {
            email: 'Admin@ego.com',
            password: 'Admin@123'
        });
        const token = loginRes.data.accessToken;

        const randCode = () => String.fromCharCode(65+Math.floor(Math.random()*26)) + String.fromCharCode(65+Math.floor(Math.random()*26)) + String.fromCharCode(65+Math.floor(Math.random()*26)) + String.fromCharCode(65+Math.floor(Math.random()*26));
        
        // CAT-DEPTH-001: Create ROOT
        let rootCategory;
        try {
            const res = await fetchAPI('/admin/categories', 'POST', {
                name: "Test Root " + Date.now(),
                slug: "test-root-" + Date.now(),
                code: randCode(),
                description: "Test Root",
                isActive: true
            }, token);
            rootCategory = res.data;
            assert(rootCategory.level === 'ROOT', 'CAT-DEPTH-001', 'Create category with no parent -> ROOT created');
        } catch(e) {
            assert(false, 'CAT-DEPTH-001', e.message);
        }

        // CAT-DEPTH-002: Create GROUP under ROOT
        let groupCategory;
        try {
            const res = await fetchAPI('/admin/categories', 'POST', {
                name: "Test Group " + Date.now(),
                slug: "test-group-" + Date.now(),
                code: randCode(),
                description: "Test Group",
                parentIds: [rootCategory.id],
                isActive: true
            }, token);
            groupCategory = res.data;
            assert(groupCategory.level === 'GROUP', 'CAT-DEPTH-002', 'Create with ROOT parent -> GROUP created');
        } catch(e) {
            assert(false, 'CAT-DEPTH-002', e.message);
        }

        // CAT-DEPTH-003: Create LEAF under GROUP
        let leafCategory;
        try {
            const res = await fetchAPI('/admin/categories', 'POST', {
                name: "Test Leaf " + Date.now(),
                slug: "test-leaf-" + Date.now(),
                code: randCode(),
                description: "Test Leaf",
                parentIds: [groupCategory.id],
                isActive: true
            }, token);
            leafCategory = res.data;
            assert(leafCategory.level === 'LEAF', 'CAT-DEPTH-003', 'Create with GROUP parent -> LEAF created');
        } catch(e) {
            assert(false, 'CAT-DEPTH-003', e.message);
        }

        // CAT-DEPTH-004: Create under LEAF -> 400
        try {
            await fetchAPI('/admin/categories', 'POST', {
                name: "Test Invalid " + Date.now(),
                slug: "test-invalid-" + Date.now(),
                code: randCode(),
                description: "Invalid",
                parentIds: [leafCategory.id],
                isActive: true
            }, token);
            assert(false, 'CAT-DEPTH-004', 'Allowed creating category under LEAF!');
        } catch(e) {
            if (e.message.includes('400')) {
                assert(true, 'CAT-DEPTH-004', 'Create with LEAF parent -> 400 Validation Error');
            } else {
                assert(false, 'CAT-DEPTH-004', e.message);
            }
        }

        // Create Product Payload Template
        const getProductPayload = (categoryId) => ({
            name: "Test Product " + Date.now(),
            slug: "test-product-" + Date.now(),
            description: "A test product",
            categoryId: categoryId,
            tags: ["test"],
            status: "DRAFT"
        });

        // CAT-DEPTH-005: Product assigned to ROOT -> 400
        try {
            await fetchAPI('/admin/products', 'POST', getProductPayload(rootCategory.id), token);
            assert(false, 'CAT-DEPTH-005', 'Allowed product under ROOT!');
        } catch(e) {
            if (e.message.includes('400') || e.message.includes('409') || e.message.includes('Validation')) {
                assert(true, 'CAT-DEPTH-005', 'Product assigned to ROOT -> 400 Validation Error');
            } else {
                assert(false, 'CAT-DEPTH-005', e.message);
            }
        }

        // CAT-DEPTH-006: Product assigned to GROUP -> 400
        try {
            await fetchAPI('/admin/products', 'POST', getProductPayload(groupCategory.id), token);
            assert(false, 'CAT-DEPTH-006', 'Allowed product under GROUP!');
        } catch(e) {
            if (e.message.includes('400') || e.message.includes('409') || e.message.includes('Validation')) {
                assert(true, 'CAT-DEPTH-006', 'Product assigned to GROUP -> 400 Validation Error');
            } else {
                assert(false, 'CAT-DEPTH-006', e.message);
            }
        }

        // CAT-DEPTH-007: Product assigned to LEAF -> 201
        try {
            await fetchAPI('/admin/products', 'POST', getProductPayload(leafCategory.id), token);
            assert(true, 'CAT-DEPTH-007', 'Product assigned to LEAF -> 201 Success');
        } catch(e) {
            assert(false, 'CAT-DEPTH-007', e.message);
        }

    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runAdminTests();
