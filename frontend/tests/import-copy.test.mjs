import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('uses 词书导入 consistently in the admin import workflow', () => {
  const appShell = read('admin/src/components/layout/app-shell.tsx');
  const navigation = read('admin/src/components/layout/navigation.ts');
  const importCenter = read('admin/src/pages/import-center-page.tsx');
  const frontendCopy = `${appShell}\n${navigation}\n${importCenter}`;

  assert.doesNotMatch(frontendCopy, /辞书导入/);
  assert.match(navigation, /label:\s*"词书导入"/);
  assert.equal(importCenter.match(/词书导入/g)?.length, 5);
});
