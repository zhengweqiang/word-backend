import {
  BookOpen,
  Books,
  CalendarCheck,
  CaretRight,
  ChartBar,
  Clock,
  Notebook,
  UserCircle,
} from '@phosphor-icons/react';
import type { StudentDashboard } from '../types';
import { dashboardEmptyState } from './student-workspace-state';

interface StudentDashboardHomeProps {
  dashboard: StudentDashboard;
  displayName: string;
  onStart: () => void;
  onOpenLibrary: () => void;
  onOpenProfile: () => void;
}

function ProgressRing({ value }: { value: number }) {
  return (
    <div className="progress-ring" style={{ '--progress': `${value * 3.6}deg` } as React.CSSProperties}>
      <div className="progress-ring__inner"><strong>{value}%</strong><span>完成进度</span></div>
    </div>
  );
}

export function StudentDashboardHome({
  dashboard,
  displayName,
  onStart,
  onOpenLibrary,
  onOpenProfile,
}: StudentDashboardHomeProps) {
  const emptyState = dashboardEmptyState(dashboard);
  const progress = dashboard.totalCount === 0
    ? 0
    : Math.round((dashboard.completedCount / dashboard.totalCount) * 100);
  const rows = [
    { label: '逾期复习', value: dashboard.overdueCount, tone: 'danger', icon: Clock },
    { label: '今日复习', value: dashboard.reviewCount, tone: 'blue', icon: CalendarCheck },
    { label: '新词', value: dashboard.newCount, tone: 'green', icon: BookOpen },
  ];

  return (
    <>
      <section className="hero">
        <div className="hero__topline">
          <div>
            <p className="eyebrow">Student Workspace</p>
            <h1 style={{ '--greeting-size-mobile': displayName.length > 4 ? '36px' : '40px' } as React.CSSProperties}>早上好，{displayName}</h1>
          </div>
          <span className="role-chip">STUDENT</span>
        </div>
        <p className="quote">日拱一卒，功不唐捐。</p>
      </section>

      <section className="mission">
        <div className="mission__summary">
          <div><p className="eyebrow">Today</p><h2>今日任务</h2></div>
          <div className="mission__complete">
            <strong>{dashboard.completedCount}<span>/{dashboard.totalCount}</span></strong>
            <small>已完成</small>
          </div>
        </div>

        {emptyState ? (
          <div className="mission-empty"><strong>{emptyState}</strong><span>{dashboard.hasPlans ? '明天继续保持。' : '等待教师发布学习计划。'}</span></div>
        ) : (
          <>
            <div className="mission__body">
              <ProgressRing value={progress} />
              <div className="mission__rows" aria-label="今日任务分类">
                {rows.map((row) => {
                  const Icon = row.icon;
                  return (
                    <button type="button" key={row.label} className="mission-row" onClick={onStart}>
                      <span className={`icon-badge icon-badge--${row.tone}`}><Icon size={22} /></span>
                      <span>{row.label}</span><strong>{row.value}</strong>
                    </button>
                  );
                })}
              </div>
            </div>
            <button className="primary-action" type="button" onClick={onStart} disabled={!dashboard.queue.length}>
              <BookOpen size={26} />继续今日学习
            </button>
          </>
        )}
      </section>

      {dashboard.reminders.length > 0 && (
        <section className="reminders grouped-list">
          {dashboard.reminders.map((reminder) => (
            <button type="button" className="list-row" key={reminder.code} onClick={onStart}>
              <span className={`icon-badge ${reminder.code === 'OVERDUE_REVIEW' ? 'icon-badge--danger' : 'icon-badge--blue'}`}>
                {reminder.code === 'OVERDUE_REVIEW' ? <Clock size={22} /> : <Notebook size={22} />}
              </span>
              <span>{reminder.message}</span><CaretRight size={20} />
            </button>
          ))}
        </section>
      )}

      <section className="quick-section">
        <div className="section-header"><div><p className="eyebrow">Shortcuts</p><h2>快速入口</h2></div></div>
        <div className="quick-grid">
          <button type="button" className="quick-card" onClick={onOpenLibrary}><Books size={34} /><strong>我的词书</strong><span>已分配 {dashboard.hasPlans ? '可学习' : '待安排'}</span></button>
          <button type="button" className="quick-card" onClick={onStart}><BookOpen size={34} /><strong>继续学习</strong><span>{dashboard.queue.length} 个待学</span></button>
          <button type="button" className="quick-card" onClick={onOpenProfile}><UserCircle size={34} /><strong>个人中心</strong><span>资料与账号</span></button>
          <button type="button" className="quick-card" onClick={onStart}><ChartBar size={34} /><strong>今日进度</strong><span>完成 {progress}%</span></button>
        </div>
      </section>
    </>
  );
}
