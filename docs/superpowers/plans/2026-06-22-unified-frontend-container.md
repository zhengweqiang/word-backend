# Unified Frontend Container Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the student, teacher, and admin frontends from one `frontend` codebase, one Docker image, one container, and one public port.

**Architecture:** Keep the existing React app as the root application at `/`. Move the Solid admin app under `frontend/admin` and build it as a Vite sub-application with `base: "/admin/"`, emitting static files into `frontend/dist/admin`. Use one nginx config in the `frontend` image to serve both static apps and proxy `/api` to the Spring backend.

**Tech Stack:** React + Vite for the root app, SolidJS + Vite for the admin sub-app, nginx for static serving and API proxying, Docker Compose for orchestration.

## Global Constraints

- Do not rewrite the admin app from SolidJS to React in this change.
- The single public frontend URL is `http://localhost:8083/`.
- Admin users access `http://localhost:8083/admin/`.
- Student and teacher users access `http://localhost:8083/`.
- Preserve existing API proxy behavior through `/api`.
- Do not modify unrelated in-progress page work under `admin-frontend/src/pages`.

---

### Task 1: Lock the Unified Configuration Contract

**Files:**
- Create: `frontend/tests/unified-container-config.test.mjs`

**Interfaces:**
- Consumes: `docker-compose.yml`, `frontend/package.json`, `frontend/admin/package.json`, `frontend/admin/vite.config.ts`, `frontend/nginx.conf`
- Produces: A fast Node test that fails until the single-container frontend configuration exists.

- [ ] **Step 1: Write the failing test**

```js
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');

test('docker compose exposes only one frontend container on localhost 8083', () => {
  const compose = read('docker-compose.yml');

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
  const nginx = read('frontend/nginx.conf');

  assert.match(adminVite, /base:\s*["']\/admin\/["']/);
  assert.match(nginx, /location\s+\^~\s+\/admin\//);
  assert.doesNotMatch(nginx, /proxy_pass\s+http:\/\/admin-frontend/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- tests/unified-container-config.test.mjs`

Expected: FAIL because `frontend/admin/package.json` does not exist yet and compose still has `admin-frontend`.

### Task 2: Move the Admin App Into the Frontend Project

**Files:**
- Move: `admin-frontend/*` to `frontend/admin/*`
- Modify: `frontend/admin/package.json`
- Modify: `frontend/admin/vite.config.ts`

**Interfaces:**
- Consumes: Existing Solid admin app.
- Produces: A self-contained admin Vite sub-app under `frontend/admin`.

- [ ] **Step 1: Move files without changing page code**

Run: `mkdir -p frontend/admin && mv admin-frontend/* frontend/admin/`

- [ ] **Step 2: Rename the admin package**

Set `frontend/admin/package.json` `"name"` to `"frontend-admin"`.

- [ ] **Step 3: Mount the admin app under `/admin/`**

Set `frontend/admin/vite.config.ts` `base` to `"/admin/"`.

- [ ] **Step 4: Run the failing config test again**

Run: `cd frontend && npm test -- tests/unified-container-config.test.mjs`

Expected: FAIL because root build and compose/nginx are not unified yet.

### Task 3: Build Both Apps From the Frontend Package

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/Dockerfile`

**Interfaces:**
- Consumes: `frontend/admin` sub-app.
- Produces: `npm run build` creates `frontend/dist/index.html` and `frontend/dist/admin/index.html`.

- [ ] **Step 1: Add root build scripts**

Add scripts:

```json
"build:root": "tsc -b && vite build",
"build:admin": "npm --prefix admin run build -- --outDir ../dist/admin",
"build": "npm run build:root && npm run build:admin"
```

- [ ] **Step 2: Replace the nginx-only Dockerfile with a Node builder plus nginx runtime**

Install root dependencies, install admin dependencies, build both apps, then copy `dist` into nginx.

- [ ] **Step 3: Run the frontend build**

Run: `cd frontend && npm run build`

Expected: PASS and creates `dist/admin/index.html`.

### Task 4: Serve Both Apps From One nginx Container

**Files:**
- Modify: `frontend/nginx.conf`
- Modify: `docker-compose.yml`
- Delete: `frontend/admin/nginx.conf`
- Delete: `frontend/admin/Dockerfile`

**Interfaces:**
- Consumes: Static assets in `frontend/dist`.
- Produces: One public frontend service on `http://localhost:8083`.

- [ ] **Step 1: Serve `/admin/` statically**

Use `try_files $uri $uri/ /admin/index.html` for `location ^~ /admin/`.

- [ ] **Step 2: Expose only the `frontend` service**

Add `ports: ["8083:80"]` to `frontend`, and remove the `admin-frontend` service from `docker-compose.yml`.

- [ ] **Step 3: Remove obsolete admin container files**

Delete the moved admin Dockerfile and nginx config so the only frontend container definition is `frontend/Dockerfile`.

- [ ] **Step 4: Run the config test**

Run: `cd frontend && npm test -- tests/unified-container-config.test.mjs`

Expected: PASS.

### Task 5: Verify and Restart the Unified Frontend

**Files:**
- No new source files.

**Interfaces:**
- Consumes: Completed unified frontend container.
- Produces: Running app at `http://localhost:8083/` and `http://localhost:8083/admin/`.

- [ ] **Step 1: Run focused tests**

Run: `cd frontend && npm test`

Expected: PASS.

- [ ] **Step 2: Rebuild the single frontend container**

Run: `docker-compose up -d --build frontend`

Expected: PASS and only `words-frontend` is rebuilt for frontend serving.

- [ ] **Step 3: Smoke-test HTTP routes**

Run:

```bash
curl -sS -o /tmp/root.html -w '%{http_code}\n' http://localhost:8083/
curl -sS -o /tmp/admin.html -w '%{http_code}\n' http://localhost:8083/admin/
```

Expected: both return `200`.
