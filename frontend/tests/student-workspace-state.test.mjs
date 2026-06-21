import assert from 'node:assert/strict';
import test from 'node:test';

import {
  dashboardEmptyState,
  nextAccent,
  taskTypeLabel,
} from '../src/student/student-workspace-state.ts';

test('distinguishes no-plan and completed empty states', () => {
  assert.equal(dashboardEmptyState({ hasPlans: false, allTasksCompleted: false }), '尚未安排学习任务');
  assert.equal(dashboardEmptyState({ hasPlans: true, allTasksCompleted: true }), '今日任务已完成');
  assert.equal(dashboardEmptyState({ hasPlans: true, allTasksCompleted: false }), null);
});

test('maps task types to student-facing labels', () => {
  assert.equal(taskTypeLabel('OVERDUE_REVIEW'), '逾期复习');
  assert.equal(taskTypeLabel('TODAY_REVIEW'), '今日复习');
  assert.equal(taskTypeLabel('NEW_LEARN'), '新词');
});

test('toggles pronunciation accent', () => {
  assert.equal(nextAccent('US'), 'UK');
  assert.equal(nextAccent('UK'), 'US');
});
