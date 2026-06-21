import type {
  AiChatPayload,
  AiChatResponse,
  AiConfig,
  AiConfigStatus,
  AiConfigTestResponse,
  BooksImportBatchFile,
  BooksImportConflict,
  BooksImportJob,
  Classroom,
  CreateAiConfigPayload,
  CreateStudyPlanPayload,
  Dictionary,
  DictionaryWord,
  Exam,
  ExamAnswer,
  ExamHistoryItem,
  ExamSubmissionResult,
  FamousQuote,
  GenerateDictionaryWordWithAiPayload,
  GenerateDictionaryWordWithAiResponse,
  GenerateWordDetailsPayload,
  GenerateWordDetailsResponse,
  LoginResponse,
  MetaWord,
  MetaWordEntry,
  Page,
  RecordStudyPayload,
  GenerateReadingPayload,
  GenerateReadingResponse,
  StudentAttentionDailyStat,
  StudentDashboard,
  StudentDashboardRecordPayload,
  StudentStudyPlanSummary,
  StudyPlan,
  StudyPlanOverview,
  StudyPlanStudentAttention,
  StudyPlanStudentSummary,
  StudyTask,
  UpdateAiConfigPayload,
  User,
} from '../types';

export interface WordListProcessResult {
  message: string;
  dictionaryId: number;
  total: number;
  existed: number;
  created: number;
  added: number;
  failed: number;
}

const API_BASE = '/api';
const TOKEN_STORAGE_KEY = 'word_atelier_token';
const LOGIN_QUOTE_STORAGE_KEY = 'word_atelier_login_quote';

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export function getStoredToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function storeToken(token: string) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearStoredToken() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY);
}

export function getStoredLoginQuote(): FamousQuote | null {
  const raw = window.localStorage.getItem(LOGIN_QUOTE_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<FamousQuote>;
    if (
      typeof parsed.text === 'string'
      && typeof parsed.translation === 'string'
      && typeof parsed.author === 'string'
    ) {
      return {
        text: parsed.text,
        translation: parsed.translation,
        author: parsed.author,
      };
    }
  } catch {
    // Ignore invalid cached quotes and clear them below.
  }

  window.localStorage.removeItem(LOGIN_QUOTE_STORAGE_KEY);
  return null;
}

export function storeLoginQuote(quote: FamousQuote) {
  window.localStorage.setItem(LOGIN_QUOTE_STORAGE_KEY, JSON.stringify(quote));
}

export function clearStoredLoginQuote() {
  window.localStorage.removeItem(LOGIN_QUOTE_STORAGE_KEY);
}

interface FetchJsonOptions extends RequestInit {
  skipUnauthorizedHandler?: boolean;
}

