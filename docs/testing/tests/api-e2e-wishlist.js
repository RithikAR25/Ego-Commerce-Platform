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
    const token = await login();
    console.log("Logged in successfully.");

    // The seed script usually populates variants with IDs like 1, 2, 3...
    // Let's assume variantId 1 exists.
    const variantId = 1;

    // 1. Add to wishlist
    let res = await fetch('http://localhost:8080/api/v1/wishlist/items', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ variantId })
    });
    let data = await res.json();
    assert(res.status === 200, `WISH-001: Add to Wishlist (Status ${res.status})`);
    
    // 2. Duplicate Add is Idempotent
    res = await fetch('http://localhost:8080/api/v1/wishlist/items', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ variantId })
    });
    assert(res.status === 200, `WISH-002: Duplicate Add is Idempotent (Status ${res.status})`);

    // 3. Get Wishlist Items (and check stock availability flag)
    res = await fetch('http://localhost:8080/api/v1/wishlist', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    data = await res.json();
    console.log(data.data.items[0]);
    assert(res.status === 200 && data.data.items.length > 0, `WISH-004: Get Wishlist Items`);
    if (data.data.items.length > 0) {
      assert(data.data.items[0].stockStatus !== undefined, `WISH-005: Stock Availability Flag exists`);
    } else {
      assert(false, `WISH-005: Wishlist empty, cannot check flag`);
    }

    // 4. Remove from Wishlist
    res = await fetch(`http://localhost:8080/api/v1/wishlist/items/${variantId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    assert(res.status === 200, `WISH-003: Remove from Wishlist (Status ${res.status})`);

    console.log(`\nResults: ${passed} passed, ${failed} failed.`);
  } catch (err) {
    console.error("Test suite failed with error:", err);
  }
}

runTests();
