import type { StudyTaskType } from '../types';

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