async function fetchJson<T>(url: string, options?: FetchJsonOptions): Promise<T> {
  const isFormData = options?.body instanceof FormData;
  const headers = new Headers(options?.headers ?? {});
  const token = getStoredToken();
  const { skipUnauthorizedHandler = false, ...requestOptions } = options ?? {};

  if (!isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...requestOptions,
    credentials: requestOptions.credentials ?? 'same-origin',
    headers,
  });
  if (!response.ok) {
    let message = `API Error: ${response.status}`;

    try {
      const errorBody = await response.json();
      message = errorBody.message || errorBody.error || message;
    } catch {
      // Ignore non-JSON error bodies.
    }

    if (response.status === 401) {
      clearStoredToken();
      if (!skipUnauthorizedHandler) {
        unauthorizedHandler?.();
      }
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

export const authApi = {
  login: (username: string, password: string) => fetchJson<LoginResponse>(`${API_BASE}/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }),
  getLoginQuote: () => fetchJson<FamousQuote>(`${API_BASE}/auth/quote`, {
    skipUnauthorizedHandler: true,
  }),
  logout: () => fetchJson<void>(`${API_BASE}/auth/logout`, {
    method: 'POST',
    skipUnauthorizedHandler: true,
  }),
  me: () => fetchJson<User>(`${API_BASE}/auth/me`),
};

export const dictionaryApi = {
  getAll: (classroomIds?: number[]) => {
    const query = classroomIds && classroomIds.length > 0
      ? `?${classroomIds.map((classroomId) => `classroomIds=${classroomId}`).join('&')}`
      : '';
    return fetchJson<Dictionary[]>(`${API_BASE}/dictionaries${query}`);
  },
  getById: (id: number) => fetchJson<Dictionary>(`${API_BASE}/dictionaries/${id}`),
  getByCategory: (category: string) => fetchJson<Dictionary[]>(`${API_BASE}/dictionaries/category/${category}`),
  create: (dictionary: Omit<Dictionary, 'id'>) => fetchJson<Dictionary>(`${API_BASE}/dictionaries`, {
    method: 'POST',
    body: JSON.stringify(dictionary),
  }),
  importDictionaries: () => fetchJson<BooksImportJob>(`${API_BASE}/dictionaries/import`, { method: 'POST' }),
  getLatestImportJob: () => fetchJson<BooksImportJob>(`${API_BASE}/dictionaries/import/latest`),
  getImportJob: (jobId: string) => fetchJson<BooksImportJob>(`${API_BASE}/dictionaries/import/${jobId}`),
  deleteAll: () => fetch(`${API_BASE}/dictionaries`, { method: 'DELETE' }),
  deleteById: (id: number) => fetchJson<{ message: string; id: number }>(`${API_BASE}/dictionaries/${id}`, { method: 'DELETE' }),
  deleteUserCreated: () => fetchJson<{ message: string; deletedCount: number }>(`${API_BASE}/dictionaries/user-created`, { method: 'DELETE' }),
  assignStudents: (id: number, studentIds: number[]) => fetchJson<{ message: string; dictionaryId: number; assignedCount: number }>(
    `${API_BASE}/dictionaries/${id}/assign/students`,
    {
      method: 'POST',
      body: JSON.stringify({ studentIds }),
    },
  ),
  assignClassrooms: (id: number, classroomIds: number[]) => fetchJson<{ message: string; dictionaryId: number; assignedCount: number }>(
    `${API_BASE}/dictionaries/${id}/assign/classrooms`,
    {
      method: 'POST',
      body: JSON.stringify({ classroomIds }),
    },
  ),
};

export const booksImportApi = {
  createBatch: () => fetchJson<BooksImportJob>(`${API_BASE}/books-import/batches`, { method: 'POST' }),
  getLatestBatch: () => fetchJson<BooksImportJob>(`${API_BASE}/books-import/batches/latest`),
  getBatch: (batchId: string) => fetchJson<BooksImportJob>(`${API_BASE}/books-import/batches/${batchId}`),
  getBatchFiles: (batchId: string) => fetchJson<BooksImportBatchFile[]>(`${API_BASE}/books-import/batches/${batchId}/files`),
  startAutoMerge: (batchId: string) => fetchJson<BooksImportJob>(`${API_BASE}/books-import/batches/${batchId}/auto-merge`, { method: 'POST' }),
  getConflicts: (batchId: string, resolved?: boolean) => {
    const params = new URLSearchParams();
    if (resolved !== undefined) {
      params.set('resolved', String(resolved));
    }
    const query = params.toString();
    return fetchJson<BooksImportConflict[]>(
      `${API_BASE}/books-import/batches/${batchId}/conflicts${query ? `?${query}` : ''}`,
    );
  },
  resolveConflict: (
    batchId: string,
    conflictId: number,
    payload: {
      resolution: 'KEEP_EXISTING' | 'USE_IMPORTED' | 'MANUAL' | 'IGNORE';
      finalWord?: string;
      finalDefinition?: string;
      finalDifficulty?: number;
      comment?: string;
    },
  ) => fetchJson<BooksImportConflict>(
    `${API_BASE}/books-import/batches/${batchId}/conflicts/${conflictId}/resolve`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  ),
  publish: (batchId: string) => fetchJson<BooksImportJob>(`${API_BASE}/books-import/batches/${batchId}/publish`, { method: 'POST' }),
  discard: (batchId: string) => fetchJson<BooksImportJob>(`${API_BASE}/books-import/batches/${batchId}/discard`, { method: 'POST' }),
};

export const metaWordApi = {
  getAll: () => fetchJson<MetaWord[]>(`${API_BASE}/meta-words`),
  getById: (id: number) => fetchJson<MetaWord>(`${API_BASE}/meta-words/${id}`),
  getByWord: (word: string) => fetchJson<MetaWord>(`${API_BASE}/meta-words/word/${word}`),
  search: (keyword: string, dictionaryId?: number, page?: number, size?: number) => {
    const requestBody = {
      keyword,
      dictionaryId,
      page,
      size
    };
    
    return fetchJson<Page<MetaWord>>(`${API_BASE}/meta-words/search`, {
      method: 'POST',
      body: JSON.stringify(requestBody),
    });
  },
  getByDifficulty: (difficulty: number) => fetchJson<MetaWord[]>(`${API_BASE}/meta-words/difficulty/${difficulty}`),
  create: (word: Omit<MetaWord, 'id'>) => fetchJson<MetaWord>(`${API_BASE}/meta-words`, {
    method: 'POST',
    body: JSON.stringify(word),
  }),
  deleteAll: () => fetch(`${API_BASE}/meta-words`, { method: 'DELETE' }),
  import: () => fetchJson<BooksImportJob>(`${API_BASE}/meta-words/import`, { method: 'POST' }),
};

export const dictionaryWordApi = {
  getByDictionary: (dictionaryId: number) => fetchJson<DictionaryWord[]>(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}`),
  getWordsByDictionary: (dictionaryId: number, page: number = 1, size: number = 10) => fetchJson<Page<MetaWord>>(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}/words?page=${page}&size=${size}`),
  getMetaWordSuggestions: (
    dictionaryId: number,
    keyword: string,
    limit: number = 8,
    signal?: AbortSignal,
  ) => fetchJson<MetaWord[]>(
    `${API_BASE}/dictionary-words/dictionary/${dictionaryId}/meta-word-suggestions?keyword=${encodeURIComponent(keyword)}&limit=${limit}`,
    { signal },
  ),
  getByWord: (metaWordId: number) => fetchJson<DictionaryWord[]>(`${API_BASE}/dictionary-words/word/${metaWordId}`),
  addWord: (dictionaryId: number, metaWordId: number) => fetchJson<DictionaryWord>(`${API_BASE}/dictionary-words/${dictionaryId}/${metaWordId}`, { method: 'POST' }),
  generateWithAi: (dictionaryId: number, payload: GenerateDictionaryWordWithAiPayload) =>
    fetchJson<GenerateDictionaryWordWithAiResponse>(`${API_BASE}/dictionary-words/${dictionaryId}/words/ai-generate`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  removeByDictionary: (dictionaryId: number) => fetch(`${API_BASE}/dictionary-words/dictionary/${dictionaryId}`, { method: 'DELETE' }),
  addWordList: (dictionaryId: number, words: MetaWordEntry[]) => fetchJson<WordListProcessResult>(`${API_BASE}/dictionary-words/${dictionaryId}/words/list`, {
    method: 'POST',
    body: JSON.stringify({ words }),
  }),
  importCsv: (dictionaryId: number, formData: FormData) => fetchJson<WordListProcessResult>(`${API_BASE}/dictionary-words/${dictionaryId}/words/import-csv`, {
    method: 'POST',
    body: formData,
  }),
  importJson: (dictionaryId: number, jsonData: string) => fetchJson<WordListProcessResult>(`${API_BASE}/dictionary-words/${dictionaryId}/words/import-json`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: jsonData,
  }),
};

export const examApi = {
  create: (dictionaryId: number, questionCount: number, targetUserId: number) => fetchJson<Exam>(`${API_BASE}/exams`, {
    method: 'POST',
    body: JSON.stringify({ dictionaryId, questionCount, targetUserId }),
  }),
  getHistory: (dictionaryId?: number) => {
    const query = dictionaryId ? `?dictionaryId=${dictionaryId}` : '';
    return fetchJson<ExamHistoryItem[]>(`${API_BASE}/exams/history${query}`);
  },
  getById: (examId: number) => fetchJson<Exam>(`${API_BASE}/exams/${examId}`),
  getResult: (examId: number) => fetchJson<ExamSubmissionResult>(`${API_BASE}/exams/${examId}/result`),
  submit: (examId: number, answers: ExamAnswer[]) => fetchJson<ExamSubmissionResult>(`${API_BASE}/exams/${examId}/submit`, {
    method: 'POST',
    body: JSON.stringify({ answers }),
  }),
};

export const aiConfigApi = {
  list: (params?: { status?: AiConfigStatus; providerName?: string }) => {
    const searchParams = new URLSearchParams();
    if (params?.status) {
      searchParams.set('status', params.status);
    }
    if (params?.providerName) {
      searchParams.set('providerName', params.providerName);
    }
    const query = searchParams.toString();
    return fetchJson<AiConfig[]>(`${API_BASE}/ai-configs${query ? `?${query}` : ''}`);
  },
  getById: (id: number) => fetchJson<AiConfig>(`${API_BASE}/ai-configs/${id}`),
  create: (payload: CreateAiConfigPayload) => fetchJson<AiConfig>(`${API_BASE}/ai-configs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  update: (id: number, payload: UpdateAiConfigPayload) => fetchJson<AiConfig>(`${API_BASE}/ai-configs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  remove: (id: number) => fetchJson<void>(`${API_BASE}/ai-configs/${id}`, {
    method: 'DELETE',
  }),
  updateStatus: (id: number, status: AiConfigStatus) => fetchJson<AiConfig>(`${API_BASE}/ai-configs/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }),
  setDefault: (id: number) => fetchJson<AiConfig>(`${API_BASE}/ai-configs/${id}/default`, {
    method: 'PATCH',
  }),
  test: (id: number) => fetchJson<AiConfigTestResponse>(`${API_BASE}/ai-configs/${id}/test`, {
    method: 'POST',
  }),
};

export const aiApi = {
  chat: (payload: AiChatPayload) => fetchJson<AiChatResponse>(`${API_BASE}/ai/chat`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  generateWordDetails: (payload: GenerateWordDetailsPayload) => fetchJson<GenerateWordDetailsResponse>(`${API_BASE}/ai/generate-word-details`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  generateReading: (payload: GenerateReadingPayload) => fetchJson<GenerateReadingResponse>(`${API_BASE}/ai/generate-reading`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
};

export const userApi = {
  getAll: () => fetchJson<User[]>(`${API_BASE}/users`),
  getStudents: () => fetchJson<User[]>(`${API_BASE}/users/students`),
  create: (payload: {
    username: string;
    password: string;
    displayName: string;
    email?: string;
    phone?: string;
    role: User['role'];
  }) => fetchJson<User>(`${API_BASE}/users`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  updateRole: (id: number, role: User['role']) => fetchJson<User>(`${API_BASE}/users/${id}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  }),
  updateStatus: (id: number, status: User['status']) => fetchJson<User>(`${API_BASE}/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }),
};

export const classroomApi = {
  getAll: () => fetchJson<Classroom[]>(`${API_BASE}/classrooms`),
  create: (payload: {
    name: string;
    description?: string;
    teacherId?: number;
  }) => fetchJson<Classroom>(`${API_BASE}/classrooms`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  update: (id: number, payload: {
    name: string;
    description?: string;
    teacherId?: number;
  }) => fetchJson<Classroom>(`${API_BASE}/classrooms/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  deleteById: (id: number) => fetchJson<{ message: string; id: number }>(`${API_BASE}/classrooms/${id}`, {
    method: 'DELETE',
  }),
  getStudents: (id: number) => fetchJson<User[]>(`${API_BASE}/classrooms/${id}/students`),
  addStudent: (id: number, studentId: number) => fetchJson<void>(`${API_BASE}/classrooms/${id}/students/${studentId}`, {
    method: 'POST',
  }),
  removeStudent: (id: number, studentId: number) => fetchJson<void>(`${API_BASE}/classrooms/${id}/students/${studentId}`, {
    method: 'DELETE',
  }),
};

export const teacherApi = {
  getMyStudents: () => fetchJson<User[]>(`${API_BASE}/teachers/me/students`),
  getStudents: (teacherId: number) => fetchJson<User[]>(`${API_BASE}/teachers/${teacherId}/students`),
  assignStudent: (teacherId: number, studentId: number) => fetchJson<void>(`${API_BASE}/teachers/${teacherId}/students/${studentId}`, {
    method: 'POST',
  }),
  removeStudent: (teacherId: number, studentId: number) => fetchJson<void>(`${API_BASE}/teachers/${teacherId}/students/${studentId}`, {
    method: 'DELETE',
  }),
};

export const studentApi = {
  getMyDictionaries: () => fetchJson<Dictionary[]>(`${API_BASE}/students/me/dictionaries`),
};

export const studentDashboardApi = {
  get: () => fetchJson<StudentDashboard>(`${API_BASE}/students/me/dashboard`),
  record: (payload: StudentDashboardRecordPayload) => fetchJson<StudentDashboard>(
    `${API_BASE}/students/me/dashboard/records`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  ),
};

export const studyPlanApi = {
  create: (payload: CreateStudyPlanPayload) => fetchJson<StudyPlan>(`${API_BASE}/study-plans`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  list: () => fetchJson<StudyPlan[]>(`${API_BASE}/study-plans`),
  getById: (id: number) => fetchJson<StudyPlan>(`${API_BASE}/study-plans/${id}`),
  publish: (id: number) => fetchJson<StudyPlan>(`${API_BASE}/study-plans/${id}/publish`, {
    method: 'POST',
  }),
  getOverview: (id: number) => fetchJson<StudyPlanOverview>(`${API_BASE}/study-plans/${id}/overview`),
  getStudents: (id: number) => fetchJson<StudyPlanStudentSummary[]>(`${API_BASE}/study-plans/${id}/students`),
  getStudentAttention: (id: number, studentId: number) => fetchJson<StudyPlanStudentAttention>(
    `${API_BASE}/study-plans/${id}/students/${studentId}/attention`,
  ),
};

export const studentStudyPlanApi = {
  listMine: () => fetchJson<StudentStudyPlanSummary[]>(`${API_BASE}/students/me/study-plans`),
  getTodayTask: (studentStudyPlanId: number) => fetchJson<StudyTask>(
    `${API_BASE}/students/me/study-plans/${studentStudyPlanId}/today`,
  ),
  record: (studentStudyPlanId: number, payload: RecordStudyPayload) => fetchJson<StudyTask>(
    `${API_BASE}/students/me/study-plans/${studentStudyPlanId}/records`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  ),
  getAttention: (studentStudyPlanId: number) => fetchJson<StudentAttentionDailyStat[]>(
    `${API_BASE}/students/me/study-plans/${studentStudyPlanId}/attention`,
  ),
};
