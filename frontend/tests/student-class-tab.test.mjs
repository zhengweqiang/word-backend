import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('student workspace uses a class tab instead of a video tab', () => {
  const workspace = read('src/student/StudentWorkspace.tsx');

  assert.match(workspace, /label:\s*'班级'/);
  assert.doesNotMatch(workspace, /label:\s*'视频'/);
  assert.match(workspace, /<StudentClassrooms\b/);
  assert.doesNotMatch(workspace, /<StudentVideos\b/);
});

test('student API exposes classroom and group feed methods', () => {
  const api = read('src/api/index.ts');

  assert.match(api, /getMyClassrooms:/);
  assert.match(api, /listClassroomGroupFeedMessages:/);
  assert.match(api, /createClassroomGroupFeedTextMessage:/);
});
