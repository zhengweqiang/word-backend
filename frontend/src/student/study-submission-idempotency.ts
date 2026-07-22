export interface PendingStudySubmission<T extends object> {
  identity: string;
  payload: T & { requestKey: string };
}

export interface StudySubmissionStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface StudySubmissionPersistence {
  storageKey: string;
  storage?: StudySubmissionStorage | null;
}

interface StoredStudySubmission<T extends object> extends PendingStudySubmission<T> {
  fingerprint: string;
}

export function preparePendingStudySubmission<T extends object>(
  pending: PendingStudySubmission<T> | null,
  identity: string,
  createPayload: () => T,
  createRequestKey: () => string = () => globalThis.crypto.randomUUID(),
  persistence?: StudySubmissionPersistence,
): PendingStudySubmission<T> {
  if (pending?.identity === identity) {
    return pending;
  }

  const restored = readPendingSubmission<T>(identity, persistence);
  if (restored) {
    return restored;
  }

  const next = {
    identity,
    payload: {
      ...createPayload(),
      requestKey: createRequestKey(),
    },
  };
  writePendingSubmission(next, persistence);
  return next;
}

export function clearPendingStudySubmission<T extends object>(
  _pending: PendingStudySubmission<T> | null,
  persistence?: StudySubmissionPersistence,
): null {
  const storage = resolveStorage(persistence);
  if (storage && persistence) {
    try {
      storage.removeItem(persistence.storageKey);
    } catch {
      // Browser privacy settings can make sessionStorage unavailable at runtime.
    }
  }
  return null;
}

function readPendingSubmission<T extends object>(
  identity: string,
  persistence?: StudySubmissionPersistence,
): PendingStudySubmission<T> | null {
  const storage = resolveStorage(persistence);
  if (!storage || !persistence) {
    return null;
  }

  try {
    const serialized = storage.getItem(persistence.storageKey);
    if (!serialized) {
      return null;
    }
    const stored = JSON.parse(serialized) as StoredStudySubmission<T>;
    if (
      stored?.identity !== identity
      || !stored.payload
      || typeof stored.payload !== 'object'
      || typeof stored.payload.requestKey !== 'string'
      || stored.payload.requestKey.length === 0
      || stored.fingerprint !== fingerprintPayload(stored.payload)
    ) {
      return null;
    }
    return { identity: stored.identity, payload: stored.payload };
  } catch {
    return null;
  }
}

function writePendingSubmission<T extends object>(
  pending: PendingStudySubmission<T>,
  persistence?: StudySubmissionPersistence,
): void {
  const storage = resolveStorage(persistence);
  if (!storage || !persistence) {
    return;
  }

  const stored: StoredStudySubmission<T> = {
    ...pending,
    fingerprint: fingerprintPayload(pending.payload),
  };
  try {
    storage.setItem(persistence.storageKey, JSON.stringify(stored));
  } catch {
    // Keep request submission available in memory when persistence is blocked.
  }
}

function resolveStorage(persistence?: StudySubmissionPersistence): StudySubmissionStorage | null {
  if (!persistence) {
    return null;
  }
  if (persistence.storage !== undefined) {
    return persistence.storage;
  }
  try {
    return globalThis.sessionStorage;
  } catch {
    return null;
  }
}

function fingerprintPayload(payload: object & { requestKey: string }): string {
  const { requestKey: _requestKey, ...businessPayload } = payload;
  return stableSerialize(businessPayload);
}

function stableSerialize(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableSerialize).join(',')}]`;
  }
  if (value !== null && typeof value === 'object') {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableSerialize(record[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value) ?? 'undefined';
}
