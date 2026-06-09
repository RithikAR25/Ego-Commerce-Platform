const http = require('http');
const fs = require('fs');

const BASE_URL = 'http://localhost:8080/api/v1';

async function fetchAPI(endpoint) {
    return new Promise((resolve, reject) => {
        http.get(`${BASE_URL}${endpoint}`, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode >= 400) {
                    reject(new Error(`API Error ${res.statusCode}: ${data}`));
                } else {
                    resolve(JSON.parse(data));
                }
            });
        }).on('error', reject);
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

async function runTests() {
    console.log('\n--- Running Catalog & Search API Tests ---');
    
    try {
        // 1. Get products list
        const productsPage = await fetchAPI('/products?page=0&size=10');
        assert(productsPage && productsPage.data, 'CAT-001', 'Browse Product Listing returns data');
        
        if (productsPage.data && productsPage.data.content && productsPage.data.content.length > 0) {
            const sampleProduct = productsPage.data.content[0];
            
            // 2. Get product by slug
            if (sampleProduct.slug) {
                try {
                    const prodDetails = await fetchAPI(`/products/${sampleProduct.slug}`);
                    assert(prodDetails && prodDetails.data && prodDetails.data.slug === sampleProduct.slug, 'CAT-002', 'Get Product by Slug works');
                } catch (e) {
                    assert(false, 'CAT-002', `Get Product by Slug failed: ${e.message}`);
                }
            }
        } else {
            console.warn("No products found in DB. Skipping CAT-002.");
        }

        // 3. Product Not Found
        try {
            await fetchAPI('/products/non-existent-product-slug-12345');
            assert(false, 'CAT-003', 'Product Not Found did not return 404');
        } catch (e) {
            assert(e.message.includes('404'), 'CAT-003', 'Product Not Found Returns 404');
        }

        // 4. Basic Search (Autocomplete/Keyword)
        const searchRes = await fetchAPI('/search?query=t-shirt');
        assert(searchRes && searchRes.data, 'SRCH-001', 'Keyword Search Returns Results');
        
        // 5. Category Filtering
        const catFilterRes = await fetchAPI('/search?categorySlug=men');
        assert(catFilterRes && catFilterRes.data, 'SRCH-CAT-003', 'Filter by ROOT slug returns results');

    } catch (e) {
        console.error("Test execution failed:", e);
    }
}

runTests();
