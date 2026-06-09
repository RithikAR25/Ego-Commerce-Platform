const http = require('http');
const { execSync } = require('child_process');

async function fetchAPI(endpoint, method='GET', body=null, token=null) {
  return new Promise((resolve, reject) => {
    const url = new URL('http://localhost:8080/api/v1' + endpoint);
    const opts = { hostname: url.hostname, port: url.port, path: url.pathname + url.search, method, headers: { 'Content-Type': 'application/json' } };
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    const req = http.request(opts, (res) => {
      let d = ''; res.on('data', c => d += c); res.on('end', () => resolve({ status: res.statusCode, data: d ? JSON.parse(d) : {} }));
    }).on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function main() {
  const ts = Date.now();
  const email = `dbg${ts}@test.com`;
  await fetchAPI('/auth/register', 'POST', { email, password: 'Password@123', firstName: 'D', lastName: 'B' });
  execSync(`docker exec rawego mysql -uroot -proot rawego -e "UPDATE users SET is_email_verified=1 WHERE email='${email}';"`);
  
  const lr = await fetchAPI('/auth/login', 'POST', { email, password: 'Password@123' });
  const tok = lr.data.data.accessToken;
  
  await fetchAPI('/cart', 'DELETE', null, tok);
  const addRes = await fetchAPI('/cart/add', 'POST', { variantId: 1, quantity: 1 }, tok);
  console.log('Add cart:', addRes.status, JSON.stringify(addRes.data).substring(0, 200));
  
  const chkRes = await fetchAPI('/orders/checkout', 'POST', { shippingAddress: '123 St' }, tok);
  console.log('Checkout:', chkRes.status, JSON.stringify(chkRes.data).substring(0, 300));
  
  if (chkRes.data && chkRes.data.data) {
    const oId = chkRes.data.data.id;
    const pay = await fetchAPI('/payments/razorpay/create', 'POST', { orderId: oId }, tok);
    console.log('Payment:', pay.status, JSON.stringify(pay.data).substring(0, 400));
  }
}
main().catch(console.error);
