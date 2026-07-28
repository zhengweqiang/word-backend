import { ArrowLeft, CheckCircle, Clock, XCircle } from '@phosphor-icons/react';
import type { StudentPaperResult } from '../types';

interface StudentPaperResultViewProps {
  title: string;
  result: StudentPaperResult;
  onBack: () => void;
}

export function StudentPaperResultView({ title, result, onBack }: StudentPaperResultViewProps) {
  const late = result.status === 'SUBMITTED_LATE';
  return (
    <section className="paper-page paper-result">
      <header className="paper-detail-header">
        <button type="button" className="paper-icon-button" aria-label="返回测验列表" onClick={onBack}><ArrowLeft size={22} /></button>
        <div><p className="eyebrow">RESULT</p><h1>{title}</h1></div>
        <span className={`paper-status${late ? ' paper-status--late' : ''}`}>{late ? '超时提交' : '已提交'}</span>
      </header>

      {!result.scoreVisible ? (
        <div className="paper-result-pending">
          <Clock size={44} weight="duotone" />
          <strong>已成功交卷</strong>
          <p>老师尚未发布成绩，发布后可在这里查看。</p>
        </div>
      ) : (
        <>
          <div className="paper-score">
            <span>本次得分</span>
            <strong>{result.earnedScore}<small> / {result.totalScore}</small></strong>
            <div><span>正确 {result.correctCount} 题</span><span>作答 {result.answeredCount} 题</span><span>{result.scorePercentage}%</span></div>
          </div>
          {!result.answersVisible && <div className="paper-alert">当前仅发布分数，题目答案暂不可查看。</div>}
          {result.answersVisible && <div className="paper-result-list">
            {result.questions.map((question) => (
              <article className={`paper-result-question ${question.correct ? 'is-correct' : 'is-wrong'}`} key={question.releaseQuestionId}>
                <header>{question.correct ? <CheckCircle size={22} weight="fill" /> : <XCircle size={22} weight="fill" />}<strong>{question.questionOrder}. {question.stem}</strong><span>{question.earnedScore}/{question.questionScore} 分</span></header>
                <p>你的答案：{question.questionType === 'FILL_IN_BLANK'
                  ? question.blankAnswers.join('、') || '未作答'
                  : question.selectedAnswers.map((key) => `${key}. ${question.options[key] ?? ''}`).join('；') || '未作答'}</p>
                {!question.correct && <p>参考答案：{question.acceptedAnswers.join('、')}</p>}
                {question.explanation && <div>{question.explanation}</div>}
              </article>
            ))}
          </div>}
        </>
      )}
    </section>
  );
}
