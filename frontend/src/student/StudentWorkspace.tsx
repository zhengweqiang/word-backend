import { useCallback, useEffect, useRef, useState } from 'react';
import { BookOpen, House, Notebook, UserCircle, VideoCamera } from '@phosphor-icons/react';
import { studentDashboardApi } from '../api';
import type { Dictionary, StudentDashboard, User } from '../types';
import { StudentDashboardHome } from './StudentDashboardHome';
import { StudentLibrary } from './StudentLibrary';
import { StudentProfile } from './StudentProfile';
import { StudentStudySession } from './StudentStudySession';
import { StudentVideos } from './StudentVideos';
import './student-workspace.css';

type StudentTab = 'home' | 'study' | 'library' | 'videos' | 'profile';

interface StudentWorkspaceProps {
  user: User;
  dictionaries: Dictionary[];
  onSignOut: () => void;
}

const navItems = [
  { id: 'home' as const, label: '首页', icon: House },
  { id: 'study' as const, label: '学习', icon: BookOpen },
  { id: 'library' as const, label: '词库', icon: Notebook },
  { id: 'videos' as const, label: '视频', icon: VideoCamera },
  { id: 'profile' as const, label: '我的', icon: UserCircle },
];

export function StudentWorkspace({ user, dictionaries, onSignOut }: StudentWorkspaceProps) {
  const [tab, setTab] = useState<StudentTab>('home');
  const [dashboard, setDashboard] = useState<StudentDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDashboard(await studentDashboardApi.get());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '学习工作台加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: 0, behavior: 'auto' });
  }, [tab]);

  const openTab = (nextTab: StudentTab) => {
    setTab(nextTab);
    if (nextTab === 'home' || nextTab === 'study') {
      void loadDashboard();
    }
  };

  return (
    <main className="prototype-shell">
      <div className="phone-frame" aria-label={`学生工作台，当前页面：${navItems.find((item) => item.id === tab)?.label}`}>
        <div className="phone-scroll" ref={scrollRef}>
          {loading && (tab === 'home' || tab === 'study') && (
            <div className="student-loading"><span />正在整理今日任务...</div>
          )}
          {error && (
            <div className="student-load-error" role="alert">
              <p>{error}</p>
              <button type="button" onClick={() => void loadDashboard()}>重新加载</button>
            </div>
          )}
          {!loading && !error && dashboard && tab === 'home' && (
            <StudentDashboardHome
              dashboard={dashboard}
              displayName={user.displayName}
              onStart={() => setTab('study')}
              onOpenLibrary={() => setTab('library')}
              onOpenProfile={() => setTab('profile')}
            />
          )}
          {!loading && !error && dashboard && tab === 'study' && (
            <StudentStudySession
              dashboard={dashboard}
              onDashboardChange={setDashboard}
              onBackHome={() => setTab('home')}
            />
          )}
          {tab === 'library' && <StudentLibrary dictionaries={dictionaries} />}
          {tab === 'videos' && <StudentVideos />}
          {tab === 'profile' && <StudentProfile user={user} onSignOut={onSignOut} />}
        </div>

        <nav className="bottom-nav" aria-label="主导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = tab === item.id;
            return (
              <button type="button" key={item.id} className={active ? 'is-active' : ''} onClick={() => openTab(item.id)}>
                <Icon size={27} weight={active ? 'fill' : 'regular'} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>
    </main>
  );
}
