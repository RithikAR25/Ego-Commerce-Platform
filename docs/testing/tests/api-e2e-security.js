const fs = require('fs');

async function login() {
  const res = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'arikothanrithik@gmail.com', password: 'Rithik@1234' })
  });
  const data = await res.json();
  if (!res.ok) throw new Error(`Login failed: ${JSON.stringify(data)}`);
  return data.data.accessToken;
}

async function runSecurityTests() {
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
    const token = await login();
    
    // 1. JWT Tampering (SEC-JWT-001)
    const tamperedToken = token.substring(0, token.length - 5) + 'abcde';
    let res = await fetch('http://localhost:8080/api/v1/auth/me', {
      headers: { 'Authorization': `Bearer ${tamperedToken}` }
    });
    assert(res.status === 401 || res.status === 403, `SEC-JWT-001: JWT Tampering (Status ${res.status})`);

    // 2. XSS Address Sanitization (SEC-XSS-002)
    const xssPayload = "123 Main St <script>alert('xss')</script>";
    res = await fetch('http://localhost:8080/api/v1/addresses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({
        fullName: "Test User",
        phone: '9876543210',
        addressLine1: xssPayload,
        city: 'Test',
        state: 'TS',
        pinCode: '123456',
        country: "IN",
        isDefault: false
      })
    });
    let data = await res.json();
    console.log("XSS Address Status:", res.status, "Data:", JSON.stringify(data));
    assert(res.status === 201 && data.data.addressLine1.indexOf("<script>") === -1, `SEC-XSS-002: XSS Address Sanitization`);

    // 3. Rate Limit Resend Verification (SEC-RAT-001)
    let rateLimited = false;
    for (let i = 0; i < 20; i++) {
      let r = await fetch('http://localhost:8080/api/v1/auth/resend-verification?email=arikothanrithik@gmail.com', {
        method: 'POST'
      });
      if (r.status === 429) {
        rateLimited = true;
        break;
      }
    }
    assert(rateLimited, `SEC-RAT-001: Rate Limit Resend Verification`);

    console.log(`\nResults: ${passed} passed, ${failed} failed.`);
  } catch (err) {
    console.error("Test suite failed with error:", err);
  }
}

runSecurityTests();
