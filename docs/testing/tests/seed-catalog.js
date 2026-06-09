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

async function runSeed() {
    console.log('\n--- Seeding Product Catalog ---');
    
    try {
        // 1. Login as Admin
        console.log("Logging in as Admin...");
        const loginRes = await fetchAPI('/auth/login', 'POST', {
            email: 'Admin@ego.com',
            password: 'Admin@123'
        });
        const token = loginRes.data.accessToken;
        
        if (!token) throw new Error("No access token returned");

        // 2. Create a Product
        console.log("Creating Premium Streetwear Hoodie...");
        const createProdReq = {
            name: 'Premium Streetwear Hoodie',
            slug: 'premium-streetwear-hoodie',
            description: 'Heavyweight oversized hoodie perfect for the streets.',
            categoryId: 501, // Gen Z Hoodies
            price: 2499.00,
            compareAtPrice: 3499.00,
            status: 'ACTIVE',
            isFeatured: true
        };
        
        let product;
        try {
            const createRes = await fetchAPI('/admin/products', 'POST', createProdReq, token);
            product = createRes.data;
            console.log(`✅ Product Created: ${product.id}`);
        } catch(e) {
            if (e.message.includes('409')) {
                console.log("Product already exists, fetching it...");
                const list = await fetchAPI('/products/premium-streetwear-hoodie', 'GET');
                product = list.data;
            } else {
                throw e;
            }
        }

        // 3. Add inventory/variants if necessary
        // Typically handled by InventoryController or VariantController depending on schema
        // The EGO platform uses variants. Let's see if we can create one.
        console.log("Creating Product Variant...");
        const createVariantReq = {
            productId: product.id,
            sku: 'HOODIE-BLK-L',
            size: 'L',
            color: 'Black',
            priceAdjustment: 0,
            stockQuantity: 100,
            lowStockThreshold: 10,
            status: 'ACTIVE'
        };
        
        try {
            const variantRes = await fetchAPI(`/admin/products/${product.id}/variants`, 'POST', createVariantReq, token);
            console.log(`✅ Variant Created: ${variantRes.data.id}`);
        } catch (e) {
            if (e.message.includes('409') || e.message.includes('Duplicate')) {
                 console.log("Variant already exists.");
            } else {
                 console.log("Variant creation failed/not applicable:", e.message);
            }
        }

        // 4. Force Elasticsearch Reindex
        console.log("Triggering Elasticsearch Reindex...");
        const reindexRes = await fetchAPI('/admin/search/reindex', 'POST', null, token);
        console.log("✅ Reindex result:", reindexRes.data);

        console.log("Catalog Seed Complete!");
    } catch (e) {
        console.error("Seeding failed:", e);
    }
}

runSeed();
