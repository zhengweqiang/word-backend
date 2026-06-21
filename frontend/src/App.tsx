import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  aiConfigApi,
  authApi,
  booksImportApi,
  classroomApi,
  clearStoredLoginQuote,
  clearStoredToken,
  dictionaryApi,
  dictionaryWordApi,
  examApi,
  getStoredLoginQuote,
  metaWordApi,
  setUnauthorizedHandler,
  storeLoginQuote,
  storeToken,
  studentApi,
  teacherApi,
  userApi,
} from './api';
import type {
  BooksImportBatchFile,
  BooksImportConflict,
  BooksImportJob,
  Classroom,
  Dictionary,
  Exam,
  ExamHistoryItem,
  ExamSubmissionResult,
  FamousQuote,
  MetaWord,
  User,
} from './types';
import { AddWordListModal } from './components/AddWordListModal';
import { AiConfigManagementModal } from './components/AiConfigManagementModal';
import { CreateDictionaryModal } from './components/CreateDictionaryModal';
import { CreateExamModal } from './components/CreateExamModal';
import { CsvImportModal } from './components/CsvImportModal';
import { AssignDictionaryModal } from './components/AssignDictionaryModal';
import { ClassManagementModal } from './components/ClassManagementModal';
import { DictionaryCard } from './components/DictionaryCard';
import { ExamHistoryModal } from './components/ExamHistoryModal';
import { ExamSessionModal } from './components/ExamSessionModal';
import { LoginScreen } from './components/LoginScreen';
import { SearchBox } from './components/SearchBox';
import { StudentStudyPlanModal } from './components/StudentStudyPlanModal';
import { StudyPlanManagementModal } from './components/StudyPlanManagementModal';
import { UserManagementModal } from './components/UserManagementModal';
import { WordDetail } from './components/WordDetail';
import { WordList } from './components/WordList';
import { postLoginDestination } from './auth/routing';
import { StudentWorkspace } from './student/StudentWorkspace';
import './App.css';

type MobilePanel = 'library' | 'words';

type ImportConflictDraft = {
  finalWord: string;
  finalDefinition: string;
  finalDifficulty: string;
  comment: string;
};

const FALLBACK_LOGIN_QUOTE: FamousQuote = {
  text: 'Learning never exhausts the mind.',
  translation: '学习从不会使头脑疲惫。',
  author: 'Leonardo da Vinci',
};

function redirectTo(destination: string) {
  if (typeof window === 'undefined') {
    return;
  }

  window.location.replace(destination);
}

