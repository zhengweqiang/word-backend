import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiSource = await readFile(new URL('../src/api/index.ts', import.meta.url), 'utf8');
const workspaceSource = await readFile(new URL('../src/student/StudentWorkspace.tsx', import.meta.url), 'utf8');
const attemptSource = await readFile(new URL('../src/student/StudentPaperAttempt.tsx', import.meta.url), 'utf8');

test('student paper client covers list, open, versioned draft, submit, and result endpoints', () => {
  assert.match(apiSource, /students\/me\/papers`/);
  assert.match(apiSource, /students\/me\/papers\/\$\{attemptId\}`/);
  assert.match(apiSource, /students\/me\/papers\/\$\{attemptId\}\/draft/);
  assert.match(apiSource, /students\/me\/papers\/\$\{attemptId\}\/submit/);
  assert.match(apiSource, /students\/me\/papers\/\$\{attemptId\}\/result/);
  assert.match(apiSource, /JSON\.stringify\(\{ expectedVersion, answers \}\)/);
});

test('student workspace exposes assessments without merging them into study tasks', () => {
  assert.match(workspaceSource, /id: 'assessments'/);
  assert.match(workspaceSource, /<StudentAssignedPapers/);
  assert.doesNotMatch(workspaceSource, /onDashboardChange.*StudentAssignedPapers/);
  assert.match(workspaceSource, /error && \(tab === 'home' \|\| tab === 'study'\)/);
});

test('late attempts remain answerable and submit with a visible late confirmation', () => {
  assert.match(attemptSource, /attempt\.attemptStatus === 'OVERDUE'/);
  assert.match(attemptSource, /仍可继续作答/);
  assert.match(attemptSource, /会被标记为超时提交/);
});
