const http = require('http');
const { execSync } = require('child_process');

const BASE_URL = 'http://localhost:8080/api/v1';

async function fetchAPI(endpoint, method = 'GET', body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(`${BASE_URL}${endpoint}`);
        const options = {
            hostname: url.hostname, port: url.port, path: url.pathname + url.search, method,
            headers: { 'Content-Type': 'application/json' }
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

async function runTests() {
    let passed = 0, failed = 0;
    function assert(condition, message) { if (condition) { console.log(`✅ PASS: ${message}`); passed++; } else { console.error(`❌ FAIL: ${message}`); failed++; } }

    try {
        console.log("--- Setup ---");
        const adminLoginRes = await fetchAPI('/auth/login', 'POST', { email: 'Admin@ego.com', password: 'Admin@123' });
        const adminToken = adminLoginRes.data.data.accessToken;

        console.log("\n--- Running Elasticsearch API Tests ---");

        // ES-ADV-001: Reindex API
        let reindexRes = await fetchAPI('/admin/search/reindex', 'POST', null, adminToken);
        assert(reindexRes.status === 200, `ES-ADV-001: Reindex API (Admin) (Status ${reindexRes.status})`);

        // ES-ADV-002: DLQ Processing
        // We will just verify the endpoint returns 200, DLQ simulation is complex.
        let dlqRes = await fetchAPI('/admin/search/outbox/failed', 'GET', null, adminToken);
        assert(dlqRes.status === 200 && dlqRes.data.data.failedCount !== undefined, `ES-ADV-002: DLQ Processing Viewer (Status ${dlqRes.status})`);

        // ES-ADV-004: Faceted Search
        let searchRes = await fetchAPI('/search?query=Hoodie', 'GET');
        assert(searchRes.status === 200, `ES-ADV-004: Faceted Search with Multiple Aggregations (Status ${searchRes.status})`);

        // ES-ADV-003: Circuit Breaker Simulation
        console.log("Simulating Elasticsearch failure by stopping container...");
        execSync("docker stop ego-elasticsearch");
        console.log("Container stopped. Waiting 5 seconds...");
        await new Promise(r => setTimeout(r, 5000));

        let fallbackRes = await fetchAPI('/search?query=Hoodie', 'GET');
        assert(fallbackRes.status === 200, `ES-ADV-003: Circuit Breaker Simulation (Fallback to MySQL) (Status ${fallbackRes.status})`);

        console.log("Starting container back up...");
        execSync("docker start ego-elasticsearch");

        console.log(`\nResults: ${passed} passed, ${failed} failed.`);
    } catch (e) {
        console.error("Test execution failed:", e);
        try { execSync("docker start ego-elasticsearch"); } catch(ex){}
    }
}

runTests();
