export interface MetaWord {
  id: number;
  word: string;
  phonetic?: string;
  definition?: string;
  partOfSpeech?: string;
  exampleSentence?: string;
  translation?: string;
  phoneticDetail?: PhoneticDetail | null;
  syllableDetail?: SyllableDetail | null;
  difficulty?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface PhoneticDetail {
  uk?: string | null;
  us?: string | null;
}

export interface SyllableSegment {
  text: string;
  ukPhonetic?: string | null;
  usPhonetic?: string | null;
  ukAudioUrl?: string | null;
  usAudioUrl?: string | null;
}

export interface SyllableDetail {
  segments: SyllableSegment[];
}

export interface MetaWordEntry {
  word: string;
  phonetic?: string;
  definition?: string;
  partOfSpeech?: string;
  exampleSentence?: string;
  translation?: string;
  difficulty?: number;
}

export interface Dictionary {
  id: number;
  name: string;
  filePath?: string;
  fileSize?: number;
  category?: string;
  wordCount?: number;
  creationType?: 'USER_CREATED' | 'IMPORTED';
  createdBy?: number;
  ownerUserId?: number;
  scopeType?: 'SYSTEM' | 'TEACHER' | 'PERSONAL';
  createdAt?: string;
  updatedAt?: string;
}

export type BooksImportJobStatus =
  | 'PENDING'
  | 'SCANNING'
  | 'STAGING'
  | 'STAGED'
  | 'AUTO_MERGING'
  | 'WAITING_REVIEW'
  | 'READY_TO_PUBLISH'
  | 'PUBLISHING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'DISCARDED';

export interface BooksImportJob {
  jobId: string;
  status: BooksImportJobStatus;
  batchType?: string;
  totalFiles?: number;
  processedFiles?: number;
  failedFiles?: number;
  totalRows?: number;
  processedRows?: number;
  successRows?: number;
  failedRows?: number;
  importedDictionaryCount?: number;
  importedWordCount?: number;
  currentFile?: string | null;
  candidateCount?: number;
  conflictCount?: number;
  errorMessage?: string | null;
  createdBy?: number | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  publishStartedAt?: string | null;
  publishFinishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type BooksImportBatchFileStatus = 'PENDING' | 'STAGING' | 'STAGED' | 'FAILED';

export interface BooksImportBatchFile {
  id: number;
  batchId: string;
  fileName: string;
  dictionaryName: string;
  status: BooksImportBatchFileStatus;
  rowCount?: number;
  successRows?: number;
  failedRows?: number;
  durationMs?: number | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type BooksImportConflictType = 'FIELD_CONFLICT' | 'MULTI_SOURCE_CONFLICT' | 'NORMALIZATION_COLLISION';

export type BooksImportConflictResolution = 'KEEP_EXISTING' | 'USE_IMPORTED' | 'MANUAL' | 'IGNORE';

export interface BooksImportConflict {
  id: number;
  candidateId?: number | null;
  normalizedWord: string;
  displayWord: string;
  conflictType: BooksImportConflictType;
  dictionaryNames: string[];
  resolution?: BooksImportConflictResolution | null;
  existingPayload?: Record<string, unknown> | null;
  importedPayload?: Record<string, unknown> | null;
  resolvedPayload?: Record<string, unknown> | null;
  comment?: string | null;
  resolvedAt?: string | null;
}

export interface Classroom {
  id: number;
  name: string;
  description?: string | null;
  teacherId: number;
  teacherName?: string | null;
  studentCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT';

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';

export interface User {
  id: number;
  username: string;
  displayName: string;
  email?: string | null;
  phone?: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
  lastLoginAt?: string | null;
}

export type AiConfigStatus = 'ENABLED' | 'DISABLED';

export interface AiConfig {
  id: number;
  providerName: string;
  apiUrl: string;
  apiKeyMasked: string;
  modelName: string;
  status: AiConfigStatus;
  isDefault: boolean;
  remark?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateAiConfigPayload {
  providerName: string;
  apiUrl: string;
  apiKey: string;
  modelName: string;
  status: AiConfigStatus;
  isDefault: boolean;
  remark?: string;
}

export interface UpdateAiConfigPayload {
  providerName: string;
  apiUrl: string;
  apiKey?: string;
  modelName: string;
  status: AiConfigStatus;
  isDefault: boolean;
  remark?: string;
}

export interface AiConfigTestResponse {
  configId: number;
  providerName: string;
  modelName: string;
  success: boolean;
  reply: string;
}

export interface AiChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface AiChatPayload {
  configId: number;
  messages: AiChatMessage[];
}

export interface AiChatResponse {
  configId: number;
  providerName: string;
  modelName: string;
  reply: string;
}

export interface GenerateWordDetailsPayload {
  configId?: number;
  word: string;
}

export interface GenerateWordDetailsResponse {
  configId: number;
  providerName: string;
  modelName: string;
  word: string;
  translation?: string | null;
  partOfSpeech?: string | null;
  phonetic?: string | null;
  definition?: string | null;
  exampleSentence?: string | null;
}

export interface GenerateDictionaryWordWithAiPayload {
  configId?: number;
  metaWordId?: number;
  word: string;
}

export interface GenerateDictionaryWordWithAiResponse {
  dictionaryId: number;
  metaWordId: number;
  configId: number;
  providerName: string;
  modelName: string;
  word: string;
  translation?: string | null;
  partOfSpeech?: string | null;
  phonetic?: string | null;
  definition?: string | null;
  exampleSentence?: string | null;
  total: number;
  existed: number;
  created: number;
  added: number;
  failed: number;
}

export interface GenerateReadingPayload {
  configId?: number;
  topic: string;
  wordCount: number;
  difficulty: string;
}

export interface GenerateReadingResponse {
  configId: number;
  providerName: string;
  modelName: string;
  title: string;
  content: string;
}

export interface FamousQuote {
  text: string;
  translation: string;
  author: string;
}

export interface LoginResponse {
  token: string;
  user: User;
  quote: FamousQuote;
}

export interface DictionaryWord {
  id: number;
  dictionaryId: number;
  metaWordId: number;
  createdAt?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ExamOption {
  key: string;
  translation: string;
}

export interface ExamQuestion {
  questionId: number;
  word: string;
  options: ExamOption[];
}

export interface Exam {
  examId: number;
  dictionaryId: number;
  dictionaryName: string;
  questionCount: number;
  answeredCount: number;
  correctCount: number;
  score: number;
  status: 'GENERATED' | 'SUBMITTED';
  createdAt?: string;
  submittedAt?: string;
  questions: ExamQuestion[];
}

export interface ExamAnswer {
  questionId: number;
  selectedOption: string;
}

export interface ExamResultQuestion {
  questionId: number;
  word: string;
  selectedOption?: string;
  selectedTranslation?: string;
  correctOption: string;
  correctTranslation: string;
  correct: boolean;
}

export interface ExamSubmissionResult {
  examId: number;
  dictionaryId: number;
  dictionaryName: string;
  totalQuestions: number;
  answeredQuestions: number;
  correctCount: number;
  score: number;
  status: 'SUBMITTED';
  submittedAt?: string;
  results: ExamResultQuestion[];
}

export interface ExamHistoryItem {
  examId: number;
  dictionaryId: number;
  dictionaryName: string;
  questionCount: number;
  answeredCount: number;
  correctCount: number;
  score: number;
  status: 'SUBMITTED';
  createdAt?: string;
  submittedAt?: string;
}

export type ReviewMode = 'EBBINGHAUS' | 'FIXED_INTERVAL' | 'CUSTOM';

export type StudyPlanStatus = 'DRAFT' | 'PUBLISHED' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';

export type StudentStudyPlanStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'DROPPED';

export type StudyDayTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'MISSED';

export type StudyTaskType = 'OVERDUE_REVIEW' | 'TODAY_REVIEW' | 'NEW_LEARN';

export type StudyActionType = 'LEARN' | 'REVIEW';

export type StudyRecordResult = 'CORRECT' | 'INCORRECT' | 'SKIPPED';

export type AttentionState = 'FOCUSED' | 'MIXED' | 'IDLE';

export interface CreateStudyPlanPayload {
  name: string;
  description?: string;
  dictionaryId: number;
  classroomIds: number[];
  startDate: string;
  endDate?: string;
  timezone: string;
  dailyNewCount: number;
  dailyReviewLimit: number;
  reviewMode: ReviewMode;
  reviewIntervals: number[];
  completionThreshold: number;
  dailyDeadlineTime: string;
  attentionTrackingEnabled: boolean;
  minFocusSecondsPerWord: number;
  maxFocusSecondsPerWord: number;
  longStayWarningSeconds: number;
  idleTimeoutSeconds: number;
}

export interface StudyPlan {
  id: number;
  name: string;
  description?: string | null;
  teacherId: number;
  dictionaryId: number;
  dictionaryName: string;
  classroomIds: number[];
  startDate: string;
  endDate?: string | null;
  timezone: string;
  dailyNewCount: number;
  dailyReviewLimit: number;
  reviewMode: ReviewMode;
  reviewIntervals: number[];
  completionThreshold: number;
  dailyDeadlineTime: string;
  attentionTrackingEnabled: boolean;
  minFocusSecondsPerWord: number;
  maxFocusSecondsPerWord: number;
  longStayWarningSeconds: number;
  idleTimeoutSeconds: number;
  status: StudyPlanStatus;
  studentCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface StudyPlanOverview {
  studyPlanId: number;
  studyPlanName: string;
  status: StudyPlanStatus;
  taskDate: string;
  totalStudents: number;
  completedStudents: number;
  notStartedStudents: number;
  inProgressStudents: number;
  missedStudents: number;
  averageCompletionRate: number;
  averageAttentionScore: number;
}

export interface StudentAttentionDailyStat {
  taskDate: string;
  wordsVisited: number;
  wordsCompleted: number;
  totalFocusSeconds: number;
  avgFocusSecondsPerWord: number;
  medianFocusSecondsPerWord: number;
  maxFocusSecondsPerWord: number;
  longStayWordCount: number;
  idleInterruptCount: number;
  attentionScore: number;
}

export interface StudyPlanStudentSummary {
  studentId: number;
  studentName: string;
  studentStudyPlanId: number;
  status: StudentStudyPlanStatus;
  taskDate: string;
  todayStatus: StudyDayTaskStatus;
  completedCount: number;
  totalTaskCount: number;
  completionRate: number;
  totalFocusSeconds: number;
  avgFocusSecondsPerWord: number;
  attentionScore: number;
  currentStreak: number;
  lastStudyAt?: string | null;
}

export interface StudyPlanStudentAttention {
  studentId: number;
  studentName: string;
  planId: number;
  dailyStats: StudentAttentionDailyStat[];
}

export interface StudentStudyPlanSummary {
  studentStudyPlanId: number;
  studyPlanId: number;
  planName: string;
  planPublishedAt?: string | null;
  dictionaryId: number;
  dictionaryName: string;
  status: StudentStudyPlanStatus;
  overallProgress: number;
  currentStreak: number;
  lastStudyAt?: string | null;
  taskDate: string;
  todayStatus: StudyDayTaskStatus;
  totalTaskCount: number;
  completedCount: number;
  completionRate: number;
  avgFocusSeconds: number;
  attentionScore: number;
}

export interface StudyTaskItem {
  studyDayTaskItemId: number;
  metaWordId: number;
  word?: string | null;
  definition?: string | null;
  translation?: string | null;
  partOfSpeech?: string | null;
  exampleSentence?: string | null;
  phonetic?: string | null;
  phoneticDetail?: PhoneticDetail | null;
  syllableDetail?: SyllableDetail | null;
  taskType: StudyTaskType;
  phase: number;
  dueDate?: string | null;
}

export interface StudyTask {
  studentStudyPlanId: number;
  taskDate: string;
  status: StudyDayTaskStatus;
  overdueCount: number;
  reviewCount: number;
  newCount: number;
  completedCount: number;
  totalFocusSeconds: number;
  completionRate: number;
  avgFocusSecondsPerWord: number;
  attentionScore: number;
  queue: StudyTaskItem[];
}

export interface RecordStudyPayload {
  metaWordId: number;
  actionType: StudyActionType;
  result: StudyRecordResult;
  durationSeconds: number;
  focusSeconds: number;
  idleSeconds: number;
  interactionCount: number;
  attentionState: AttentionState;
}

export interface StudentDashboardReminder {
  code: 'UNFINISHED_TODAY_TASK' | 'OVERDUE_REVIEW';
  message: string;
  count: number;
}

export interface StudentDashboardTaskItem {
  studentStudyPlanId: number;
  studyDayTaskItemId: number;
  studyPlanId: number;
  planName: string;
  planPublishedAt?: string | null;
  metaWordId: number;
  word?: string | null;
  definition?: string | null;
  translation?: string | null;
  partOfSpeech?: string | null;
  exampleSentence?: string | null;
  phonetic?: string | null;
  phoneticDetail?: PhoneticDetail | null;
  syllableDetail?: SyllableDetail | null;
  taskType: StudyTaskType;
  phase: number;
  dueDate?: string | null;
  attemptCount: number;
}

export interface StudentDashboard {
  taskDate?: string | null;
  hasPlans: boolean;
  allTasksCompleted: boolean;
  overdueCount: number;
  reviewCount: number;
  newCount: number;
  completedCount: number;
  totalCount: number;
  completionRate: number;
  reminders: StudentDashboardReminder[];
  queue: StudentDashboardTaskItem[];
}

export interface StudentDashboardRecordPayload extends RecordStudyPayload {
  studentStudyPlanId: number;
}
