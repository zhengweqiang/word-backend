import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Check, Clock, FloppyDisk, PaperPlaneTilt, Warning } from '@phosphor-icons/react';
import { studentPaperApi } from '../api';
import type { StudentPaperAttempt, SubmitStudentPaperResponse } from '../types';
import {
  countAnsweredPaperQuestions,
  createPaperAnswerDraft,
  paperAttemptStatusLabels,
  toPaperAnswerPayload,
  type PaperAnswerDraft,
} from './student-workspace-state';

interface StudentPaperAttemptViewProps {
  attempt: StudentPaperAttempt;
  onAttemptChange: (attempt: StudentPaperAttempt) => void;
  onBack: () => void;
  onSubmitted: (response: SubmitStudentPaperResponse) => void;
}

function formatFullDateTime(value?: string | null) {
  if (!value) return '不限';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(date);
}

export function StudentPaperAttemptView({ attempt, onAttemptChange, onBack, onSubmitted }: StudentPaperAttemptViewProps) {
  const [draft, setDraft] = useState<PaperAnswerDraft>(() => createPaperAnswerDraft(attempt.questions, attempt.answers));
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraft(createPaperAnswerDraft(attempt.questions, attempt.answers));
    setDirty(false);
  }, [attempt]);

  const answeredCount = useMemo(() => countAnsweredPaperQuestions(draft), [draft]);
  const late = attempt.attemptStatus === 'OVERDUE';
  const scheduled = attempt.releaseStatus === 'SCHEDULED' && !attempt.answerable;

  const updateAnswer = (questionId: number, values: string[]) => {
    setDraft((current) => ({ ...current, [questionId]: values }));
    setDirty(true);
    setFeedback(null);
    setError(null);
  };

  const saveDraft = async () => {
    if (!attempt.answerable || saving || submitting) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await studentPaperApi.saveDraft(
        attempt.attemptId,
        attempt.version,
        toPaperAnswerPayload(attempt.questions, draft),
      );
      onAttemptChange(updated);
      setFeedback('草稿已保存');
      setDirty(false);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '草稿保存失败，请重新打开试卷');
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    if (!attempt.answerable || submitting || saving) return;
    if (attempt.blankAnswerPolicy === 'REQUIRE_ALL_ANSWERED' && answeredCount < attempt.questionCount) {
      setError(`还有 ${attempt.questionCount - answeredCount} 题未作答，当前试卷要求全部作答后提交。`);
      return;
    }
    const message = late
      ? '本次提交会被标记为超时提交，确认交卷吗？'
      : `确认提交吗？提交后不能再修改，当前已作答 ${answeredCount}/${attempt.questionCount} 题。`;
    if (!window.confirm(message)) return;
    setSubmitting(true);
    setError(null);
    try {
      onSubmitted(await studentPaperApi.submit(
        attempt.attemptId,
        attempt.version,
        toPaperAnswerPayload(attempt.questions, draft),
      ));
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '提交失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="paper-page paper-attempt">
      <header className="paper-detail-header">
        <button type="button" className="paper-icon-button" aria-label="返回测验列表" onClick={onBack}><ArrowLeft size={22} /></button>
        <div><p className="eyebrow">PAPER</p><h1>{attempt.title}</h1></div>
        <span className={`paper-status${late ? ' paper-status--late' : ''}`}>{paperAttemptStatusLabels[attempt.attemptStatus]}</span>
      </header>

      <div className="paper-overview">
        <p>{attempt.instructions || '请独立完成本次测验。'}</p>
        <dl>
          <div><dt>题目</dt><dd>{attempt.questionCount} 题</dd></div>
          <div><dt>总分</dt><dd>{attempt.totalScore} 分</dd></div>
          <div><dt>开始</dt><dd>{formatFullDateTime(attempt.startTime)}</dd></div>
          <div><dt>截止</dt><dd>{formatFullDateTime(attempt.deadline)}</dd></div>
        </dl>
      </div>

      {scheduled && (
        <div className="paper-scheduled">
          <Clock size={38} weight="duotone" />
          <strong>尚未到作答时间</strong>
          <span>{formatFullDateTime(attempt.startTime)} 开始</span>
          <button type="button" className="primary-action primary-action--compact" disabled>等待开始</button>
        </div>
      )}
      {!scheduled && !attempt.answerable && (
        <div className="paper-alert"><Warning size={20} />这份试卷当前不可继续作答。</div>
      )}
      {late && (
        <div className="paper-alert paper-alert--late"><Warning size={20} />已超过截止时间，你仍可继续作答，提交后会标记为超时。</div>
      )}
      {error && <div className="paper-alert paper-alert--error" role="alert"><Warning size={20} />{error}</div>}
      {feedback && <div className="paper-alert paper-alert--success"><Check size={20} />{feedback}</div>}

      {attempt.questions.length > 0 && <div className="paper-question-list">
        {attempt.questions.map((question) => {
          const values = draft[question.id] ?? [];
          return (
            <fieldset className="paper-question" key={question.id} disabled={!attempt.answerable || saving || submitting}>
              <legend><span>{question.questionOrder}</span><strong>{question.stem}</strong><small>{question.score} 分</small></legend>
              {question.questionType === 'FILL_IN_BLANK' ? (
                <input
                  type="text"
                  aria-label={`第 ${question.questionOrder} 题答案`}
                  value={values[0] ?? ''}
                  onChange={(event) => updateAnswer(question.id, [event.target.value])}
                  placeholder="填写答案"
                  autoComplete="off"
                />
              ) : (
                <div className="paper-options">
                  {Object.entries(question.options).map(([key, label]) => {
                    const selected = values.includes(key);
                    return (
                      <label className={selected ? 'is-selected' : ''} key={key}>
                        <input
                          type={question.questionType === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'}
                          name={`paper-question-${question.id}`}
                          checked={selected}
                          onChange={() => updateAnswer(
                            question.id,
                            question.questionType === 'SINGLE_CHOICE'
                              ? [key]
                              : selected ? values.filter((value) => value !== key) : [...values, key],
                          )}
                        />
                        <span>{key}</span><strong>{label}</strong>
                      </label>
                    );
                  })}
                </div>
              )}
            </fieldset>
          );
        })}
      </div>}

      {attempt.answerable && <footer className="paper-submit-bar">
        <div><strong>{answeredCount}/{attempt.questionCount}</strong><span>{dirty ? '有未保存修改' : '草稿已同步'}</span></div>
        <button type="button" className="paper-secondary-action" onClick={() => void saveDraft()} disabled={!dirty || saving || submitting}>
          <FloppyDisk size={20} />{saving ? '保存中' : '存草稿'}
        </button>
        <button type="button" className="paper-submit-action" onClick={() => void submit()} disabled={saving || submitting}>
          <PaperPlaneTilt size={20} />{submitting ? '提交中' : '交卷'}
        </button>
      </footer>}
    </section>
  );
}
