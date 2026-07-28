import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowClockwise,
  CaretRight,
  CheckCircle,
  Clock,
  Exam,
  WarningCircle,
} from '@phosphor-icons/react';
import { studentAssessmentApi, studentPaperApi } from '../api';
import type {
  StudentAssessmentSummary,
  StudentAssignedPaperSummary,
  StudentPaperAttempt,
  StudentPaperResult,
} from '../types';
import {
  isFinalPaperStatus,
  isPendingPaperStatus,
  paperAttemptStatusLabels,
} from './student-workspace-state';
import { StudentPaperAttemptView } from './StudentPaperAttempt';
import { StudentPaperResultView } from './StudentPaperResult';

type PaperListMode = 'pending' | 'history';
type PaperScreen =
  | { kind: 'list' }
  | { kind: 'attempt'; value: StudentPaperAttempt }
  | { kind: 'result'; title: string; value: StudentPaperResult };

interface StudentAssignedPapersProps {
  onOpenLegacyExam: (examId: number) => Promise<void>;
  refreshToken: number;
}

function formatDateTime(value?: string | null) {
  if (!value) return '不限';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function StudentAssignedPapers({ onOpenLegacyExam, refreshToken }: StudentAssignedPapersProps) {
  const [papers, setPapers] = useState<StudentAssignedPaperSummary[]>([]);
  const [pendingAssessments, setPendingAssessments] = useState<StudentAssessmentSummary[]>([]);
  const [historyAssessments, setHistoryAssessments] = useState<StudentAssessmentSummary[]>([]);
  const [mode, setMode] = useState<PaperListMode>('pending');
  const [screen, setScreen] = useState<PaperScreen>({ kind: 'list' });
  const [loading, setLoading] = useState(true);
  const [openingId, setOpeningId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [assigned, pending, history] = await Promise.all([
        studentPaperApi.listAssigned(),
        studentAssessmentApi.getPending(),
        studentAssessmentApi.getHistory(),
      ]);
      setPapers(assigned);
      setPendingAssessments(pending);
      setHistoryAssessments(history);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '测验列表加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshToken]);

  const visiblePapers = useMemo(() => papers.filter((paper) => (
    mode === 'pending'
      ? isPendingPaperStatus(paper.attemptStatus)
      : !isPendingPaperStatus(paper.attemptStatus)
  )), [mode, papers]);

  const legacyAssessments = useMemo(() => (
    mode === 'pending' ? pendingAssessments : historyAssessments
  ).filter((assessment) => assessment.assessmentType === 'LEGACY_GENERATED_EXAM'), [historyAssessments, mode, pendingAssessments]);

  const openPaper = async (paper: StudentAssignedPaperSummary) => {
    if (paper.attemptStatus === 'INVALIDATED') return;
    setOpeningId(paper.attemptId);
    setError(null);
    try {
      if (isFinalPaperStatus(paper.attemptStatus)) {
        setScreen({ kind: 'result', title: paper.title, value: await studentPaperApi.getResult(paper.attemptId) });
      } else {
        setScreen({ kind: 'attempt', value: await studentPaperApi.open(paper.attemptId) });
      }
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : '测验打开失败');
    } finally {
      setOpeningId(null);
    }
  };

  const returnToList = () => {
    setScreen({ kind: 'list' });
    void load();
  };

  if (screen.kind === 'attempt') {
    return (
      <StudentPaperAttemptView
        attempt={screen.value}
        onAttemptChange={(value) => setScreen({ kind: 'attempt', value })}
        onBack={returnToList}
        onSubmitted={(response) => setScreen({
          kind: 'result',
          title: screen.value.title,
          value: response.result,
        })}
      />
    );
  }

  if (screen.kind === 'result') {
    return <StudentPaperResultView title={screen.title} result={screen.value} onBack={returnToList} />;
  }

  const empty = visiblePapers.length === 0 && legacyAssessments.length === 0;
  return (
    <section className="paper-page">
      <header className="paper-page__header">
        <div>
          <p className="eyebrow">ASSESSMENTS</p>
          <h1>测验</h1>
        </div>
        <button type="button" className="paper-icon-button" aria-label="刷新测验" onClick={() => void load()} disabled={loading}>
          <ArrowClockwise size={22} className={loading ? 'is-spinning' : ''} />
        </button>
      </header>

      <div className="paper-tabs" role="tablist" aria-label="测验分类">
        <button type="button" role="tab" aria-selected={mode === 'pending'} className={mode === 'pending' ? 'is-active' : ''} onClick={() => setMode('pending')}>
          待完成 <span>{pendingAssessments.length}</span>
        </button>
        <button type="button" role="tab" aria-selected={mode === 'history'} className={mode === 'history' ? 'is-active' : ''} onClick={() => setMode('history')}>
          已完成 <span>{historyAssessments.length}</span>
        </button>
      </div>

      {error && <div className="paper-alert paper-alert--error" role="alert"><WarningCircle size={20} />{error}</div>}
      {loading && <div className="paper-state"><span className="points-spinner" />正在加载测验...</div>}
      {!loading && empty && (
        <div className="paper-empty">
          <CheckCircle size={42} weight="duotone" />
          <strong>{mode === 'pending' ? '暂时没有待完成测验' : '还没有已完成记录'}</strong>
        </div>
      )}

      {!loading && !empty && <div className="paper-list">
        {visiblePapers.map((paper) => {
          const late = paper.attemptStatus === 'OVERDUE' || paper.attemptStatus === 'SUBMITTED_LATE';
          const disabled = paper.attemptStatus === 'INVALIDATED' || openingId === paper.attemptId;
          return (
            <button type="button" className="paper-list-item" key={paper.attemptId} disabled={disabled} onClick={() => void openPaper(paper)}>
              <span className={`paper-list-item__icon${late ? ' is-late' : ''}`}><Exam size={23} weight="duotone" /></span>
              <span className="paper-list-item__body">
                <span className="paper-list-item__topline">
                  <strong>{paper.title}</strong>
                  <span className={`paper-status paper-status--${late ? 'late' : paper.attemptStatus.toLowerCase()}`}>
                    {paperAttemptStatusLabels[paper.attemptStatus]}
                  </span>
                </span>
                <span className="paper-list-item__meta">{paper.questionCount} 题 · {paper.totalScore} 分</span>
                <span className="paper-list-item__time"><Clock size={14} />{paper.releaseStatus === 'SCHEDULED' ? `开始 ${formatDateTime(paper.startTime)}` : `截止 ${formatDateTime(paper.deadline)}`}</span>
              </span>
              {paper.attemptStatus !== 'INVALIDATED' && <CaretRight size={19} />}
            </button>
          );
        })}
        {legacyAssessments.map((assessment) => (
          <button
            type="button"
            className="paper-list-item"
            key={`legacy-${assessment.assessmentId}`}
            disabled={!assessment.legacyExamId || openingId === assessment.assessmentId}
            onClick={async () => {
              if (!assessment.legacyExamId) return;
              setOpeningId(assessment.assessmentId);
              try { await onOpenLegacyExam(assessment.legacyExamId); }
              catch (openError) { setError(openError instanceof Error ? openError.message : '词库测验打开失败'); }
              finally { setOpeningId(null); }
            }}
          >
            <span className="paper-list-item__icon"><Exam size={23} /></span>
            <span className="paper-list-item__body">
              <span className="paper-list-item__topline"><strong>{assessment.title}</strong><span className="paper-status">词库测验</span></span>
              <span className="paper-list-item__meta">{assessment.questionCount} 题{assessment.scoreVisible && assessment.scorePercentage != null ? ` · ${assessment.scorePercentage} 分` : ''}</span>
            </span>
            <CaretRight size={19} />
          </button>
        ))}
      </div>}
    </section>
  );
}
