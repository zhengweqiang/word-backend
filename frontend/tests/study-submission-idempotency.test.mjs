import assert from 'node:assert/strict';
import test from 'node:test';

import {
  clearPendingStudySubmission,
  preparePendingStudySubmission,
} from '../src/student/study-submission-idempotency.ts';

test('reuses the complete payload and request key when retrying the same attempt', () => {
  let generatedKeys = 0;
  const createKey = () => `request-${++generatedKeys}`;
  const first = preparePendingStudySubmission(
    null,
    'plan:200:item:400:CORRECT',
    () => ({ result: 'CORRECT', durationSeconds: 12 }),
    createKey,
  );

  const retry = preparePendingStudySubmission(
    first,
    'plan:200:item:400:CORRECT',
    () => ({ result: 'CORRECT', durationSeconds: 99 }),
    createKey,
  );

  assert.equal(retry, first);
  assert.equal(retry.payload.requestKey, 'request-1');
  assert.equal(retry.payload.durationSeconds, 12);
  assert.equal(generatedKeys, 1);
});

test('creates a new request key for a different item or result', () => {
  let generatedKeys = 0;
  const createKey = () => `request-${++generatedKeys}`;
  const first = preparePendingStudySubmission(
    null,
    'plan:200:item:400:CORRECT',
    () => ({ result: 'CORRECT' }),
    createKey,
  );

  const changed = preparePendingStudySubmission(
    first,
    'plan:200:item:400:INCORRECT',
    () => ({ result: 'INCORRECT' }),
    createKey,
  );

  assert.notEqual(changed.payload.requestKey, first.payload.requestKey);
  assert.equal(changed.payload.requestKey, 'request-2');
});

test('clears a successful submission so the next attempt receives a new key', () => {
  const pending = preparePendingStudySubmission(
    null,
    'plan:200:item:400:CORRECT',
    () => ({ result: 'CORRECT' }),
    () => 'request-1',
  );

  assert.equal(clearPendingStudySubmission(pending), null);
});

test('restores the exact pending payload and key after remount', () => {
  const storage = createStorage();
  const persistence = { storage, storageKey: 'dashboard:plan:200' };
  const first = preparePendingStudySubmission(
    null,
    'item:400:CORRECT',
    () => ({ result: 'CORRECT', durationSeconds: 12 }),
    () => 'request-1',
    persistence,
  );

  const restored = preparePendingStudySubmission(
    null,
    'item:400:CORRECT',
    () => ({ result: 'CORRECT', durationSeconds: 99 }),
    () => 'request-2',
    persistence,
  );

  assert.deepEqual(restored, first);
  assert.equal(restored.payload.durationSeconds, 12);
  assert.equal(restored.payload.requestKey, 'request-1');
});

test('clears persisted pending submission after success', () => {
  const storage = createStorage();
  const persistence = { storage, storageKey: 'modal:plan:200' };
  const pending = preparePendingStudySubmission(
    null,
    'item:400:CORRECT',
    () => ({ result: 'CORRECT' }),
    () => 'request-1',
    persistence,
  );

  assert.equal(clearPendingStudySubmission(pending, persistence), null);
  assert.equal(storage.getItem('modal:plan:200'), null);
});

test('replaces persisted key when the business payload identity changes', () => {
  const storage = createStorage();
  const persistence = { storage, storageKey: 'dashboard:plan:200' };
  const first = preparePendingStudySubmission(
    null,
    'item:400:CORRECT',
    () => ({ result: 'CORRECT' }),
    () => 'request-1',
    persistence,
  );
  const changed = preparePendingStudySubmission(
    first,
    'item:400:INCORRECT',
    () => ({ result: 'INCORRECT' }),
    () => 'request-2',
    persistence,
  );

  assert.equal(changed.payload.requestKey, 'request-2');
  assert.match(storage.getItem('dashboard:plan:200'), /request-2/);
});

test('ignores corrupt or tampered persisted submissions safely', () => {
  const storage = createStorage();
  const persistence = { storage, storageKey: 'dashboard:plan:200' };
  storage.setItem('dashboard:plan:200', '{not-json');

  const fromCorrupt = preparePendingStudySubmission(
    null,
    'item:400:CORRECT',
    () => ({ result: 'CORRECT' }),
    () => 'request-1',
    persistence,
  );
  const stored = JSON.parse(storage.getItem('dashboard:plan:200'));
  stored.payload.result = 'INCORRECT';
  storage.setItem('dashboard:plan:200', JSON.stringify(stored));

  const fromTampered = preparePendingStudySubmission(
    null,
    'item:400:CORRECT',
    () => ({ result: 'CORRECT' }),
    () => 'request-2',
    persistence,
  );

  assert.equal(fromCorrupt.payload.requestKey, 'request-1');
  assert.equal(fromTampered.payload.requestKey, 'request-2');
});

test('continues without persistence when session storage is unavailable', () => {
  const unavailableStorage = {
    getItem() { throw new Error('blocked'); },
    setItem() { throw new Error('blocked'); },
    removeItem() { throw new Error('blocked'); },
  };
  const persistence = { storage: unavailableStorage, storageKey: 'dashboard:plan:200' };

  let pending;
  assert.doesNotThrow(() => {
    pending = preparePendingStudySubmission(
      null,
      'item:400:CORRECT',
      () => ({ result: 'CORRECT' }),
      () => 'request-1',
      persistence,
    );
  });

  assert.equal(pending.payload.requestKey, 'request-1');
  assert.doesNotThrow(() => clearPendingStudySubmission(pending, persistence));
});

function createStorage() {
  const values = new Map();
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, value);
    },
    removeItem(key) {
      values.delete(key);
    },
  };
}
