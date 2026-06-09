/**
 * SEC-XSS-002: Address API XSS Sanitization Verification
 * Tests that <script> tags and JS event handlers are stripped from all address fields.
 */
const http = require('http');
const { execSync } = require('child_process');

const BASE_URL = 'http://localhost:8080/api/v1';

async function fetchAPI(endpoint, method = 'GET', body = null, token = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(`${BASE_URL}${endpoint}`);
        const opts = {
            hostname: url.hostname, port: url.port,
            path: url.pathname + url.search, method,
            headers: { 'Content-Type': 'application/json' }
        };
        if (token) opts.headers['Authorization'] = 'Bearer ' + token;
        const req = http.request(opts, (res) => {
            let d = ''; res.on('data', c => d += c);
            res.on('end', () => resolve({ status: res.statusCode, data: d ? JSON.parse(d) : {} }));
        }).on('error', reject);
        if (body) req.write(JSON.stringify(body));
        req.end();
    });
}

function assertNoXss(value, fieldName, assert) {
    // Check for actual exploitable XSS patterns:
    // - Remaining HTML tags (script, img, etc.)
    // - JavaScript event handler attributes (onerror=, onclick=)
    // - javascript: URI scheme (executable in href/src context)
    // NOTE: plain residual text like "alert(1)" after tag-stripping is NOT exploitable
    //       and is acceptable — the dangerous wrapper was removed.
    const dangerous = [
        /<[^>]*>/i,           // any remaining HTML tags
        /\bon\w+\s*=/i,       // JS event handlers: onerror=, onclick=, etc.
        /javascript\s*:/i     // javascript: URI scheme
    ];
    const hasXss = dangerous.some(p => value && p.test(value));
    assert(!hasXss, `SEC-XSS-002: Field '${fieldName}' stripped exploitable XSS (value="${value}")`);
}

async function runTests() {
    let passed = 0, failed = 0;
    function assert(condition, message) {
        if (condition) { console.log(`✅ PASS: ${message}`); passed++; }
        else { console.error(`❌ FAIL: ${message}`); failed++; }
    }

    const ts = Date.now();
    const email = `xssaddr${ts}@test.com`;
    await fetchAPI('/auth/register', 'POST', { email, password: 'Password@123', firstName: 'Xss', lastName: 'Test' });
    execSync(`docker exec rawego mysql -uroot -proot rawego -e "UPDATE users SET is_email_verified=1 WHERE email='${email}';"`);
    const lr = await fetchAPI('/auth/login', 'POST', { email, password: 'Password@123' });
    const token = lr.data.data.accessToken;

    // Note: fullName has pattern ^[a-zA-Z .'‐]+$ and phone has ^[6-9]\d{9}$
    // Bean validation already blocks raw HTML in those fields (first line of defence).
    // HtmlSanitizer is the second line of defence for free-text fields with no char pattern:
    // addressLine1, addressLine2, landmark, city, state, country.
    const xssPayload = {
        fullName:      'John Doe',               // valid — no XSS possible here via validator
        phone:         '9999999999',             // valid
        addressLine1:  '<script>alert(1)</script>12 MG Road',
        addressLine2:  'javascript:alert(1) Floor 2',
        landmark:      '<img src=x onerror=alert(1)>Near Mall',
        city:          '<b onclick=alert(1)>Bengaluru</b>',
        state:         'Karnataka',
        pinCode:       '560001',
        country:       '<ScRiPt>alert(2)</ScRiPt>India',
        addressType:   'HOME',
        setAsDefault:  true
    };

    console.log('\n--- SEC-XSS-002: Address Create XSS Sanitization ---');
    const createRes = await fetchAPI('/addresses', 'POST', xssPayload, token);
    assert(createRes.status === 201 || createRes.status === 200,
        `Address created successfully (Status ${createRes.status})`);

    const addr = createRes.data.data;
    if (addr) {
        assertNoXss(addr.addressLine1, 'addressLine1', assert);
        assertNoXss(addr.addressLine2, 'addressLine2', assert);
        assertNoXss(addr.landmark,     'landmark',     assert);
        assertNoXss(addr.city,         'city',         assert);
        assertNoXss(addr.country,      'country',      assert);

        // Verify legitimate text content is preserved after tag stripping
        assert(addr.addressLine1 && addr.addressLine1.includes('12 MG Road'), `addressLine1 preserves legitimate content`);
        assert(addr.landmark     && addr.landmark.includes('Near Mall'),      `landmark preserves legitimate content`);
        assert(addr.city         && addr.city.includes('Bengaluru'),           `city preserves legitimate content`);
        assert(addr.country      && addr.country.includes('India'),            `country preserves legitimate content`);

        console.log('\n--- SEC-XSS-002: Address Update XSS Sanitization ---');
        const addrId = addr.id;
        const updatePayload = { ...xssPayload, city: '<b onclick=alert(1)>Mumbai</b>' };
        const updateRes = await fetchAPI(`/addresses/${addrId}`, 'PUT', updatePayload, token);
        assert(updateRes.status === 200, `Address updated successfully (Status ${updateRes.status})`);

        const upd = updateRes.data.data;
        if (upd) {
            assertNoXss(upd.city, 'city (update)', assert);
            assert(upd.city && upd.city.includes('Mumbai'), `city preserves content after update`);
        }
    }

    console.log(`\nResults: ${passed} passed, ${failed} failed.`);
}

runTests().catch(console.error);
