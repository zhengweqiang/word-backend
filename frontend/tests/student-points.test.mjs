import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { mergePointTransactions } from '../src/student/point-transactions.ts';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('student navigation replaces the library tab with points and keeps five entries', () => {
  const workspace = read('src/student/StudentWorkspace.tsx');
  const navBlock = workspace.match(/const navItems = \[([\s\S]*?)\];/)?.[1] ?? '';

  assert.equal((navBlock.match(/label:/g) ?? []).length, 5);
  assert.match(navBlock, /id:\s*'points'[\s\S]*label:\s*'积分'/);
  assert.doesNotMatch(navBlock, /id:\s*'library'[\s\S]*label:\s*'词库'/);
  assert.match(workspace, /<StudentPoints\b/);
  assert.match(workspace, /tab === 'library' \? '词库'/);
});

test('home retains a library shortcut outside the bottom navigation', () => {
  const home = read('src/student/StudentDashboardHome.tsx');

  assert.match(home, /onOpenLibrary/);
  assert.match(home, />我的词书</);
});

test('student point API exposes summary and paged transaction requests', () => {
  const api = read('src/api/index.ts');

  assert.match(api, /studentPointApi\s*=\s*\{/);
  assert.match(api, /getSummary:[\s\S]*\/students\/me\/points/);
  assert.match(api, /getTransactions:[\s\S]*page=\$\{page\}&size=\$\{size\}/);
});

test('points page renders summary, history and all async states', () => {
  const page = read('src/student/StudentPoints.tsx');

  assert.match(page, /可用积分/);
  assert.match(page, /今日获得/);
  assert.match(page, /累计获得/);
  assert.match(page, /累计消耗/);
  assert.match(page, /积分明细/);
  assert.match(page, /正在加载积分/);
  assert.match(page, /重新加载/);
  assert.match(page, /暂无积分明细/);
  assert.match(page, /加载更多/);
});

test('transaction pages are merged by id with stable newest-first ordering', () => {
  const transaction = (id, createdAt, amount = 1) => ({ id, createdAt, amount });
  const merged = mergePointTransactions(
    [transaction(3, '2026-07-21T10:00:00'), transaction(2, '2026-07-21T09:00:00')],
    [transaction(2, '2026-07-21T09:00:00'), transaction(4, '2026-07-21T10:00:00')],
  );

  assert.deepEqual(merged.map(({ id }) => id), [4, 3, 2]);
});

test('bottom navigation exposes current page and visible keyboard focus', () => {
  const workspace = read('src/student/StudentWorkspace.tsx');
  const styles = read('src/student/student-workspace.css');

  assert.match(workspace, /aria-current=\{active \? 'page' : undefined\}/);
  assert.match(styles, /\.bottom-nav button:focus-visible\s*\{[^}]*outline:\s*3px/);
});
