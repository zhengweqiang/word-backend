import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');

test('docker compose exposes only one frontend container on localhost 8083', () => {
  const compose = read('docker-compose.yml').replace(/\r\n/g, '\n');

  assert.match(compose, /frontend:\n[\s\S]*?ports:\n\s+- "8083:80"/);
  assert.doesNotMatch(compose, /\n\s+admin-frontend:/);
});

test('frontend build includes the admin sub-application', () => {
  const pkg = JSON.parse(read('frontend/package.json'));
  const adminPkg = JSON.parse(read('frontend/admin/package.json'));

  assert.match(pkg.scripts.build, /npm run build:admin/);
  assert.equal(adminPkg.name, 'frontend-admin');
});

test('admin sub-application is mounted at /admin/', () => {
  const adminVite = read('frontend/admin/vite.config.ts');
  const adminApp = read('frontend/admin/src/App.tsx');
  const nginx = read('frontend/nginx.conf');

  assert.match(adminVite, /base:\s*["']\/admin\/["']/);
  assert.match(nginx, /location\s+\^~\s+\/admin\//);
  assert.match(nginx, /location\s+=\s+\/admin\/login/);
  assert.doesNotMatch(adminApp, /path=["']\/login["']/);
  assert.doesNotMatch(nginx, /proxy_pass\s+http:\/\/admin-frontend/);
});
