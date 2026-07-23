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
  assert.match(api, /completeFromClassroomFeed:/);
  assert.match(api, /\/classrooms\/\$\{classroomId\}\/group-feed\/videos\/\$\{videoId\}\/complete/);
});

test('student classroom feed treats study plan messages as learning resources', () => {
  const classrooms = read('src/student/StudentClassrooms.tsx');
  const types = read('src/types/index.ts');

  assert.match(types, /'STUDY_PLAN'/);
  assert.match(classrooms, /message\.messageType === 'STUDY_PLAN'/);
  assert.match(classrooms, /查看今日任务/);
});

test('student classroom switcher only appears when there are multiple classrooms', () => {
  const classrooms = read('src/student/StudentClassrooms.tsx');

  assert.match(classrooms, /classrooms\.length > 1 && \(/);
  assert.match(classrooms, /className="student-classroom-strip"/);
});

test('student classroom header does not show a classroom count', () => {
  const classrooms = read('src/student/StudentClassrooms.tsx');

  assert.doesNotMatch(classrooms, /subtle-count/);
});

test('student classroom composer opens in a floating layer', () => {
  const classrooms = read('src/student/StudentClassrooms.tsx');
  const styles = read('src/student/student-workspace.css');

  assert.match(classrooms, /className="student-compose-trigger"/);
  assert.match(classrooms, /setComposeOpen\(true\)/);
  assert.match(classrooms, /className="student-compose-layer"/);
  assert.match(classrooms, /aria-label="发布班级留言"/);
  assert.doesNotMatch(classrooms, /className="student-feed-composer"/);
  assert.match(styles, /student-compose-sheet/);
  assert.doesNotMatch(styles, /\.student-feed-composer/);
});
