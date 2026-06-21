import { useEffect, useRef, useState } from 'react';
import { CaretRight, Clock } from '@phosphor-icons/react';
import { studentDashboardApi } from '../api';
import type { StudentDashboard, StudentDashboardTaskItem, StudyRecordResult } from '../types';
import { SyllableReader } from './SyllableReader';
import { taskTypeLabel } from './student-workspace-state';

interface StudentStudySessionProps {
  dashboard: StudentDashboard;
  onDashboardChange: (dashboard: StudentDashboard) => void;
  onBackHome: () => void;
}

export function StudentStudySession({ dashboard, onDashboardChange, onBackHome }: StudentStudySessionProps) {
  const current = dashboard.queue[0];
  const startedAtRef = useRef(Date.now());
  const hiddenAtRef = useRef<number | null>(null);
  const hiddenSecondsRef = useRef(0);
  const interactionCountRef = useRef(0);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showPlanNote, setShowPlanNote] = useState(false);

  useEffect(() => {
    startedAtRef.current = Date.now();
    hiddenAtRef.current = null;
    hiddenSecondsRef.current = 0;
    interactionCountRef.current = 0;
    setElapsedSeconds(0);
    setError(null);
    setNotice(null);
    const timer = window.setInterval(() => setElapsedSeconds(Math.floor((Date.now() - startedAtRef.current) / 1000)), 1000);
    return () => window.clearInterval(timer);
  }, [current?.studentStudyPlanId, current?.studyDayTaskItemId]);

  useEffect(() => {
    const handleVisibility = () => {
      if (document.hidden) {
        hiddenAtRef.current = Date.now();
      } else if (hiddenAtRef.current !== null) {
        hiddenSecondsRef.current += Math.floor((Date.now() - hiddenAtRef.current) / 1000);
        hiddenAtRef.current = null;
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, []);

  const noteInteraction = () => {
    interactionCountRef.current += 1;
  };

  const record = async (result: StudyRecordResult) => {
    if (!current || submitting) return;
    noteInteraction();
    setSubmitting(true);
    setError(null);
    setNotice(result === 'CORRECT' ? '已记录：我会了' : result === 'INCORRECT' ? '已移到队尾，稍后再学' : '已跳过当前单词');
    const durationSeconds = Math.max(1, Math.floor((Date.now() - startedAtRef.current) / 1000));
    const currentHiddenSeconds = hiddenAtRef.current === null ? 0 : Math.floor((Date.now() - hiddenAtRef.current) / 1000);
    const idleSeconds = Math.min(durationSeconds, hiddenSecondsRef.current + currentHiddenSeconds);
    const focusSeconds = Math.max(0, durationSeconds - idleSeconds);
    const focusRatio = focusSeconds / durationSeconds;

    try {
      const nextDashboard = await studentDashboardApi.record({
        studentStudyPlanId: current.studentStudyPlanId,
        metaWordId: current.metaWordId,
        actionType: current.taskType === 'NEW_LEARN' ? 'LEARN' : 'REVIEW',
        result,
        durationSeconds,
        focusSeconds,
        idleSeconds,
        interactionCount: interactionCountRef.current,
        attentionState: focusRatio >= 0.8 ? 'FOCUSED' : focusRatio >= 0.45 ? 'MIXED' : 'IDLE',
      });
      onDashboardChange(nextDashboard);
    } catch (recordError) {
      setNotice(null);
      setError(recordError instanceof Error ? recordError.message : '学习记录保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (!current) {
    return (
      <section className="study-complete">
        <p className="eyebrow">Today</p><h1>今日任务已完成</h1>
        <p>今天的学习记录已经保存，明天继续保持。</p>
        <button className="primary-action primary-action--compact" type="button" onClick={onBackHome}>返回首页</button>
      </section>
    );
  }

  return (
    <>
      <div className="section-header">
        <div><p className="eyebrow">Study Session</p><h2>今日学习</h2></div>
        <span className="status-pill status-pill--danger">逾期优先</span>
      </div>

      <section className="study-card">
        <div className="study-card__meta">
          <span className={`status-pill status-pill--${current.taskType === 'NEW_LEARN' ? 'green' : 'danger'}`}>{taskTypeLabel(current.taskType)}</span>
          <button type="button" onClick={() => setShowPlanNote((value) => !value)} aria-expanded={showPlanNote}>
            <span>{current.planName}</span><CaretRight size={16} />
          </button>
        </div>
        {showPlanNote && <p className="context-note">来自“{current.planName}”，系统已按到期时间和计划顺序合并学习队列。</p>}

        <WordLearningContent item={current} onInteraction={noteInteraction} />

        <div className="focus-strip">
          <Clock size={20} /><span>当前停留时间</span>
          <strong>{Math.floor(elapsedSeconds / 60)}分{String(elapsedSeconds % 60).padStart(2, '0')}秒</strong>
        </div>
        {notice && <div className="result-toast result-toast--known">{notice}</div>}
        {error && <div className="result-toast result-toast--again" role="alert">{error}</div>}
        <div className="study-actions">
          <button type="button" disabled={submitting} onClick={() => void record('SKIPPED')}>先跳过</button>
          <button type="button" disabled={submitting} onClick={() => void record('INCORRECT')}>再学一次</button>
          <button className="primary-action primary-action--compact" type="button" disabled={submitting} onClick={() => void record('CORRECT')}>
            {submitting ? '保存中' : '我会了'}
          </button>
        </div>
      </section>

      <section className="queue-panel">
        <div className="section-header"><div><p className="eyebrow">Queue</p><h2>接下来</h2></div><span className="subtle-count">{dashboard.queue.length} 个待学任务</span></div>
        <div className="grouped-list">
          {dashboard.queue.slice(0, 5).map((item, index) => (
            <div key={`${item.studentStudyPlanId}-${item.studyDayTaskItemId}`} className="list-row list-row--static">
              <span className="queue-index">{index + 1}</span>
              <div><strong>{item.word || '未命名单词'}</strong><small>{item.phonetic || item.translation || item.definition || item.planName}</small></div>
              <span className={`status-pill status-pill--${item.taskType === 'NEW_LEARN' ? 'green' : 'blue'}`}>{taskTypeLabel(item.taskType)}</span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

function WordLearningContent({ item, onInteraction }: { item: StudentDashboardTaskItem; onInteraction: () => void }) {
  const phonetic = item.phoneticDetail?.us || item.phonetic || item.phoneticDetail?.uk;
  return (
    <article className="word-learning-content">
      <h1>{item.word || '未命名单词'}</h1>
      {phonetic && <p className="phonetic">{phonetic}</p>}
      <p className="translation">
        {item.partOfSpeech && <strong>{item.partOfSpeech} </strong>}
        {item.definition || item.translation || '暂无释义'}
      </p>
      <SyllableReader word={item.word || ''} detail={item.syllableDetail} onInteraction={onInteraction} />
      {item.exampleSentence && <div className="example-strip"><span>例句</span><p>{item.exampleSentence}</p></div>}
    </article>
  );
}
