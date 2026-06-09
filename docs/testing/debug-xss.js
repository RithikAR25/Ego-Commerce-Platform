async function run() {
  const res = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'arikothanrithik@gmail.com', password: 'Rithik@1234' })
  });
  const data = await res.json();
  const token = data.data.accessToken;
  const res2 = await fetch('http://localhost:8080/api/v1/user/addresses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
    body: JSON.stringify({
      fullName: 'Test User',
      phone: '1234567890',
      addressLine1: '123 Main St <script>alert("xss")</script>',
      city: 'Test',
      state: 'TS',
      postalCode: '123456',
      country: 'IN',
      isDefault: false
    })
  });
  const data2 = await res2.json();
  console.log('Status:', res2.status);
  console.log('Response:', data2);
}
run();
