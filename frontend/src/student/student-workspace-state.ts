import type {
  StudentPaperAnswer,
  StudentPaperAnswerPayload,
  StudentPaperAttemptStatus,
  StudentPaperQuestion,
  StudyTaskType,
} from '../types';

export type PronunciationAccent = 'US' | 'UK';

interface DashboardStateInput {
  hasPlans: boolean;
  allTasksCompleted: boolean;
}

export function dashboardEmptyState(state: DashboardStateInput): string | null {
  if (!state.hasPlans) {
    return '尚未安排学习任务';
  }
  if (state.allTasksCompleted) {
    return '今日任务已完成';
  }
  return null;
}

export function taskTypeLabel(taskType: StudyTaskType): string {
  switch (taskType) {
    case 'OVERDUE_REVIEW':
      return '逾期复习';
    case 'TODAY_REVIEW':
      return '今日复习';
    case 'NEW_LEARN':
      return '新词';
  }
}

export function nextAccent(accent: PronunciationAccent): PronunciationAccent {
  return accent === 'US' ? 'UK' : 'US';
}

export type PaperAnswerDraft = Record<number, string[]>;

export const paperAttemptStatusLabels: Record<StudentPaperAttemptStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '作答中',
  SUBMITTED: '已提交',
  OVERDUE: '已超时',
  SUBMITTED_LATE: '超时提交',
  INVALIDATED: '已作废',
};

export function isFinalPaperStatus(status: StudentPaperAttemptStatus): boolean {
  return status === 'SUBMITTED' || status === 'SUBMITTED_LATE';
}

export function isPendingPaperStatus(status: StudentPaperAttemptStatus): boolean {
  return !isFinalPaperStatus(status) && status !== 'INVALIDATED';
}

export function createPaperAnswerDraft(
  questions: StudentPaperQuestion[],
  answers: StudentPaperAnswer[],
): PaperAnswerDraft {
  const draft: PaperAnswerDraft = Object.fromEntries(questions.map((question) => [question.id, []]));
  answers.forEach((answer) => {
    draft[answer.releaseQuestionId] = answer.selectedAnswers.length > 0
      ? [...answer.selectedAnswers]
      : [...answer.blankAnswers];
  });
  return draft;
}

export function toPaperAnswerPayload(
  questions: StudentPaperQuestion[],
  draft: PaperAnswerDraft,
): StudentPaperAnswerPayload[] {
  return questions.map((question) => ({
    releaseQuestionId: question.id,
    selectedAnswers: question.questionType === 'FILL_IN_BLANK' ? [] : (draft[question.id] ?? []),
    blankAnswers: question.questionType === 'FILL_IN_BLANK'
      ? (draft[question.id] ?? []).map((value) => value.trim()).filter(Boolean).slice(0, 1)
      : [],
  }));
}

export function countAnsweredPaperQuestions(draft: PaperAnswerDraft): number {
  return Object.values(draft).filter((answers) => answers.some((answer) => answer.trim().length > 0)).length;
}
