import assert from 'node:assert/strict';
import test from 'node:test';

import { postLoginDestination } from '../src/auth/routing.ts';
import { TOKEN_STORAGE_KEY } from '../src/api/index.ts';
import { TOKEN_STORAGE_KEY as ADMIN_TOKEN_STORAGE_KEY } from '../admin/src/lib/session.ts';

test('routes administrators and teachers to the admin workspace after login', () => {
  assert.equal(postLoginDestination('ADMIN'), '/admin/');
  assert.equal(postLoginDestination('TEACHER'), '/admin/');
});

test('routes students to the student workspace after login', () => {
  assert.equal(postLoginDestination('STUDENT'), '/');
});

test('root and admin apps share the same login token storage key', () => {
  assert.equal(ADMIN_TOKEN_STORAGE_KEY, TOKEN_STORAGE_KEY);
});
