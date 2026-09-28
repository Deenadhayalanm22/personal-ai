import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createPrivateServer } from './server.mjs';

test('private host rejects assets before auth and fails closed', async () => {
  const dist = await mkdtemp(join(tmpdir(), 'v2-private-'));
  await writeFile(join(dist, 'index.html'), 'private app');
  await writeFile(join(dist, 'asset.js'), 'private asset');
  let online = true;
  const fetcher = async (url, options = {}) => {
    if (!online) throw Error('backend down');
    if (String(url).endsWith('/auth/session')) return new Response('', {status: options.headers?.cookie === 'WEB_SESSION=valid' ? 200 : 401});
    return new Response('', {status:404});
  };
  const server = createPrivateServer({apiOrigin:'http://backend.invalid',publicOrigin:'http://127.0.0.1:41999',dist,fetcher});
  await new Promise(resolve => server.listen(41999,'127.0.0.1',resolve));
  try {
    const denied=await fetch('http://127.0.0.1:41999/asset.js',{redirect:'manual'});
    assert.equal(denied.status,302);
    assert.equal(denied.headers.get('location'),'/portal');
    const allowed=await fetch('http://127.0.0.1:41999/asset.js',{headers:{cookie:'WEB_SESSION=valid'}});
    assert.equal(allowed.status,200);
    assert.equal(await allowed.text(),'private asset');
    online=false;
    assert.equal((await fetch('http://127.0.0.1:41999/',{headers:{cookie:'WEB_SESSION=valid'},redirect:'manual'})).status,302);
    assert.equal((await fetch('http://127.0.0.1:41999/portal')).status,200);
  } finally { await new Promise(resolve=>server.close(resolve)); await rm(dist,{recursive:true,force:true}); }
});

test('auth cookie is scoped to v2 host and cross-origin writes are refused', async () => {
  const server=createPrivateServer({apiOrigin:'http://backend.invalid',publicOrigin:'http://127.0.0.1:42000',fetcher:async () => new Response('{}',{status:200,headers:{'set-cookie':'WEB_SESSION=secret; Path=/api/web; HttpOnly; SameSite=Lax'}})});
  await new Promise(resolve=>server.listen(42000,'127.0.0.1',resolve));
  try {
    const denied=await fetch('http://127.0.0.1:42000/api/web/auth/magic-link',{method:'POST',headers:{origin:'https://evil.example'}});
    assert.equal(denied.status,403);
    const accepted=await fetch('http://127.0.0.1:42000/api/web/auth/magic-link',{method:'POST',headers:{origin:'http://127.0.0.1:42000'}});
    assert.equal(accepted.status,200);
    assert.match(accepted.headers.get('set-cookie'),/Path=\//);
    assert.doesNotMatch(accepted.headers.get('set-cookie'),/Path=\/api\/web/);
  } finally { await new Promise(resolve=>server.close(resolve)); }
});
