import { useCallback, useEffect, useRef, useState } from 'react';
import { BookOpen, Coins, House, UserCircle, UsersThree } from '@phosphor-icons/react';
import { studentDashboardApi } from '../api';
import type { Dictionary, StudentDashboard, User } from '../types';
import { StudentClassrooms } from './StudentClassrooms';
import { StudentDashboardHome } from './StudentDashboardHome';
import { StudentLibrary } from './StudentLibrary';
import { StudentPoints } from './StudentPoints';
import { StudentProfile } from './StudentProfile';
import { StudentStudySession } from './StudentStudySession';
import './student-workspace.css';

type StudentTab = 'home' | 'study' | 'points' | 'classrooms' | 'profile' | 'library';

interface StudentWorkspaceProps {
  user: User;
  dictionaries: Dictionary[];
  onSignOut: () => void;
}

const navItems = [
  { id: 'home' as const, label: '首页', icon: House },
  { id: 'study' as const, label: '学习', icon: BookOpen },
  { id: 'points' as const, label: '积分', icon: Coins },
  { id: 'classrooms' as const, label: '班级', icon: UsersThree },
  { id: 'profile' as const, label: '我的', icon: UserCircle },
];

export function StudentWorkspace({ user, dictionaries, onSignOut }: StudentWorkspaceProps) {
  const [tab, setTab] = useState<StudentTab>('home');
  const [dashboard, setDashboard] = useState<StudentDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [libraryDictionaryId, setLibraryDictionaryId] = useState<number | null>(null);
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
  const currentPageLabel = tab === 'library' ? '词库' : navItems.find((item) => item.id === tab)?.label;

  return (
    <main className="prototype-shell">
      <div className="phone-frame" aria-label={`学生工作台，当前页面：${currentPageLabel}`}>
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
          {tab === 'library' && <StudentLibrary dictionaries={dictionaries} initialDictionaryId={libraryDictionaryId} />}
          {tab === 'points' && <StudentPoints />}
          {tab === 'classrooms' && (
            <StudentClassrooms
              onOpenDictionary={(dictionaryId) => {
                setLibraryDictionaryId(dictionaryId);
                setTab('library');
              }}
              onOpenStudyPlan={() => {
                setTab('study');
                void loadDashboard();
              }}
            />
          )}
          {tab === 'profile' && <StudentProfile user={user} onSignOut={onSignOut} />}
        </div>

        <nav className="bottom-nav" aria-label="主导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = tab === item.id;
            return (
              <button
                type="button"
                key={item.id}
                className={active ? 'is-active' : ''}
                aria-current={active ? 'page' : undefined}
                onClick={() => openTab(item.id)}
              >
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
