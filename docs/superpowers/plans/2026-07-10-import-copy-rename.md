# Import Copy Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every frontend occurrence of `辞书导入` with `词书导入` while leaving routes and import behavior unchanged.

**Architecture:** Add one source-level regression test that reads the navigation and import page source files and enforces the approved terminology. Make only exact copy replacements in those two existing components, then rebuild the unified frontend Docker image.

**Tech Stack:** Node.js test runner, TypeScript/TSX, SolidJS, Vite, Docker Compose

---

### Task 1: Add the terminology regression test

**Files:**
- Create: `frontend/tests/import-copy.test.mjs`
- Test: `frontend/tests/import-copy.test.mjs`

- [ ] **Step 1: Write the failing test**

```javascript
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('uses 词书导入 consistently in the admin import workflow', () => {
  const appShell = read('admin/src/components/layout/app-shell.tsx');
  const importCenter = read('admin/src/pages/import-center-page.tsx');
  const frontendCopy = `${appShell}\n${importCenter}`;

  assert.doesNotMatch(frontendCopy, /辞书导入/);
  assert.match(appShell, /label:\s*"词书导入"/);
  assert.equal(importCenter.match(/词书导入/g)?.length, 5);
});
```

- [ ] **Step 2: Run the test and verify the RED state**

Run from `frontend/`:

```bash
node --test --experimental-strip-types tests/import-copy.test.mjs
```

Expected: FAIL at `assert.doesNotMatch` because the frontend still contains `辞书导入`.

### Task 2: Replace the approved frontend copy

**Files:**
- Modify: `frontend/admin/src/components/layout/app-shell.tsx:49`
- Modify: `frontend/admin/src/pages/import-center-page.tsx:281`
- Test: `frontend/tests/import-copy.test.mjs`

- [ ] **Step 1: Update the navigation label**

Replace the exact menu entry with:

```tsx
{ href: "/imports", label: "词书导入", icon: DatabaseZap, roles: ["ADMIN"] },
```

- [ ] **Step 2: Update all five import-page phrases**

Apply these exact copy changes in `import-center-page.tsx`:

```tsx
<PageHeader eyebrow="Imports" title="词书导入" description="该页面仅对管理员开放。" />
```

```tsx
title="词书导入"
```

```tsx
<Button onClick={() => void mutate(() => api.createImportBatch(), "已开始新的词书导入。")}>
```

```tsx
系统会扫描服务器上的 `books` 目录，并自动创建一个新的词书导入批次。
```

```tsx
fallback={<EmptyState title="还没有导入批次" description="点击右上角“开始导入”即可发起一次词书导入。" />}
```

- [ ] **Step 3: Run the focused test and verify the GREEN state**

Run from `frontend/`:

```bash
node --test --experimental-strip-types tests/import-copy.test.mjs
```

Expected: PASS with 1 test and 0 failures.

- [ ] **Step 4: Run the full frontend test suite**

Run from `frontend/`:

```bash
npm test
```

Expected: all frontend tests pass with 0 failures.

- [ ] **Step 5: Build the production frontend**

Run from `frontend/`:

```bash
npm run build
```

Expected: both root and admin Vite builds complete successfully.

- [ ] **Step 6: Confirm the source terminology**

Run from the repository root:

```bash
rg -n "辞书导入|词书导入" frontend
```

Expected: no `辞书导入` matches; six production `词书导入` matches plus test assertions.

- [ ] **Step 7: Commit the tested copy change**

```bash
git add frontend/tests/import-copy.test.mjs frontend/admin/src/components/layout/app-shell.tsx frontend/admin/src/pages/import-center-page.tsx
git commit -m "Rename dictionary import copy"
```

### Task 3: Rebuild and verify the Docker frontend

**Files:**
- No source files changed.

- [ ] **Step 1: Rebuild and restart the unified frontend container**

Run from the repository root:

```bash
docker compose up -d --build frontend
```

Expected: `words-db` and `words-app` remain healthy, and `words-frontend` is rebuilt and started.

- [ ] **Step 2: Check final container health**

```bash
docker compose ps
```

Expected: `words-db`, `words-app`, and `words-frontend` all report `healthy`.

- [ ] **Step 3: Verify the browser entry point and API proxy**

```bash
curl.exe -sS -o NUL -w "LOGIN_HTTP=%{http_code}" http://localhost:8083/
curl.exe -sS -o NUL -w "PROXY_API_HTTP=%{http_code}" http://localhost:8083/api/auth/quote
```

Expected: `LOGIN_HTTP=200` and `PROXY_API_HTTP=200`.
