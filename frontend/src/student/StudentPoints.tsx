import { useEffect, useRef, useState } from 'react';
import {
  ArrowClockwise,
  ArrowDown,
  ArrowUp,
  CalendarCheck,
  Coins,
  Receipt,
  TrendDown,
  TrendUp,
} from '@phosphor-icons/react';
import { studentPointApi } from '../api';
import type {
  Page,
  PointSourceType,
  PointTransactionType,
  StudentPointSummary,
  StudentPointTransaction,
} from '../types';
import { mergePointTransactions } from './point-transactions';

const PAGE_SIZE = 20;

const transactionLabels: Record<PointTransactionType, string> = {
  EARN: '获得积分',
  DEDUCT: '扣减积分',
  FREEZE: '冻结积分',
  UNFREEZE: '解冻积分',
  SPEND: '使用积分',
  REVERSE: '积分冲正',
};

const sourceLabels: Record<PointSourceType, string> = {
  STUDY_TASK: '完成学习任务',
  STUDY_RECORD: '学习记录',
  VIDEO_WATCH: '观看视频',
  EXAM: '考试',
  MANUAL_ADJUSTMENT: '教师调整',
  ADMIN_CORRECTION: '管理员修正',
  REDEMPTION: '积分兑换',
};

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function pointNumberClass(value: number) {
  const digits = Math.abs(value).toString().length;
  if (digits >= 10) return 'points-number--tight';
  if (digits >= 8) return 'points-number--compact';
  return '';
}

function transactionDescription(transaction: StudentPointTransaction) {
  return transaction.reason?.trim()
    || sourceLabels[transaction.sourceType]
    || transactionLabels[transaction.transactionType];
}

export function StudentPoints() {
  const [summary, setSummary] = useState<StudentPointSummary | null>(null);
  const [transactions, setTransactions] = useState<StudentPointTransaction[]>([]);
  const [transactionPage, setTransactionPage] = useState<Page<StudentPointTransaction> | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const loadingMoreRef = useRef(false);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError(null);
      setLoadMoreError(null);
      try {
        const [nextSummary, nextTransactions] = await Promise.all([
          studentPointApi.getSummary(),
          studentPointApi.getTransactions(0, PAGE_SIZE),
        ]);
        if (!cancelled) {
          setSummary(nextSummary);
          setTransactionPage(nextTransactions);
          setTransactions(mergePointTransactions([], nextTransactions.content));
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : '积分加载失败，请稍后重试');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  const loadMore = async () => {
    if (!transactionPage || transactionPage.last || loadingMoreRef.current) {
      return;
    }

    loadingMoreRef.current = true;
    setLoadingMore(true);
    setLoadMoreError(null);
    try {
      const nextPage = await studentPointApi.getTransactions(transactionPage.number + 1, PAGE_SIZE);
      setTransactionPage(nextPage);
      setTransactions((current) => mergePointTransactions(current, nextPage.content));
    } catch (loadError) {
      setLoadMoreError(loadError instanceof Error ? loadError.message : '更多明细加载失败');
    } finally {
      loadingMoreRef.current = false;
      setLoadingMore(false);
    }
  };

  if (loading) {
    return (
      <section className="points-state" aria-live="polite">
        <span className="points-spinner" />
        <strong>正在加载积分...</strong>
      </section>
    );
  }

  if (error || !summary || !transactionPage) {
    return (
      <section className="points-state points-state--error" role="alert">
        <Receipt size={34} />
        <strong>积分暂时无法加载</strong>
        <p>{error ?? '请稍后重试'}</p>
        <button type="button" onClick={() => setReloadToken((current) => current + 1)}>
          <ArrowClockwise size={19} />重新加载
        </button>
      </section>
    );
  }

  return (
    <div className="points-page">
      <header className="points-header">
        <div>
          <p className="eyebrow">Points</p>
          <h1>我的积分</h1>
        </div>
        <Coins size={34} weight="duotone" />
      </header>

      <section className="points-summary" aria-label="积分概况">
        <div className="points-balance">
          <span>可用积分</span>
          <strong className={pointNumberClass(summary.availablePoints)}>{summary.availablePoints}</strong>
          {summary.frozenPoints > 0 && <small>另有 {summary.frozenPoints} 积分冻结中</small>}
        </div>
        <dl className="points-metrics">
          <div><dt><CalendarCheck size={18} />今日获得</dt><dd className={pointNumberClass(summary.todayEarnedPoints)}>+{summary.todayEarnedPoints}</dd></div>
          <div><dt><TrendUp size={18} />累计获得</dt><dd className={pointNumberClass(summary.lifetimeEarnedPoints)}>{summary.lifetimeEarnedPoints}</dd></div>
          <div><dt><TrendDown size={18} />累计消耗</dt><dd className={pointNumberClass(summary.lifetimeSpentPoints)}>{summary.lifetimeSpentPoints}</dd></div>
        </dl>
      </section>

      <section className="points-history" aria-labelledby="points-history-title">
        <div className="section-header">
          <div><p className="eyebrow">History</p><h2 id="points-history-title">积分明细</h2></div>
          <span className="subtle-count">共 {transactionPage.totalElements} 条</span>
        </div>

        {transactions.length === 0 ? (
          <div className="points-empty">
            <Receipt size={32} />
            <strong>暂无积分明细</strong>
            <span>完成学习任务后，积分变动会显示在这里。</span>
          </div>
        ) : (
          <div className="points-transaction-list">
            {transactions.map((transaction) => {
              const positive = transaction.amount > 0;
              const AmountIcon = positive ? ArrowUp : ArrowDown;
              return (
                <article className="points-transaction" key={transaction.id}>
                  <span className={`points-transaction__icon ${positive ? 'is-positive' : 'is-negative'}`}>
                    <AmountIcon size={19} weight="bold" />
                  </span>
                  <div className="points-transaction__content">
                    <strong>{transactionDescription(transaction)}</strong>
                    <span>{transactionLabels[transaction.transactionType]} · {formatDateTime(transaction.createdAt)}</span>
                  </div>
                  <div className={`points-transaction__amount ${positive ? 'is-positive' : 'is-negative'}`}>
                    <strong>{positive ? '+' : ''}{transaction.amount}</strong>
                    <span>余额 {transaction.balanceAfter}</span>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {loadMoreError && <p className="points-load-more-error" role="alert">{loadMoreError}</p>}
        {!transactionPage.last && (
          <button className="points-load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>
            {loadingMore ? <span className="points-spinner" /> : <ArrowDown size={18} />}
            {loadingMore ? '正在加载...' : '加载更多'}
          </button>
        )}
      </section>
    </div>
  );
}
