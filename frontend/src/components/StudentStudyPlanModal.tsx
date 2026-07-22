import { useEffect, useRef, useState } from 'react';
import { studentStudyPlanApi } from '../api';
import type {
  AttentionState,
  RecordStudyPayload,
  StudentAttentionDailyStat,
  StudentStudyPlanSummary,
  StudyRecordResult,
  StudyTask,
  StudyTaskItem,
} from '../types';
import {
  clearPendingStudySubmission,
  preparePendingStudySubmission,
  type PendingStudySubmission,
} from '../student/study-submission-idempotency';

type RecordStudyAttempt = Omit<RecordStudyPayload, 'requestKey'>;

interface StudentStudyPlanModalProps {
  isOpen: boolean;
  onClose: () => void;
}

function formatPercent(value?: number | null) {
  return `${Number(value ?? 0).toFixed(0)}%`;
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '暂无记录';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function planStatusLabel(status: StudentStudyPlanSummary['todayStatus']) {
  switch (status) {
    case 'NOT_STARTED':
      return '未开始';
    case 'IN_PROGRESS':
      return '进行中';
    case 'COMPLETED':
      return '已完成';
    case 'MISSED':
      return '已缺勤';
    default:
      return status;
  }
}

function taskTypeLabel(taskType: StudyTaskItem['taskType']) {
  switch (taskType) {
    case 'NEW_LEARN':
      return '今日新词';
    case 'TODAY_REVIEW':
      return '今日复习';
    case 'OVERDUE_REVIEW':
      return '逾期复习';
    default:
      return taskType;
  }
}

function resolveAttentionState(durationSeconds: number, idleSeconds: number): AttentionState {
  if (idleSeconds >= durationSeconds) {
    return 'IDLE';
  }
  if (idleSeconds > 0) {
    return 'MIXED';
  }
  return 'FOCUSED';
}

export function StudentStudyPlanModal({ isOpen, onClose }: StudentStudyPlanModalProps) {
  const [plans, setPlans] = useState<StudentStudyPlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [task, setTask] = useState<StudyTask | null>(null);
  const [attentionStats, setAttentionStats] = useState<StudentAttentionDailyStat[]>([]);
  const [loadingPlans, setLoadingPlans] = useState(false);
  const [loadingTask, setLoadingTask] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const currentItem = task?.queue[0] ?? null;
  const selectedPlan = plans.find((plan) => plan.studentStudyPlanId === selectedPlanId) ?? null;
  const startedAtRef = useRef<number | null>(null);
  const hiddenStartedAtRef = useRef<number | null>(null);
  const hiddenAccumulatedMsRef = useRef(0);
  const interactionCountRef = useRef(0);
  const pendingSubmissionRef = useRef<PendingStudySubmission<RecordStudyAttempt> | null>(null);

  const resetTracking = () => {
    startedAtRef.current = Date.now();
    hiddenStartedAtRef.current = document.visibilityState === 'hidden' ? Date.now() : null;
    hiddenAccumulatedMsRef.current = 0;
    interactionCountRef.current = 0;
    setElapsedSeconds(0);
  };

  const loadPlans = async (preferredPlanId?: number) => {
    setLoadingPlans(true);
    setError(null);
    try {
      const nextPlans = await studentStudyPlanApi.listMine();
      setPlans(nextPlans);

      const nextSelectedPlanId = preferredPlanId
        ?? (selectedPlanId && nextPlans.some((plan) => plan.studentStudyPlanId === selectedPlanId)
          ? selectedPlanId
          : nextPlans[0]?.studentStudyPlanId ?? null);
      setSelectedPlanId(nextSelectedPlanId);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载学习计划失败');
    } finally {
      setLoadingPlans(false);
    }
  };

  const loadTaskBundle = async (studentStudyPlanId: number) => {
    setSelectedPlanId(studentStudyPlanId);
    setLoadingTask(true);
    setError(null);
    try {
      const [nextTask, nextAttentionStats] = await Promise.all([
        studentStudyPlanApi.getTodayTask(studentStudyPlanId),
        studentStudyPlanApi.getAttention(studentStudyPlanId),
      ]);
      setTask(nextTask);
      setAttentionStats(nextAttentionStats);
      setSuccessMessage(nextTask.queue.length === 0 ? '今天的学习任务已经完成。' : null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载今日学习任务失败');
    } finally {
      setLoadingTask(false);
    }
  };

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setTask(null);
    setAttentionStats([]);
    setSuccessMessage(null);
    void loadPlans();
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || !currentItem) {
      startedAtRef.current = null;
      hiddenStartedAtRef.current = null;
      hiddenAccumulatedMsRef.current = 0;
      interactionCountRef.current = 0;
      setElapsedSeconds(0);
      return;
    }

    resetTracking();
    const tickTimer = window.setInterval(() => {
      if (startedAtRef.current === null) {
        setElapsedSeconds(0);
        return;
      }
      setElapsedSeconds(Math.max(1, Math.round((Date.now() - startedAtRef.current) / 1000)));
    }, 1000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        hiddenStartedAtRef.current = Date.now();
      } else if (hiddenStartedAtRef.current !== null) {
        hiddenAccumulatedMsRef.current += Date.now() - hiddenStartedAtRef.current;
        hiddenStartedAtRef.current = null;
      }
    };

    const registerInteraction = () => {
      interactionCountRef.current += 1;
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('pointerdown', registerInteraction, true);
    window.addEventListener('keydown', registerInteraction, true);

    return () => {
      window.clearInterval(tickTimer);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('pointerdown', registerInteraction, true);
      window.removeEventListener('keydown', registerInteraction, true);
    };
  }, [currentItem, isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleOpenPlan = async (studentStudyPlanId: number) => {
    setSuccessMessage(null);
    await loadTaskBundle(studentStudyPlanId);
  };

  const handleRecord = async (result: StudyRecordResult) => {
    if (!selectedPlanId || !currentItem || startedAtRef.current === null) {
      return;
    }

    const now = Date.now();
    let idleMs = hiddenAccumulatedMsRef.current;
    if (hiddenStartedAtRef.current !== null) {
      idleMs += now - hiddenStartedAtRef.current;
    }

    const durationSeconds = Math.max(1, Math.round((now - startedAtRef.current) / 1000));
    const idleSeconds = Math.max(0, Math.round(idleMs / 1000));
    const focusSeconds = Math.max(0, durationSeconds - idleSeconds);
    const identity = [selectedPlanId, currentItem.studyDayTaskItemId, result].join(':');
    const persistence = {
      storageKey: `word:study-plan-modal:${selectedPlanId}`,
    };
    const pending = preparePendingStudySubmission<RecordStudyAttempt>(
      pendingSubmissionRef.current,
      identity,
      () => ({
        metaWordId: currentItem.metaWordId,
        actionType: currentItem.taskType === 'NEW_LEARN' ? 'LEARN' : 'REVIEW',
        result,
        durationSeconds,
        focusSeconds,
        idleSeconds,
        interactionCount: interactionCountRef.current,
        attentionState: resolveAttentionState(durationSeconds, idleSeconds),
      }),
      undefined,
      persistence,
    );
    pendingSubmissionRef.current = pending;

    setSubmitting(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const nextTask = await studentStudyPlanApi.record(selectedPlanId, pending.payload);
      pendingSubmissionRef.current = clearPendingStudySubmission(
        pendingSubmissionRef.current,
        persistence,
      );

      const [nextPlans, nextAttentionStats] = await Promise.all([
        studentStudyPlanApi.listMine(),
        studentStudyPlanApi.getAttention(selectedPlanId),
      ]);

      setTask(nextTask);
      setPlans(nextPlans);
      setAttentionStats(nextAttentionStats);
      if (nextTask.queue.length === 0) {
        setSuccessMessage('今天的学习任务已完成，停留统计也已经同步保存。');
      }
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '提交学习结果失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">今日学习</h2>
            <p className="user-management__subtitle">点击计划即可进入今天的队列，系统会自动记录每个单词的停留时间。</p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={submitting}>
            &times;
          </button>
        </div>

        <div className="modal__form study-plan-modal">
          <section className="study-plan-modal__column">
            <div className="study-plan-modal__section-header">
              <div>
                <p className="panel__eyebrow">My Plans</p>
                <h3 className="study-plan-modal__section-title">我的学习计划</h3>
              </div>
              <button className="exam-history-btn" onClick={() => void loadPlans(selectedPlanId ?? undefined)} disabled={loadingPlans}>
                {loadingPlans ? '刷新中...' : '刷新'}
              </button>
            </div>

            {plans.length === 0 ? (
              <div className="study-plan-empty">老师发布学习计划后，这里会出现你的今日任务。</div>
            ) : (
              <div className="study-plan-card-list">
                {plans.map((plan) => (
                  <article
                    key={plan.studentStudyPlanId}
                    className={`study-plan-card ${selectedPlanId === plan.studentStudyPlanId ? 'study-plan-card--selected' : ''}`}
                  >
                    <button
                      type="button"
                      className="study-plan-card__body"
                      onClick={() => void handleOpenPlan(plan.studentStudyPlanId)}
                    >
                      <div className="study-plan-card__meta">
                        <span className={`study-plan-status study-plan-status--${plan.todayStatus.toLowerCase()}`}>
                          {planStatusLabel(plan.todayStatus)}
                        </span>
                        <span>{plan.dictionaryName}</span>
                      </div>
                      <h4 className="study-plan-card__title">{plan.planName}</h4>
                      <p className="study-plan-card__summary">
                        今日完成 {plan.completedCount}/{plan.totalTaskCount}，连续学习 {plan.currentStreak} 天
                      </p>
                      <p className="study-plan-card__summary">
                        平均停留 {Number(plan.avgFocusSeconds ?? 0).toFixed(1)} 秒 / 注意力 {formatPercent(plan.attentionScore)}
                      </p>
                      <p className="study-plan-card__summary">最近学习：{formatDateTime(plan.lastStudyAt)}</p>
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="study-plan-modal__column">
            <div className="study-plan-modal__section-header">
              <div>
                <p className="panel__eyebrow">Study Session</p>
                <h3 className="study-plan-modal__section-title">今日任务</h3>
              </div>
              {selectedPlan && <span className="panel__count">{selectedPlan.planName}</span>}
            </div>

            {error && <div className="form__error">{error}</div>}
            {successMessage && <div className="form__success">{successMessage}</div>}

            {loadingTask ? (
              <div className="study-plan-empty">正在加载今天的单词队列...</div>
            ) : task && currentItem ? (
              <div className="study-task-session">
                <div className="study-task-session__summary">
                  <div className="study-plan-overview__item">
                    <span>完成进度</span>
                    <strong>{task.completedCount}/{task.completedCount + task.queue.length}</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>今日停留</span>
                    <strong>{task.totalFocusSeconds} 秒</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>平均停留</span>
                    <strong>{Number(task.avgFocusSecondsPerWord ?? 0).toFixed(1)} 秒</strong>
                  </div>
                  <div className="study-plan-overview__item">
                    <span>注意力</span>
                    <strong>{formatPercent(task.attentionScore)}</strong>
                  </div>
                </div>

                <div className="study-task-card">
                  <div className="study-task-card__meta">
                    <span className={`study-plan-status study-plan-status--${currentItem.taskType.toLowerCase()}`}>
                      {taskTypeLabel(currentItem.taskType)}
                    </span>
                    <span>阶段 {currentItem.phase}</span>
                    <span>当前停留 {elapsedSeconds} 秒</span>
                  </div>
                  <h4 className="study-task-card__word">{currentItem.word ?? '未命名单词'}</h4>
                  <p className="study-task-card__phonetic">{currentItem.phonetic || '暂无音标'}</p>
                  <p className="study-task-card__translation">{currentItem.translation || '暂无释义'}</p>

                  <div className="study-task-card__actions">
                    <button
                      type="button"
                      className="btn btn--secondary"
                      onClick={() => void handleRecord('SKIPPED')}
                      disabled={submitting}
                    >
                      {submitting ? '提交中...' : '先跳过'}
                    </button>
                    <button
                      type="button"
                      className="exam-history-btn"
                      onClick={() => void handleRecord('INCORRECT')}
                      disabled={submitting}
                    >
                      {submitting ? '提交中...' : '再学一次'}
                    </button>
                    <button
                      type="button"
                      className="exam-trigger-btn"
                      onClick={() => void handleRecord('CORRECT')}
                      disabled={submitting}
                    >
                      {submitting ? '提交中...' : '我会了'}
                    </button>
                  </div>
                </div>

                <div className="study-task-queue">
                  <div className="study-plan-modal__section-header">
                    <div>
                      <p className="panel__eyebrow">Queue</p>
                      <h4 className="study-plan-modal__section-title">剩余队列</h4>
                    </div>
                    <span className="panel__count">{task.queue.length} 个词</span>
                  </div>

                  <div className="study-task-queue__list">
                    {task.queue.map((item, index) => (
                      <div
                        key={`${item.metaWordId}-${index}`}
                        className={`study-task-queue__item ${index === 0 ? 'study-task-queue__item--current' : ''}`}
                      >
                        <strong>{item.word || '未命名单词'}</strong>
                        <span>{taskTypeLabel(item.taskType)}</span>
                        <span>阶段 {item.phase}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : selectedPlanId ? (
              <div className="study-plan-empty">今天已经完成全部任务，可以查看下方停留统计。</div>
            ) : (
              <div className="study-plan-empty">从左侧选择一个学习计划，就能开始今天的任务。</div>
            )}

            <div className="study-plan-attention">
              <div className="study-plan-modal__section-header">
                <div>
                  <p className="panel__eyebrow">Attention</p>
                  <h4 className="study-plan-modal__section-title">我的停留统计</h4>
                </div>
                <span className="panel__count">{attentionStats.length} 天</span>
              </div>

              {attentionStats.length === 0 ? (
                <div className="study-plan-empty">开始学习后，这里会展示你每天的单词停留和注意力统计。</div>
              ) : (
                <div className="study-plan-attention-list">
                  {attentionStats.map((dailyStat) => (
                    <div key={dailyStat.taskDate} className="study-plan-attention-list__item">
                      <strong>{dailyStat.taskDate}</strong>
                      <span>访问 {dailyStat.wordsVisited} 个词</span>
                      <span>完成 {dailyStat.wordsCompleted} 个词</span>
                      <span>总停留 {dailyStat.totalFocusSeconds} 秒</span>
                      <span>均值 {Number(dailyStat.avgFocusSecondsPerWord ?? 0).toFixed(1)} 秒</span>
                      <span>长停留 {dailyStat.longStayWordCount} 次</span>
                      <span>空闲中断 {dailyStat.idleInterruptCount} 次</span>
                      <span>注意力 {formatPercent(dailyStat.attentionScore)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
