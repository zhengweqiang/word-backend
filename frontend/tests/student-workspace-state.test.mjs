import assert from 'node:assert/strict';
import test from 'node:test';

import {
  countAnsweredPaperQuestions,
  createPaperAnswerDraft,
  dashboardEmptyState,
  isFinalPaperStatus,
  isPendingPaperStatus,
  nextAccent,
  paperAttemptStatusLabels,
  taskTypeLabel,
  toPaperAnswerPayload,
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

test('maps every paper attempt state including overdue and late submission', () => {
  assert.equal(paperAttemptStatusLabels.NOT_STARTED, '未开始');
  assert.equal(paperAttemptStatusLabels.IN_PROGRESS, '作答中');
  assert.equal(paperAttemptStatusLabels.SUBMITTED, '已提交');
  assert.equal(paperAttemptStatusLabels.OVERDUE, '已超时');
  assert.equal(paperAttemptStatusLabels.SUBMITTED_LATE, '超时提交');
  assert.equal(paperAttemptStatusLabels.INVALIDATED, '已作废');
  assert.equal(isFinalPaperStatus('SUBMITTED_LATE'), true);
  assert.equal(isPendingPaperStatus('OVERDUE'), true);
  assert.equal(isPendingPaperStatus('INVALIDATED'), false);
});

test('hydrates and serializes all three paper question answer shapes', () => {
  const questions = [
    { id: 11, questionOrder: 1, questionType: 'SINGLE_CHOICE', stem: '单选', options: { A: '一', B: '二' }, score: 2 },
    { id: 12, questionOrder: 2, questionType: 'MULTIPLE_CHOICE', stem: '多选', options: { A: '一', B: '二' }, score: 3 },
    { id: 13, questionOrder: 3, questionType: 'FILL_IN_BLANK', stem: '填空', options: {}, score: 1 },
  ];
  const draft = createPaperAnswerDraft(questions, [
    { releaseQuestionId: 11, selectedAnswers: ['A'], blankAnswers: [] },
    { releaseQuestionId: 13, selectedAnswers: [], blankAnswers: [' abandon '] },
  ]);
  draft[12] = ['A', 'B'];

  assert.equal(countAnsweredPaperQuestions(draft), 3);
  assert.deepEqual(toPaperAnswerPayload(questions, draft), [
    { releaseQuestionId: 11, selectedAnswers: ['A'], blankAnswers: [] },
    { releaseQuestionId: 12, selectedAnswers: ['A', 'B'], blankAnswers: [] },
    { releaseQuestionId: 13, selectedAnswers: [], blankAnswers: ['abandon'] },
  ]);
});
