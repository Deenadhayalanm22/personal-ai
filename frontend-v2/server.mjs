// FIN-EPIC-007: private v2 host. It serves no application asset before session validation.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { resolve, sep, extname } from 'node:path';

const types = { '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8', '.svg':'image/svg+xml', '.png':'image/png', '.ico':'image/x-icon', '.woff2':'font/woff2' };
const publicPages = new Set(['/portal','/access']);

function page(title, body) {
  return `<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title} · Money Stories</title><style>body{font:16px/1.5 system-ui;background:#f6f5ef;color:#243c2e;min-height:100vh;display:grid;place-items:center;margin:0}main{background:#fff;padding:2rem;max-width:25rem;border:1px solid #d7dece;border-radius:1rem}input,button{font:inherit;padding:.7rem;border-radius:.5rem}input{width:90%;border:1px solid #9aa995}button{background:#3c6245;color:white;border:0;cursor:pointer;margin-top:1rem}p{color:#52684d}</style></head><body><main>${body}</main></body></html>`;
}
const portalPage = page('Private access', `<h1>Your private journey</h1><p>Enter the number connected to your WhatsApp account. We’ll send a one-time sign-in link.</p><form id="login"><label for="phone">Indian mobile number</label><input id="phone" type="tel" inputmode="numeric" autocomplete="tel-national" pattern="[6-9][0-9]{9}" required placeholder="9876543210"><button>Send link on WhatsApp</button></form><p id="result" role="status"></p><script>document.querySelector('#login').addEventListener('submit',async e=>{e.preventDefault();const value=document.querySelector('#phone').value;const result=document.querySelector('#result');result.textContent='Sending…';try{const response=await fetch('/api/web/auth/login-link',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({phoneNumber:'+91'+value})});if(!response.ok)throw Error();result.textContent='If this number is registered, we sent a sign-in link on WhatsApp.'}catch{result.textContent='The service is unavailable. Please try again.'}})</script>`);
const accessPage = page('Signing in', `<h1>Signing in…</h1><p id="result" role="status">Checking your one-time link.</p><script>const token=new URLSearchParams(location.search).get('token');const result=document.querySelector('#result');if(!token){result.textContent='This link is missing a token.'}else{history.replaceState({},'', '/access');fetch('/api/web/auth/magic-link',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token})}).then(response=>{if(!response.ok)throw Error();location.replace('/')}).catch(()=>{result.textContent='This link is invalid or expired. Request a new one.'})}</script><p><a href="/portal">Request a new link</a></p>`);

export function createPrivateServer({ apiOrigin, publicOrigin, dist = new URL('./dist/', import.meta.url).pathname, fetcher = fetch } = {}) {
  if (!apiOrigin || !publicOrigin) throw new Error('V2_API_ORIGIN and V2_PUBLIC_ORIGIN are required');
  const backend = new URL(apiOrigin);
  const publicUrl = new URL(publicOrigin);
  if (!['http:','https:'].includes(backend.protocol) || !['http:','https:'].includes(publicUrl.protocol)) throw new Error('Invalid origin');
  const root = resolve(dist);
  async function proxy(request, response, path) {
    const method = request.method || 'GET';
    if (!['GET','HEAD','POST','PUT','PATCH','DELETE'].includes(method)) { response.writeHead(405).end(); return; }
    if (!['GET','HEAD'].includes(method) && request.headers.origin !== publicUrl.origin) { response.writeHead(403).end(); return; }
    const url = new URL(path, backend);
    if (url.origin !== backend.origin || !url.pathname.startsWith('/api/web/')) { response.writeHead(400).end(); return; }
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    try {
      const upstream = await fetcher(url, { method, headers: { cookie:request.headers.cookie || '', 'content-type':request.headers['content-type'] || 'application/json' }, body:['GET','HEAD'].includes(method) ? undefined : Buffer.concat(chunks), redirect:'manual' });
      const headers = { 'content-type':upstream.headers.get('content-type') || 'application/json', 'cache-control':'private, no-store' };
      const cookie = upstream.headers.get('set-cookie');
      if (cookie) headers['set-cookie'] = cookie.replace(/Path=\/api\/web(?:;|$)/i, 'Path=/;').replace(/;\s*Domain=[^;]*/ig, '');
      response.writeHead(upstream.status, headers).end(Buffer.from(await upstream.arrayBuffer()));
    } catch { response.writeHead(503, {'cache-control':'no-store'}).end('Service unavailable'); }
  }
  async function authorized(request) {
    try {
      const check = await fetcher(new URL('/api/web/auth/session',backend), { headers:{ cookie:request.headers.cookie || '' }, redirect:'manual' });
      return check.status === 200;
    } catch { return false; }
  }
  return createServer(async (request,response) => {
    const path = new URL(request.url || '/', publicUrl).pathname;
    if (path.startsWith('/api/web/')) { await proxy(request,response,request.url); return; }
    if (request.method !== 'GET' && request.method !== 'HEAD') { response.writeHead(405).end(); return; }
    if (publicPages.has(path)) { response.writeHead(200, {'content-type':'text/html; charset=utf-8','cache-control':'no-store'}).end(path === '/portal' ? portalPage : accessPage); return; }
    if (!(await authorized(request))) { response.writeHead(302, {'location':'/portal','cache-control':'no-store'}).end(); return; }
    let decoded;
    try { decoded = decodeURIComponent(path); } catch { response.writeHead(400).end(); return; }
    const file = resolve(root, `.${decoded === '/' ? '/index.html' : decoded}`);
    if (!file.startsWith(root + sep) || file.includes('\0')) { response.writeHead(404).end(); return; }
    try {
      const metadata = await stat(file);
      if (!metadata.isFile()) throw Error();
      const data = await readFile(file);
      response.writeHead(200, {'content-type':types[extname(file)] || 'application/octet-stream','cache-control':'private, no-store','x-content-type-options':'nosniff'}).end(request.method === 'HEAD' ? undefined : data);
    } catch { response.writeHead(404, {'cache-control':'no-store'}).end(); }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === new URL(import.meta.url).pathname) {
  const server = createPrivateServer({apiOrigin:process.env.V2_API_ORIGIN,publicOrigin:process.env.V2_PUBLIC_ORIGIN});
  server.listen(Number(process.env.PORT || 4174), '0.0.0.0');
}