function App() {
  const [authChecking, setAuthChecking] = useState(true);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [loginQuote, setLoginQuote] = useState<FamousQuote | null>(() => getStoredLoginQuote());

  const [dictionaries, setDictionaries] = useState<Dictionary[]>([]);
  const [availableStudents, setAvailableStudents] = useState<User[]>([]);
  const [availableClassrooms, setAvailableClassrooms] = useState<Classroom[]>([]);
  const [selectedDictionary, setSelectedDictionary] = useState<Dictionary | null>(null);
  const [metaWords, setMetaWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [loading, setLoading] = useState(false);
  const [dictSearchQuery, setDictSearchQuery] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [dictPage, setDictPage] = useState(1);
  const [wordPage, setWordPage] = useState(1);
  const [totalWords, setTotalWords] = useState(0);
  const [wordRefreshKey, setWordRefreshKey] = useState(0);
  const [isCompact, setIsCompact] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<MobilePanel>('library');
  const [showMobileDetail, setShowMobileDetail] = useState(false);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showAddWordListModal, setShowAddWordListModal] = useState(false);
  const [showCsvImportModal, setShowCsvImportModal] = useState(false);
  const [showExamSetupModal, setShowExamSetupModal] = useState(false);
  const [showExamHistoryModal, setShowExamHistoryModal] = useState(false);
  const [showUserManagementModal, setShowUserManagementModal] = useState(false);
  const [showClassManagementModal, setShowClassManagementModal] = useState(false);
  const [showAssignDictionaryModal, setShowAssignDictionaryModal] = useState(false);
  const [showStudyPlanModal, setShowStudyPlanModal] = useState(false);
  const [showAiConfigModal, setShowAiConfigModal] = useState(false);
  const [dictionaryForAdd, setDictionaryForAdd] = useState<Dictionary | null>(null);
  const [dictionaryForCsvImport, setDictionaryForCsvImport] = useState<Dictionary | null>(null);
  const [dictionaryForAssignment, setDictionaryForAssignment] = useState<Dictionary | null>(null);
  const [booksImportJob, setBooksImportJob] = useState<BooksImportJob | null>(null);
  const [booksImportFiles, setBooksImportFiles] = useState<BooksImportBatchFile[]>([]);
  const [booksImportConflicts, setBooksImportConflicts] = useState<BooksImportConflict[]>([]);
  const [conflictDrafts, setConflictDrafts] = useState<Record<number, ImportConflictDraft>>({});
  const [importFeedback, setImportFeedback] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  const [examLoading, setExamLoading] = useState(false);
  const [examError, setExamError] = useState<string | null>(null);
  const [examHistory, setExamHistory] = useState<ExamHistoryItem[]>([]);
  const [activeExam, setActiveExam] = useState<Exam | null>(null);
  const [examAnswers, setExamAnswers] = useState<Record<number, string>>({});
  const [examResult, setExamResult] = useState<ExamSubmissionResult | null>(null);

  const DICT_PAGE_SIZE = 6;
  const WORD_PAGE_SIZE = 10;

  const isAdmin = currentUser?.role === 'ADMIN';
  const isTeacher = currentUser?.role === 'TEACHER';
  const isStudent = currentUser?.role === 'STUDENT';
  const canManageWorkspace = isAdmin || isTeacher;
  const canImportSystemDictionaries = isAdmin;
  const canCreateExam = (isAdmin || isTeacher) && availableStudents.length > 0;

  const resetWorkspace = useCallback(() => {
    setDictionaries([]);
    setAvailableStudents([]);
    setAvailableClassrooms([]);
    setSelectedDictionary(null);
    setMetaWords([]);
    setSelectedWord(null);
    setSearchKeyword('');
    setIsSearching(false);
    setDictSearchQuery('');
    setDictPage(1);
    setWordPage(1);
    setTotalWords(0);
    setWordRefreshKey(0);
    setSidebarCollapsed(false);
    setMobilePanel('library');
    setShowMobileDetail(false);
    setShowCreateModal(false);
    setShowAddWordListModal(false);
    setShowCsvImportModal(false);
    setShowExamSetupModal(false);
    setShowExamHistoryModal(false);
    setShowUserManagementModal(false);
    setShowClassManagementModal(false);
    setShowAssignDictionaryModal(false);
    setShowStudyPlanModal(false);
    setShowAiConfigModal(false);
    setDictionaryForAdd(null);
    setDictionaryForCsvImport(null);
    setDictionaryForAssignment(null);
    setBooksImportJob(null);
    setBooksImportFiles([]);
    setBooksImportConflicts([]);
    setConflictDrafts({});
    setImportFeedback(null);
    setExamLoading(false);
    setExamError(null);
    setExamHistory([]);
    setActiveExam(null);
    setExamAnswers({});
    setExamResult(null);
  }, []);

  const performLocalSignOut = useCallback(() => {
    clearStoredLoginQuote();
    clearStoredToken();
    setLoginQuote(null);
    setCurrentUser(null);
    setAuthError(null);
    resetWorkspace();
  }, [resetWorkspace]);

  const handleSignOut = useCallback(() => {
    performLocalSignOut();
    void authApi.logout().catch(() => undefined);
  }, [performLocalSignOut]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      performLocalSignOut();
      setAuthError('登录状态已失效，请重新登录。');
    });

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [performLocalSignOut]);

  useEffect(() => {
    let mounted = true;

    const bootstrap = async () => {
      try {
        const user = await authApi.me();
        if (mounted) {
          const destination = postLoginDestination(user.role);
          if (destination !== '/') {
            redirectTo(destination);
            return;
          }
          setCurrentUser(user);
          setAuthError(null);
        }
      } catch {
        if (mounted) {
          clearStoredLoginQuote();
          clearStoredToken();
          setLoginQuote(null);
          setCurrentUser(null);
        }
      } finally {
        if (mounted) {
          setAuthChecking(false);
        }
      }
    };

    bootstrap();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (authChecking || currentUser) {
      return undefined;
    }

    let mounted = true;

    const loadLoginQuote = async () => {
      try {
        const quote = await authApi.getLoginQuote();
        if (mounted) {
          storeLoginQuote(quote);
          setLoginQuote(quote);
        }
      } catch {
        if (mounted) {
          setLoginQuote((current) => current ?? FALLBACK_LOGIN_QUOTE);
        }
      }
    };

    void loadLoginQuote();

    return () => {
      mounted = false;
    };
  }, [authChecking, currentUser]);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined;
    }

    const mediaQuery = window.matchMedia('(max-width: 900px)');
    const updateCompactState = () => setIsCompact(mediaQuery.matches);
    updateCompactState();
    mediaQuery.addEventListener('change', updateCompactState);

    return () => mediaQuery.removeEventListener('change', updateCompactState);
  }, []);

  const loadAvailableStudents = useCallback(async () => {
    if (!currentUser) {
      return;
    }

    try {
      const students = isAdmin
        ? await userApi.getStudents()
        : isTeacher
          ? await teacherApi.getMyStudents()
          : [];
      setAvailableStudents(students);
    } catch (error) {
      console.error('Failed to load students:', error);
    }
  }, [currentUser, isAdmin, isTeacher]);

  const loadAvailableClassrooms = useCallback(async () => {
    if (!currentUser || !canManageWorkspace) {
      setAvailableClassrooms([]);
      return;
    }

    try {
      const classrooms = await classroomApi.getAll();
      setAvailableClassrooms(classrooms);
    } catch (error) {
      console.error('Failed to load classrooms:', error);
    }
  }, [canManageWorkspace, currentUser]);

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    loadAvailableStudents();
    loadAvailableClassrooms();
  }, [currentUser, loadAvailableClassrooms, loadAvailableStudents]);

  const loadDictionaries = useCallback(async () => {
    if (!currentUser) {
      return;
    }

    setLoading(true);
    try {
      const nextDictionaries = isStudent
        ? await studentApi.getMyDictionaries()
        : await dictionaryApi.getAll();
      setDictionaries(nextDictionaries);
    } catch (error) {
      console.error('Failed to load dictionaries:', error);
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '加载辞书失败',
      });
    } finally {
      setLoading(false);
    }
  }, [currentUser, isStudent]);

  useEffect(() => {
    if (!currentUser) {
      return;
    }
    loadDictionaries();
  }, [currentUser, loadDictionaries]);

  const activeBooksImportStatuses = useMemo<BooksImportJob['status'][]>(() => [
    'PENDING',
    'SCANNING',
    'STAGING',
    'AUTO_MERGING',
    'PUBLISHING',
  ], []);

  const loadBatchFiles = useCallback(async (batchId: string) => {
    try {
      const files = await booksImportApi.getBatchFiles(batchId);
      setBooksImportFiles(files);
    } catch (error) {
      console.error('Failed to load books import files:', error);
    }
  }, []);

  const loadBatchConflicts = useCallback(async (batchId: string) => {
    try {
      const conflicts = await booksImportApi.getConflicts(batchId, false);
      setBooksImportConflicts(conflicts);
      setConflictDrafts((current) => {
        const nextDrafts = { ...current };
        conflicts.forEach((conflict) => {
          if (nextDrafts[conflict.id]) {
            return;
          }
          const importedPayload = conflict.importedPayload ?? {};
          nextDrafts[conflict.id] = {
            finalWord: typeof importedPayload.word === 'string' ? importedPayload.word : conflict.displayWord,
            finalDefinition: typeof importedPayload.definition === 'string' ? importedPayload.definition : '',
            finalDifficulty: typeof importedPayload.difficulty === 'number' ? String(importedPayload.difficulty) : '',
            comment: '',
          };
        });
        return nextDrafts;
      });
    } catch (error) {
      console.error('Failed to load books import conflicts:', error);
      setBooksImportConflicts([]);
    }
  }, []);

  useEffect(() => {
    if (!currentUser || !canImportSystemDictionaries) {
      setBooksImportJob(null);
      setBooksImportFiles([]);
      setBooksImportConflicts([]);
      return;
    }

    let mounted = true;

    const loadLatestImportJob = async () => {
      try {
        const latestJob = await booksImportApi.getLatestBatch();
        if (mounted) {
          setBooksImportJob(latestJob);
          void loadBatchFiles(latestJob.jobId);
          if (latestJob.status === 'WAITING_REVIEW' || latestJob.status === 'READY_TO_PUBLISH' || latestJob.status === 'SUCCEEDED') {
            void loadBatchConflicts(latestJob.jobId);
          } else {
            setBooksImportConflicts([]);
          }
        }
      } catch {
        if (mounted) {
          setBooksImportJob(null);
          setBooksImportFiles([]);
          setBooksImportConflicts([]);
        }
      }
    };

    void loadLatestImportJob();

    return () => {
      mounted = false;
    };
  }, [canImportSystemDictionaries, currentUser, loadBatchConflicts, loadBatchFiles]);

  useEffect(() => {
    if (!booksImportJob || !activeBooksImportStatuses.includes(booksImportJob.status)) {
      return undefined;
    }

    let cancelled = false;

    const pollImportJob = async () => {
      try {
        const [nextJob, files] = await Promise.all([
          booksImportApi.getBatch(booksImportJob.jobId),
          booksImportApi.getBatchFiles(booksImportJob.jobId),
        ]);
        if (cancelled) {
          return;
        }

        setBooksImportJob(nextJob);
        setBooksImportFiles(files);

        if (nextJob.status === 'SUCCEEDED') {
          await loadDictionaries();
          const conflicts = await booksImportApi.getConflicts(nextJob.jobId, false).catch(() => []);
          if (!cancelled) {
            setBooksImportConflicts(conflicts);
            setImportFeedback({
              type: 'success',
              message: `发布完成，已替换 ${nextJob.importedDictionaryCount ?? 0} 本辞书，累计 ${nextJob.importedWordCount ?? 0} 行已落库。`,
            });
          }
          return;
        }

        if (nextJob.status === 'WAITING_REVIEW' || nextJob.status === 'READY_TO_PUBLISH') {
          const conflicts = await booksImportApi.getConflicts(nextJob.jobId, false).catch(() => []);
          if (!cancelled) {
            setBooksImportConflicts(conflicts);
          }
        }

        if (nextJob.status === 'FAILED') {
          setImportFeedback({
            type: 'error',
            message: nextJob.errorMessage || '导入失败，请查看任务状态。',
          });
        }
      } catch (error) {
        if (!cancelled) {
          setImportFeedback({
            type: 'error',
            message: error instanceof Error ? error.message : '刷新导入状态失败',
          });
        }
      }
    };

    void pollImportJob();
    const timerId = window.setInterval(() => {
      void pollImportJob();
    }, 1000);

    return () => {
      cancelled = true;
      window.clearInterval(timerId);
    };
  }, [activeBooksImportStatuses, booksImportJob, loadDictionaries]);

  useEffect(() => {
    if (!booksImportJob) {
      setBooksImportFiles([]);
      setBooksImportConflicts([]);
      return;
    }
    void loadBatchFiles(booksImportJob.jobId);
    if (booksImportJob.status === 'WAITING_REVIEW' || booksImportJob.status === 'READY_TO_PUBLISH' || booksImportJob.status === 'SUCCEEDED') {
      void loadBatchConflicts(booksImportJob.jobId);
      return;
    }
    setBooksImportConflicts([]);
  }, [booksImportJob?.jobId, booksImportJob?.status, loadBatchConflicts, loadBatchFiles]);

  useEffect(() => {
    if (!selectedDictionary && dictionaries.length > 0 && !isSearching) {
      setSelectedDictionary(dictionaries[0]);
    }
  }, [dictionaries, selectedDictionary, isSearching]);

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    let mounted = true;

    const loadWords = async () => {
      if (!selectedDictionary && !isSearching) {
        setMetaWords([]);
        setTotalWords(0);
        return;
      }

      setLoading(true);
      try {
        if (isSearching && searchKeyword.trim()) {
          const pageResult = await metaWordApi.search(searchKeyword.trim(), undefined, wordPage - 1, WORD_PAGE_SIZE);
          if (!mounted) {
            return;
          }
          setMetaWords(pageResult.content);
          setTotalWords(pageResult.totalElements);
          return;
        }

        if (!selectedDictionary) {
          return;
        }

        const pageResult = await dictionaryWordApi.getWordsByDictionary(selectedDictionary.id, wordPage, WORD_PAGE_SIZE);
        if (!mounted) {
          return;
        }

        setMetaWords(pageResult.content);
        setTotalWords(pageResult.totalElements);
      } catch (error) {
        if (mounted) {
          console.error('Failed to load words:', error);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    loadWords();

    return () => {
      mounted = false;
    };
  }, [currentUser, isSearching, searchKeyword, selectedDictionary, wordPage, wordRefreshKey]);

  useEffect(() => {
    if (!isCompact) {
      setShowMobileDetail(false);
      return;
    }

    if (selectedWord) {
      setShowMobileDetail(true);
    }
  }, [isCompact, selectedWord]);

  const filteredDictionaries = useMemo(
    () => dictionaries.filter((dictionary) =>
      dictionary.name.toLowerCase().includes(dictSearchQuery.toLowerCase()),
    ),
    [dictSearchQuery, dictionaries],
  );

  const paginatedDictionaries = useMemo(
    () => filteredDictionaries.slice((dictPage - 1) * DICT_PAGE_SIZE, dictPage * DICT_PAGE_SIZE),
    [dictPage, filteredDictionaries],
  );

  const totalDictPages = Math.max(1, Math.ceil(filteredDictionaries.length / DICT_PAGE_SIZE));
  const totalWordPages = Math.max(1, Math.ceil(totalWords / WORD_PAGE_SIZE));
  const workspaceLabel = isSearching
    ? '全库搜索'
    : selectedDictionary?.name || '词汇总览';
  const workspaceMeta = isSearching
    ? searchKeyword
      ? `关键词「${searchKeyword}」`
      : '输入关键词开始搜索'
    : selectedDictionary?.category || '选择一本辞书开始浏览';
  const activeWordCount = isSearching ? totalWords : selectedDictionary?.wordCount || totalWords;
  const displayedQuote = loginQuote ?? FALLBACK_LOGIN_QUOTE;
  const isImportingDictionaries = booksImportJob ? activeBooksImportStatuses.includes(booksImportJob.status) : false;
  const importProcessedFiles = booksImportJob?.processedFiles ?? 0;
  const importTotalFiles = booksImportJob?.totalFiles ?? 0;
  const importProgressPercent = importTotalFiles > 0
    ? Math.min(100, Math.round((importProcessedFiles / importTotalFiles) * 100))
    : booksImportJob && ['STAGED', 'WAITING_REVIEW', 'READY_TO_PUBLISH', 'PUBLISHING', 'SUCCEEDED'].includes(booksImportJob.status)
      ? 100
      : 0;
  const importStatusLabel = booksImportJob?.status === 'SUCCEEDED'
    ? '已发布'
    : booksImportJob?.status === 'FAILED'
      ? '失败'
      : booksImportJob?.status === 'PUBLISHING'
        ? '发布中'
        : booksImportJob?.status === 'READY_TO_PUBLISH'
          ? '可发布'
          : booksImportJob?.status === 'WAITING_REVIEW'
            ? '待处理冲突'
            : booksImportJob?.status === 'AUTO_MERGING'
              ? '自动合并中'
              : booksImportJob?.status === 'STAGED'
                ? '已入暂存区'
                : booksImportJob?.status === 'STAGING'
                  ? '导入中'
                  : booksImportJob?.status === 'SCANNING'
                    ? '扫描文件中'
                    : booksImportJob?.status === 'PENDING'
                      ? '排队中'
                      : booksImportJob?.status === 'DISCARDED'
                        ? '已废弃'
                        : booksImportJob?.status === 'CANCELLED'
                          ? '已取消'
                          : null;
  const currentImportFileLabel = booksImportJob?.currentFile
    || (booksImportJob?.status === 'SUCCEEDED'
      ? '本次发布已完成'
      : booksImportJob?.status === 'FAILED'
        ? '任务已终止'
        : booksImportJob?.status === 'READY_TO_PUBLISH'
          ? '等待人工发布'
          : booksImportJob?.status === 'WAITING_REVIEW'
            ? '等待人工处理冲突'
            : booksImportJob?.status === 'STAGED'
              ? '全部文件已完成暂存'
        : '等待开始');
  const canStartAutoMerge = booksImportJob?.status === 'STAGED' || booksImportJob?.status === 'WAITING_REVIEW' || booksImportJob?.status === 'READY_TO_PUBLISH';
  const canPublishBatch = booksImportJob?.status === 'READY_TO_PUBLISH';
  const canDiscardBatch = Boolean(booksImportJob && !activeBooksImportStatuses.includes(booksImportJob.status) && booksImportJob.status !== 'DISCARDED');
  const readImportPayloadText = (payload: Record<string, unknown> | null | undefined, key: string) => {
    const value = payload?.[key];
    return typeof value === 'string' ? value : '';
  };
  const readImportPayloadNumber = (payload: Record<string, unknown> | null | undefined, key: string) => {
    const value = payload?.[key];
    return typeof value === 'number' ? value : null;
  };

  const canManageDictionary = useCallback((dictionary: Dictionary) => {
    if (!currentUser) {
      return false;
    }
    if (isAdmin) {
      return true;
    }
    if (isTeacher) {
      return dictionary.creationType === 'USER_CREATED';
    }
    return false;
  }, [currentUser, isAdmin, isTeacher]);

  const canManageSelectedDictionary = selectedDictionary ? canManageDictionary(selectedDictionary) : false;

  const handleLogin = useCallback(async (username: string, password: string) => {
    setAuthLoading(true);
    setAuthError(null);
    try {
      const response = await authApi.login(username, password);
      storeToken(response.token);
      storeLoginQuote(response.quote);
      setLoginQuote(response.quote);
      const destination = postLoginDestination(response.user.role);
      if (destination !== '/') {
        redirectTo(destination);
        return;
      }
      setCurrentUser(response.user);
      resetWorkspace();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : '登录失败');
    } finally {
      setAuthLoading(false);
      setAuthChecking(false);
    }
  }, [resetWorkspace]);

  const handleDictionaryCreated = useCallback((newDictionary: Dictionary) => {
    setDictionaries((prev) => [newDictionary, ...prev]);
    setSelectedDictionary(newDictionary);
    setSelectedWord(null);
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
    setMobilePanel('words');
    setShowMobileDetail(false);
    setShowCreateModal(false);
  }, []);

  const handleDeleteDictionary = useCallback(async (id: number) => {
    if (!window.confirm('确定要删除这个辞书吗？此操作无法撤销。')) {
      return;
    }

    try {
      await dictionaryApi.deleteById(id);
      setDictionaries((prev) => prev.filter((dictionary) => dictionary.id !== id));
      if (selectedDictionary?.id === id) {
        setSelectedDictionary(null);
        setSelectedWord(null);
        setMetaWords([]);
      }
    } catch (error) {
      console.error('Failed to delete dictionary:', error);
      alert(error instanceof Error ? error.message : '删除辞书失败，请重试。');
    }
  }, [selectedDictionary]);

  const handleImportDictionaries = useCallback(async () => {
    if (!canImportSystemDictionaries || isImportingDictionaries) {
      return;
    }

    setImportFeedback(null);
    try {
      const job = await booksImportApi.createBatch();
      setBooksImportJob(job);
      setBooksImportFiles([]);
      setBooksImportConflicts([]);
      setImportFeedback({
        type: 'info',
        message: '导入批次已创建，文件会先进入 staging。',
      });
    } catch (error) {
      if (error instanceof Error && error.message.includes('already running')) {
        try {
          const latestJob = await booksImportApi.getLatestBatch();
          setBooksImportJob(latestJob);
          await loadBatchFiles(latestJob.jobId);
          setImportFeedback({
            type: 'info',
            message: '已有导入批次正在执行，已切换到当前进度。',
          });
          return;
        } catch {
          // Ignore fallback lookup errors and keep the original conflict message below.
        }
      }
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '导入失败',
      });
    }
  }, [canImportSystemDictionaries, isImportingDictionaries, loadBatchFiles]);

  const handleStartAutoMerge = useCallback(async () => {
    if (!booksImportJob) {
      return;
    }
    try {
      const nextJob = await booksImportApi.startAutoMerge(booksImportJob.jobId);
      setBooksImportJob(nextJob);
      setImportFeedback({
        type: 'info',
        message: '自动合并已启动，系统正在生成候选和冲突清单。',
      });
    } catch (error) {
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '启动自动合并失败',
      });
    }
  }, [booksImportJob]);

  const handlePublishBatch = useCallback(async () => {
    if (!booksImportJob) {
      return;
    }
    try {
      const nextJob = await booksImportApi.publish(booksImportJob.jobId);
      setBooksImportJob(nextJob);
      setImportFeedback({
        type: 'info',
        message: '发布已启动，系统正在替换正式词条。',
      });
    } catch (error) {
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '发布失败',
      });
    }
  }, [booksImportJob]);

  const handleDiscardBatch = useCallback(async () => {
    if (!booksImportJob || !window.confirm('确定要废弃当前导入批次吗？staging 和冲突数据会被清空。')) {
      return;
    }
    try {
      const nextJob = await booksImportApi.discard(booksImportJob.jobId);
      setBooksImportJob(nextJob);
      setBooksImportFiles([]);
      setBooksImportConflicts([]);
      setImportFeedback({
        type: 'info',
        message: '导入批次已废弃。',
      });
    } catch (error) {
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '废弃批次失败',
      });
    }
  }, [booksImportJob]);

  const handleConflictDraftChange = useCallback((conflictId: number, patch: Partial<ImportConflictDraft>) => {
    setConflictDrafts((current) => ({
      ...current,
      [conflictId]: {
        ...(current[conflictId] ?? {
          finalWord: '',
          finalDefinition: '',
          finalDifficulty: '',
          comment: '',
        }),
        ...patch,
      },
    }));
  }, []);

  const handleResolveConflict = useCallback(async (
    conflict: BooksImportConflict,
    resolution: 'KEEP_EXISTING' | 'USE_IMPORTED' | 'MANUAL' | 'IGNORE',
  ) => {
    if (!booksImportJob) {
      return;
    }
    const draft = conflictDrafts[conflict.id];
    try {
      await booksImportApi.resolveConflict(booksImportJob.jobId, conflict.id, {
        resolution,
        finalWord: resolution === 'MANUAL' ? draft?.finalWord?.trim() || undefined : undefined,
        finalDefinition: resolution === 'MANUAL' ? draft?.finalDefinition?.trim() || undefined : undefined,
        finalDifficulty: resolution === 'MANUAL' && draft?.finalDifficulty
          ? Number(draft.finalDifficulty)
          : undefined,
        comment: draft?.comment?.trim() || undefined,
      });
      const [nextJob, conflicts] = await Promise.all([
        booksImportApi.getBatch(booksImportJob.jobId),
        booksImportApi.getConflicts(booksImportJob.jobId, false),
      ]);
      setBooksImportJob(nextJob);
      setBooksImportConflicts(conflicts);
      setImportFeedback({
        type: 'success',
        message: `冲突 ${conflict.displayWord} 已处理。`,
      });
    } catch (error) {
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '处理冲突失败',
      });
    }
  }, [booksImportJob, conflictDrafts]);

  const handleSearchClear = useCallback(() => {
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
    setSelectedWord(null);
    setMobilePanel('words');
  }, []);

  const handleSearchQueryChange = useCallback((query: string) => {
    setSearchKeyword(query);
    setWordPage(1);
    setSelectedWord(null);
    setMobilePanel('words');
    setIsSearching(Boolean(query.trim()));
  }, []);

  const handleSelectDictionary = useCallback((dictionary: Dictionary) => {
    setSelectedDictionary(dictionary);
    setSelectedWord(null);
    setWordPage(1);
    setMobilePanel('words');
    setShowMobileDetail(false);
    setIsSearching(false);
    setSearchKeyword('');
  }, []);

  const handleSelectWord = useCallback((word: MetaWord) => {
    setSelectedWord(word);
    if (isCompact) {
      setShowMobileDetail(true);
    }
  }, [isCompact]);

  const handleOpenExamHistory = useCallback(async () => {
    if (!selectedDictionary) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    setShowExamHistoryModal(true);
    try {
      const historyItems = await examApi.getHistory(selectedDictionary.id);
      setExamHistory(historyItems);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '加载考试历史失败');
    } finally {
      setExamLoading(false);
    }
  }, [selectedDictionary]);

  const handleOpenExamSetup = useCallback(() => {
    if (!canCreateExam || !selectedDictionary) {
      return;
    }
    setExamError(null);
    setShowExamSetupModal(true);
  }, [canCreateExam, selectedDictionary]);

  const handleOpenAssignDictionary = useCallback(() => {
    if (!selectedDictionary || !canManageWorkspace) {
      return;
    }

    setDictionaryForAssignment(selectedDictionary);
    setShowAssignDictionaryModal(true);
  }, [canManageWorkspace, selectedDictionary]);

  const handleStartExam = useCallback(async (questionCount: number, targetUserId: number) => {
    if (!selectedDictionary) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    try {
      const exam = await examApi.create(selectedDictionary.id, questionCount, targetUserId);
      setActiveExam(exam);
      setExamAnswers({});
      setExamResult(null);
      setShowExamSetupModal(false);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '生成考试失败');
    } finally {
      setExamLoading(false);
    }
  }, [selectedDictionary]);

  const handleSelectExamOption = useCallback((questionId: number, optionKey: string) => {
    setExamAnswers((prev) => ({
      ...prev,
      [questionId]: optionKey,
    }));
  }, []);

  const handleSubmitExam = useCallback(async () => {
    if (!activeExam) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    try {
      const answers = Object.entries(examAnswers).map(([questionId, selectedOption]) => ({
        questionId: Number(questionId),
        selectedOption,
      }));

      const result = await examApi.submit(activeExam.examId, answers);
      setExamResult(result);

      if (selectedDictionary) {
        const historyItems = await examApi.getHistory(selectedDictionary.id);
        setExamHistory(historyItems);
      }
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '提交考试失败');
    } finally {
      setExamLoading(false);
    }
  }, [activeExam, examAnswers, selectedDictionary]);

  const handleViewHistoryResult = useCallback(async (examId: number) => {
    setExamLoading(true);
    setExamError(null);
    try {
      const [exam, result] = await Promise.all([
        examApi.getById(examId),
        examApi.getResult(examId),
      ]);
      setActiveExam(exam);
      setExamResult(result);
      setExamAnswers({});
      setShowExamHistoryModal(false);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '加载考试详情失败');
    } finally {
      setExamLoading(false);
    }
  }, []);

  const handleCloseExam = useCallback(() => {
    setActiveExam(null);
    setExamAnswers({});
    setExamResult(null);
    setExamError(null);
  }, []);

  const handleAdminUsersChanged = useCallback((users: User[]) => {
    setAvailableStudents(users.filter((user) => user.role === 'STUDENT'));
  }, []);

  const handleClassroomsChanged = useCallback((classrooms: Classroom[]) => {
    setAvailableClassrooms(classrooms);
  }, []);

  const handleAiConfigsChanged = useCallback(async () => {
    try {
      await aiConfigApi.list();
    } catch {
      // Ignore refresh failures here; the modal handles its own feedback.
    }
  }, []);

  if (authChecking) {
    return (
      <div className="auth-loading">
        <span className="sidebar__spinner"></span>
        <p>正在恢复登录状态...</p>
      </div>
    );
  }

  if (!currentUser) {
    return (
      <LoginScreen
        loading={authLoading}
        error={authError}
        quote={displayedQuote}
        onSubmit={handleLogin}
      />
    );
  }

  if (isStudent) {
    return (
      <StudentWorkspace
        user={currentUser}
        dictionaries={dictionaries}
        onSignOut={handleSignOut}
      />
    );
  }

  const sidebarContent = (
    <div className="sidebar__section">
      <div className="sidebar__header">
        <div className="sidebar__header-copy">
          <p className="sidebar__eyebrow">Library</p>
          <h2 className="sidebar__title">辞书书架</h2>
          <p className="sidebar__summary">
            {filteredDictionaries.length} / {dictionaries.length} 本可用辞书
          </p>
        </div>

        {canManageWorkspace && (
          <div className="sidebar__actions sidebar__actions--stacked">
            {canImportSystemDictionaries && (
              <button
                className="exam-history-btn"
                onClick={handleImportDictionaries}
                disabled={isImportingDictionaries}
              >
                {isImportingDictionaries
                  ? `导入中 ${importProcessedFiles}/${importTotalFiles || '?'}`
                  : '导入 books'}
              </button>
            )}
            <button className="add-dictionary-btn" onClick={() => setShowCreateModal(true)}>
              新建辞书
            </button>
          </div>
        )}
      </div>

      <div className="sidebar__toolbar">
        <div className="sidebar__search">
          <input
            type="text"
            className="sidebar__search-input"
            placeholder="筛选辞书名称"
            value={dictSearchQuery}
            onChange={(event) => {
              setDictSearchQuery(event.target.value);
              setDictPage(1);
            }}
          />
          {dictSearchQuery && (
            <button className="sidebar__search-clear" onClick={() => setDictSearchQuery('')}>
              ×
            </button>
          )}
        </div>
        <p className="sidebar__hint">
          {isStudent
            ? '这里展示分配给你的学习资源。'
            : '选择一本辞书后，右侧会同步单词、考试与详情工作区。'}
        </p>
      </div>

      {booksImportJob && (
        <div className={`sidebar__import-status sidebar__import-status--${booksImportJob.status.toLowerCase()}`}>
          <div className="sidebar__import-status-head">
            <div>
              <p className="sidebar__import-status-eyebrow">Books Import</p>
              <strong className="sidebar__import-status-title">{importStatusLabel}</strong>
            </div>
            <span className="sidebar__import-status-badge">
              {importProcessedFiles}/{importTotalFiles || '?'}
            </span>
          </div>

          <div className="sidebar__import-progress-track">
            <div
              className="sidebar__import-progress-bar"
              style={{ width: `${importProgressPercent}%` }}
            />
          </div>

          <div className="sidebar__import-status-grid">
            <span>当前文件</span>
            <strong title={booksImportJob.currentFile || undefined}>
              {currentImportFileLabel}
            </strong>
            <span>总行数</span>
            <strong>{booksImportJob.totalRows ?? 0}</strong>
            <span>失败文件</span>
            <strong>{booksImportJob.failedFiles ?? 0}</strong>
            <span>已暂存行数</span>
            <strong>{booksImportJob.successRows ?? booksImportJob.importedWordCount ?? 0}</strong>
            <span>候选词数</span>
            <strong>{booksImportJob.candidateCount ?? 0}</strong>
            <span>未处理冲突</span>
            <strong>{booksImportJob.conflictCount ?? 0}</strong>
            <span>涉及辞书</span>
            <strong>{booksImportJob.importedDictionaryCount ?? 0}</strong>
          </div>

          <div className="sidebar__import-actions">
            <button
              className="sidebar__import-action"
              onClick={handleStartAutoMerge}
              disabled={!canStartAutoMerge || isImportingDictionaries}
            >
              自动合并
            </button>
            <button
              className="sidebar__import-action"
              onClick={handlePublishBatch}
              disabled={!canPublishBatch || isImportingDictionaries}
            >
              发布
            </button>
            <button
              className="sidebar__import-action sidebar__import-action--ghost"
              onClick={handleDiscardBatch}
              disabled={!canDiscardBatch}
            >
              废弃
            </button>
          </div>

          {booksImportFiles.length > 0 && (
            <div className="sidebar__import-section">
              <div className="sidebar__import-section-head">
                <span>文件进度</span>
                <strong>{booksImportFiles.length}</strong>
              </div>
              <div className="sidebar__import-file-list">
                {booksImportFiles.map((file) => (
                  <div key={file.id} className={`sidebar__import-file sidebar__import-file--${file.status.toLowerCase()}`}>
                    <div>
                      <strong title={file.fileName}>{file.dictionaryName}</strong>
                      <p>{file.fileName}</p>
                    </div>
                    <div className="sidebar__import-file-meta">
                      <span>{file.status}</span>
                      <span>{file.successRows ?? 0}/{file.rowCount ?? 0}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {booksImportConflicts.length > 0 && (
            <div className="sidebar__import-section">
              <div className="sidebar__import-section-head">
                <span>待处理冲突</span>
                <strong>{booksImportConflicts.length}</strong>
              </div>
              <div className="sidebar__import-conflict-list">
                {booksImportConflicts.map((conflict) => {
                  const draft = conflictDrafts[conflict.id];
                  return (
                    <div key={conflict.id} className="sidebar__import-conflict">
                      <div className="sidebar__import-conflict-head">
                        <div>
                          <strong>{conflict.displayWord}</strong>
                          <p>{conflict.conflictType}</p>
                        </div>
                        <span>{conflict.dictionaryNames.join(' / ')}</span>
                      </div>

                      <div className="sidebar__import-conflict-compare">
                        <div>
                          <span>正式库</span>
                          <strong>{readImportPayloadText(conflict.existingPayload, 'definition') || '暂无'}</strong>
                        </div>
                        <div>
                          <span>导入值</span>
                          <strong>{readImportPayloadText(conflict.importedPayload, 'definition') || '暂无'}</strong>
                        </div>
                      </div>

                      <div className="sidebar__import-conflict-form">
                        <input
                          type="text"
                          placeholder="最终单词"
                          value={draft?.finalWord ?? ''}
                          onChange={(event) => handleConflictDraftChange(conflict.id, { finalWord: event.target.value })}
                        />
                        <input
                          type="text"
                          placeholder="最终释义"
                          value={draft?.finalDefinition ?? ''}
                          onChange={(event) => handleConflictDraftChange(conflict.id, { finalDefinition: event.target.value })}
                        />
                        <input
                          type="number"
                          min={1}
                          max={5}
                          placeholder="难度"
                          value={draft?.finalDifficulty ?? ''}
                          onChange={(event) => handleConflictDraftChange(conflict.id, { finalDifficulty: event.target.value })}
                        />
                        <input
                          type="text"
                          placeholder="备注"
                          value={draft?.comment ?? ''}
                          onChange={(event) => handleConflictDraftChange(conflict.id, { comment: event.target.value })}
                        />
                      </div>

                      <div className="sidebar__import-conflict-actions">
                        <button type="button" onClick={() => handleResolveConflict(conflict, 'KEEP_EXISTING')}>
                          保留正式值
                        </button>
                        <button type="button" onClick={() => handleResolveConflict(conflict, 'USE_IMPORTED')}>
                          使用导入值
                        </button>
                        <button type="button" onClick={() => handleResolveConflict(conflict, 'MANUAL')}>
                          手工解决
                        </button>
                        <button type="button" onClick={() => handleResolveConflict(conflict, 'IGNORE')}>
                          忽略
                        </button>
                      </div>

                      {readImportPayloadNumber(conflict.existingPayload, 'difficulty') !== readImportPayloadNumber(conflict.importedPayload, 'difficulty') && (
                        <p className="sidebar__import-conflict-note">
                          难度差异: 正式库 {readImportPayloadNumber(conflict.existingPayload, 'difficulty') ?? '-'} /
                          导入值 {readImportPayloadNumber(conflict.importedPayload, 'difficulty') ?? '-'}
                        </p>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}

      {loading && dictionaries.length === 0 ? (
        <div className="sidebar__loading">
          <span className="sidebar__spinner"></span>
        </div>
      ) : filteredDictionaries.length === 0 ? (
        <div className="sidebar__empty">
          <p>还没有匹配的辞书</p>
        </div>
      ) : (
        <div className="sidebar__list">
          {paginatedDictionaries.map((dictionary, index) => (
            <div key={dictionary.id} style={{ animationDelay: `${index * 40}ms` }}>
              <DictionaryCard
                dictionary={dictionary}
                isSelected={selectedDictionary?.id === dictionary.id}
                onClick={() => handleSelectDictionary(dictionary)}
                onDelete={canManageDictionary(dictionary) ? () => handleDeleteDictionary(dictionary.id) : undefined}
                onAddJson={canManageDictionary(dictionary) ? () => {
                  setDictionaryForAdd(dictionary);
                  setShowAddWordListModal(true);
                } : undefined}
                onImportCsv={canManageDictionary(dictionary) ? () => {
                  setDictionaryForCsvImport(dictionary);
                  setShowCsvImportModal(true);
                } : undefined}
              />
            </div>
          ))}
        </div>
      )}

      {totalDictPages > 1 && (
        <div className="sidebar__pagination">
          <button
            className="sidebar__page-btn"
            disabled={dictPage === 1}
            onClick={() => setDictPage((prev) => prev - 1)}
          >
            ‹
          </button>
          <span className="sidebar__page-info">{dictPage} / {totalDictPages}</span>
          <button
            className="sidebar__page-btn"
            disabled={dictPage === totalDictPages}
            onClick={() => setDictPage((prev) => prev + 1)}
          >
            ›
          </button>
        </div>
      )}
    </div>
  );

  const listPanel = (
    <div className="content__panel content__panel--list">
      <div className="panel__header">
        <div className="panel__header-main">
          <div>
            <p className="panel__eyebrow">{isSearching ? 'Search Results' : 'Word Shelf'}</p>
            <h2 className="panel__title">{isSearching ? '搜索结果' : selectedDictionary?.name || '单词列表'}</h2>
          </div>
          {activeWordCount > 0 && <span className="panel__count">{activeWordCount} 个词条</span>}
        </div>

        <div className="panel__header-actions">
          {isCompact && selectedWord && (
            <button className="exam-history-btn" onClick={() => setShowMobileDetail(true)}>
              查看详情
            </button>
          )}
          {selectedDictionary && (
            <>
              {canManageSelectedDictionary && (
                <>
                  <button
                    className="exam-history-btn"
                    onClick={() => {
                      setDictionaryForAdd(selectedDictionary);
                      setShowAddWordListModal(true);
                    }}
                  >
                    手动录词
                  </button>
                  <button
                    className="exam-history-btn"
                    onClick={() => {
                      setDictionaryForCsvImport(selectedDictionary);
                      setShowCsvImportModal(true);
                    }}
                  >
                    批量导入
                  </button>
                </>
              )}
              {canManageWorkspace && (
                <button className="exam-history-btn" onClick={handleOpenAssignDictionary}>
                  分配班级/学生
                </button>
              )}
              <button className="exam-history-btn" onClick={handleOpenExamHistory}>
                考试历史
              </button>
              {canCreateExam && (
                <button className="exam-trigger-btn" onClick={handleOpenExamSetup}>
                  开始考试
                </button>
              )}
            </>
          )}
        </div>
      </div>

      <div className="panel__list-wrapper">
        <WordList
          words={metaWords}
          selectedWord={selectedWord}
          onSelectWord={handleSelectWord}
          loading={loading && metaWords.length === 0}
        />
      </div>

      {totalWordPages > 1 && (
        <div className="content__pagination">
          <button
            className="content__page-btn"
            disabled={wordPage === 1 || loading}
            onClick={() => setWordPage((prev) => prev - 1)}
          >
            ‹
          </button>
          <span className="content__page-info">{wordPage} / {totalWordPages}</span>
          <button
            className="content__page-btn"
            disabled={wordPage === totalWordPages || loading}
            onClick={() => setWordPage((prev) => prev + 1)}
          >
            ›
          </button>
        </div>
      )}
    </div>
  );

  const detailPanel = (
    <div className="content__panel content__panel--detail">
      <div className="panel__header">
        <div>
          <p className="panel__eyebrow">Word Detail</p>
          <h2 className="panel__title">单词详情</h2>
        </div>
        <span className="panel__count">{selectedWord ? '已选词条' : '等待选择'}</span>
      </div>
      <WordDetail word={selectedWord} />
    </div>
  );

  return (
    <div className="app">
      <header className="app__header">
        <div className="app__header-content">
          <div className="app__masthead">
            <div>
              <p className="app__eyebrow">Word Atelier</p>
              <h1 className="app__title app__title--quote">"{displayedQuote.text}"</h1>
              <p className="app__quote-translation">{displayedQuote.translation}</p>
              <p className="app__quote-author">- {displayedQuote.author}</p>
            </div>
            <div className="app__masthead-meta">
              <span className="app__masthead-chip">{workspaceLabel}</span>
              <span className="app__masthead-chip">{currentUser.displayName} · {currentUser.role}</span>
              {canManageWorkspace && (
                <button
                  className="exam-history-btn"
                  onClick={() => setShowClassManagementModal(true)}
                >
                  班级管理
                </button>
              )}
              <button
                className="exam-history-btn"
                onClick={() => setShowStudyPlanModal(true)}
              >
                学习计划
              </button>
              <button
                className="exam-history-btn"
                onClick={() => setShowAiConfigModal(true)}
              >
                AI 配置
              </button>
              {isAdmin && (
                <button
                  className="exam-history-btn"
                  onClick={() => setShowUserManagementModal(true)}
                >
                  用户管理
                </button>
              )}
              <button className="app__logout" onClick={handleSignOut}>
                退出登录
              </button>
            </div>
          </div>

          <div className="app__hero">
            <div className="app__hero-copy">
              <p className="app__eyebrow">Workspace</p>
              <p className="app__subtitle">
                管理员统筹资源，教师分配学习内容，学生只处理自己的词书与考试。所有请求都带着登录态进入后端。
              </p>
              <div className="app__hero-stats">
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">当前角色</span>
                  <strong className="app__hero-stat-value">{currentUser.role}</strong>
                </div>
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">辞书数量</span>
                  <strong className="app__hero-stat-value">{dictionaries.length}</strong>
                </div>
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">工作区状态</span>
                  <strong className="app__hero-stat-value">{workspaceMeta}</strong>
                </div>
              </div>
              {importFeedback && (
                <div className={`sidebar__import-feedback sidebar__import-feedback--${importFeedback.type}`}>
                  {importFeedback.message}
                </div>
              )}
            </div>

            <div className="app__hero-search">
              <div className="app__search-card">
                <div className="app__search-card-header">
                  <p className="app__search-label">快速搜索</p>
                  <span className="app__search-mode">{workspaceMeta}</span>
                </div>
                <div className="app__search">
                  <SearchBox
                    onLoading={setLoading}
                    onClear={handleSearchClear}
                    onSearchQueryChange={handleSearchQueryChange}
                    value={searchKeyword}
                  />
                </div>
                <p className="app__search-help">
                  搜索会切到词条工作区。移动端使用抽屉查看详情，桌面端保持双栏阅读。
                </p>
              </div>
            </div>
          </div>

          {isCompact && (
            <div className="app__mobile-switcher">
              <button
                className={`app__mobile-switch ${mobilePanel === 'library' ? 'app__mobile-switch--active' : ''}`}
                onClick={() => setMobilePanel('library')}
              >
                辞书
              </button>
              <button
                className={`app__mobile-switch ${mobilePanel === 'words' ? 'app__mobile-switch--active' : ''}`}
                onClick={() => setMobilePanel('words')}
              >
                词条
              </button>
              <button
                className={`app__mobile-switch ${showMobileDetail ? 'app__mobile-switch--active' : ''}`}
                onClick={() => selectedWord && setShowMobileDetail(true)}
                disabled={!selectedWord}
              >
                详情
              </button>
            </div>
          )}
        </div>
      </header>

      {!isCompact ? (
        <main className={`app__main ${sidebarCollapsed ? 'app__main--sidebar-collapsed' : ''}`}>
          <aside className={`app__sidebar ${sidebarCollapsed ? 'app__sidebar--collapsed' : ''}`}>
            <button
              className="sidebar__toggle"
              onClick={() => setSidebarCollapsed((prev) => !prev)}
              title={sidebarCollapsed ? '展开辞书列表' : '收起辞书列表'}
            >
              {sidebarCollapsed ? '▶' : '◀'}
            </button>
            {!sidebarCollapsed && sidebarContent}
          </aside>

          <section className="app__workspace">
            {listPanel}
            {detailPanel}
          </section>
        </main>
      ) : (
        <main className="app__main app__main--compact">
          {mobilePanel === 'library' ? (
            <section className="app__mobile-stage app__mobile-stage--library">{sidebarContent}</section>
          ) : (
            <section className="app__mobile-stage app__mobile-stage--words">{listPanel}</section>
          )}
        </main>
      )}

      {isCompact && selectedWord && (
        <div className={`detail-drawer ${showMobileDetail ? 'detail-drawer--open' : ''}`}>
          <button
            className={`detail-drawer__scrim ${showMobileDetail ? 'detail-drawer__scrim--open' : ''}`}
            aria-label="关闭单词详情"
            onClick={() => setShowMobileDetail(false)}
          />
          <aside className="detail-drawer__panel" aria-modal="true" role="dialog">
            <div className="detail-drawer__header">
              <div>
                <p className="panel__eyebrow">Focused Entry</p>
                <h2 className="panel__title">{selectedWord.word}</h2>
              </div>
              <button className="modal__close" onClick={() => setShowMobileDetail(false)}>
                ×
              </button>
            </div>
            <div className="detail-drawer__body">
              <WordDetail word={selectedWord} />
            </div>
          </aside>
        </div>
      )}

      <CreateDictionaryModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onDictionaryCreated={handleDictionaryCreated}
      />

      {dictionaryForAdd && (
        <AddWordListModal
          isOpen={showAddWordListModal}
          onClose={() => {
            setShowAddWordListModal(false);
            setDictionaryForAdd(null);
            loadDictionaries();
          }}
          dictionary={dictionaryForAdd}
          onSuccess={() => {
            loadDictionaries();
            setWordPage(1);
            setWordRefreshKey((previousValue) => previousValue + 1);
          }}
        />
      )}

      {dictionaryForCsvImport && (
        <CsvImportModal
          isOpen={showCsvImportModal}
          onClose={() => {
            setShowCsvImportModal(false);
            setDictionaryForCsvImport(null);
            loadDictionaries();
          }}
          dictionary={dictionaryForCsvImport}
          onSuccess={() => {
            loadDictionaries();
            setWordPage(1);
            setWordRefreshKey((previousValue) => previousValue + 1);
          }}
        />
      )}

      <CreateExamModal
        isOpen={showExamSetupModal}
        dictionary={selectedDictionary}
        availableStudents={availableStudents}
        loading={examLoading}
        error={examError}
        onClose={() => {
          setShowExamSetupModal(false);
          setExamError(null);
        }}
        onStart={handleStartExam}
      />

      <ExamHistoryModal
        isOpen={showExamHistoryModal}
        dictionary={selectedDictionary}
        historyItems={examHistory}
        loading={examLoading}
        error={examError}
        onClose={() => {
          setShowExamHistoryModal(false);
          setExamError(null);
        }}
        onViewResult={handleViewHistoryResult}
      />

      <ExamSessionModal
        isOpen={activeExam !== null}
        dictionary={selectedDictionary}
        exam={activeExam}
        selectedAnswers={examAnswers}
        result={examResult}
        loading={examLoading}
        error={examError}
        onClose={handleCloseExam}
        onSelectOption={handleSelectExamOption}
        onSubmit={handleSubmitExam}
      />

      {currentUser && isAdmin && (
        <UserManagementModal
          isOpen={showUserManagementModal}
          currentUser={currentUser}
          onClose={() => setShowUserManagementModal(false)}
          onUsersChanged={handleAdminUsersChanged}
        />
      )}

      {currentUser && canManageWorkspace && (
        <ClassManagementModal
          isOpen={showClassManagementModal}
          currentUser={currentUser}
          onClose={() => setShowClassManagementModal(false)}
          onClassroomsChanged={handleClassroomsChanged}
          onMembershipChanged={async () => {
            await loadAvailableStudents();
            await loadAvailableClassrooms();
          }}
        />
      )}

      <AssignDictionaryModal
        isOpen={showAssignDictionaryModal}
        dictionary={dictionaryForAssignment}
        availableStudents={availableStudents}
        availableClassrooms={availableClassrooms}
        actorRole={currentUser.role}
        onClose={() => {
          setShowAssignDictionaryModal(false);
          setDictionaryForAssignment(null);
        }}
      />

      {canManageWorkspace ? (
        <StudyPlanManagementModal
          isOpen={showStudyPlanModal}
          classrooms={availableClassrooms}
          onClose={() => setShowStudyPlanModal(false)}
        />
      ) : (
        <StudentStudyPlanModal
          isOpen={showStudyPlanModal}
          onClose={() => setShowStudyPlanModal(false)}
        />
      )}

      {(isTeacher || isStudent) && (
        <AiConfigManagementModal
          isOpen={showAiConfigModal}
          actorRole={currentUser.role as 'TEACHER' | 'STUDENT'}
          onClose={() => {
            setShowAiConfigModal(false);
            void handleAiConfigsChanged();
          }}
        />
      )}
    </div>
  );
}

export default App;
