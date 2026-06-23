export type UserRole = "ADMIN" | "TEACHER" | "STUDENT";
export type UserStatus = "ACTIVE" | "DISABLED" | "LOCKED";
export type ReviewMode = "FIXED_INTERVALS" | "SMART_REVIEW" | string;
export type StudyPlanStatus = "DRAFT" | "PUBLISHED" | string;
export type StudentStudyPlanStatus = "ACTIVE" | "COMPLETED" | "DROPPED" | string;
export type StudyDayTaskStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "MISSED" | string;
export type BooksImportJobStatus =
    | "PENDING"
    | "SCANNING"
    | "STAGING"
    | "STAGED"
    | "AUTO_MERGING"
    | "WAITING_REVIEW"
    | "READY_TO_PUBLISH"
    | "PUBLISHING"
    | "SUCCEEDED"
    | "FAILED"
    | "CANCELLED"
    | "DISCARDED";
export type ImportConflictResolution = "KEEP_EXISTING" | "USE_IMPORTED" | "MANUAL" | "IGNORE";
export type ImportConflictType = "FIELD_CONFLICT" | "MULTI_SOURCE_CONFLICT" | "NORMALIZATION_COLLISION";

export interface QuoteResponse {
    author?: string | null;
    content?: string | null;
    translation?: string | null;
}

export interface UserResponse {
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

export interface LoginResponse {
    token: string;
    user: UserResponse;
    quote?: QuoteResponse | null;
}

export interface PaginatedResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
    numberOfElements: number;
    first?: boolean;
    last?: boolean;
    empty?: boolean;
}

export interface Dictionary {
    id: number;
    name: string;
    filePath?: string | null;
    fileSize?: number | null;
    category?: string | null;
    wordCount?: number | null;
    entryCount?: number | null;
    creationType?: string | null;
    createdBy?: number | null;
    ownerUserId?: number | null;
    scopeType?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface DictionaryWordEntryResponse {
    entryId: number;
    dictionaryId: number;
    metaWordId: number;
    word?: string | null;
    translation?: string | null;
    phonetic?: string | null;
    definition?: string | null;
    chapterTagId?: number | null;
    chapterDisplayPath?: string | null;
    entryOrder?: number | null;
}

export interface MetaWordSuggestionResponse {
    id: number;
    word: string;
    phonetic?: string | null;
    definition?: string | null;
    partOfSpeech?: string | null;
    exampleSentence?: string | null;
    translation?: string | null;
    difficulty?: number | null;
}

export interface MetaWordEntryPayload {
    word: string;
    translation?: string;
    partOfSpeech?: string;
    phonetic?: string;
    definition?: string;
    exampleSentence?: string;
    difficulty?: number;
}

export interface WordListProcessResult {
    total: number;
    existed: number;
    created: number;
    added: number;
    failed: number;
}

export interface ClassroomResponse {
    id: number;
    name: string;
    description?: string | null;
    teacherId: number;
    teacherName: string;
    studentCount: number;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface StudyPlanResponse {
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

export interface StudyPlanOverviewResponse {
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

export interface StudyPlanStudentSummaryResponse {
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

export interface BooksImportJobResponse {
    jobId: string;
    status: BooksImportJobStatus;
    batchType?: string | null;
    totalFiles?: number | null;
    processedFiles?: number | null;
    failedFiles?: number | null;
    totalRows?: number | null;
    processedRows?: number | null;
    successRows?: number | null;
    failedRows?: number | null;
    importedDictionaryCount?: number | null;
    importedWordCount?: number | null;
    currentFile?: string | null;
    candidateCount?: number | null;
    conflictCount?: number | null;
    errorMessage?: string | null;
    createdBy?: number | null;
    startedAt?: string | null;
    finishedAt?: string | null;
    publishStartedAt?: string | null;
    publishFinishedAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface BooksImportBatchFileResponse {
    id: number;
    batchId: string;
    fileName: string;
    dictionaryName?: string | null;
    status: string;
    rowCount?: number | null;
    successRows?: number | null;
    failedRows?: number | null;
    durationMs?: number | null;
    errorMessage?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface BooksImportConflictResponse {
    id: number;
    candidateId: number;
    normalizedWord: string;
    displayWord: string;
    conflictType: ImportConflictType;
    dictionaryNames: string[];
    resolution?: ImportConflictResolution | null;
    existingPayload?: Record<string, unknown> | null;
    importedPayload?: Record<string, unknown> | null;
    resolvedPayload?: Record<string, unknown> | null;
    comment?: string | null;
    resolvedAt?: string | null;
}

export interface ApiErrorPayload {
    timestamp?: string;
    status?: number;
    error?: string;
    message?: string;
    details?: string[];
}

export type AiConfigStatus = "ENABLED" | "DISABLED";

export interface AiConfigResponse {
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

export interface AiChatMessagePayload {
    role: "system" | "user" | "assistant";
    content: string;
}

export interface AiChatResponse {
    configId: number;
    providerName: string;
    modelName: string;
    reply: string;
}

export interface SyllableBackfillFailureResponse {
    metaWordId: number;
    word: string;
    reason: string;
}

export interface SyllableBackfillResponse {
    attempted: number;
    updated: number;
    skipped: number;
    failures: SyllableBackfillFailureResponse[];
}

export type VideoStorageConfigStatus = "ENABLED" | "DISABLED";
export type VideoStorageProviderType = "TENCENT_VOD" | "VOLCENGINE_VOD";
export type VideoStatus = "PROCESSING" | "READY" | "FAILED";
export type VideoPublishStatus = "UNPUBLISHED" | "PUBLISHED";
export type VideoAccessMode = "PREVIEW" | "PLAY";

export interface VideoResponse {
    id: number;
    title: string;
    description?: string | null;
    originalFileName: string;
    contentType?: string | null;
    fileSize: number;
    tencentFileId: string;
    mediaUrl?: string | null;
    coverUrl?: string | null;
    durationSeconds?: number | null;
    status: VideoStatus;
    publishStatus: VideoPublishStatus;
    errorMessage?: string | null;
    createdBy: number;
    createdByDisplayName: string;
    ownerUserId: number;
    scopeType: string;
    storageConfigId: number;
    storageConfigName?: string | null;
    canManage: boolean;
    canPreview: boolean;
    publishedAt?: string | null;
    unpublishedAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface VideoAccessResponse {
    videoId: number;
    mode: VideoAccessMode;
    url: string;
    coverUrl?: string | null;
}

export interface VideoStorageConfigResponse {
    id: number;
    configName: string;
    secretIdMasked: string;
    secretKeyMasked: string;
    region: string;
    providerType: VideoStorageProviderType;
    subAppId?: number | null;
    spaceName?: string | null;
    procedureName?: string | null;
    status: VideoStorageConfigStatus;
    isDefault: boolean;
    remark?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface CreateVideoStorageConfigPayload {
    configName: string;
    secretId: string;
    secretKey: string;
    region: string;
    providerType: VideoStorageProviderType;
    subAppId?: number;
    spaceName?: string;
    procedureName?: string;
    status: VideoStorageConfigStatus;
    isDefault: boolean;
    remark?: string;
}

export interface UpdateVideoStorageConfigPayload {
    configName: string;
    secretId?: string;
    secretKey?: string;
    region: string;
    providerType: VideoStorageProviderType;
    subAppId?: number;
    spaceName?: string;
    procedureName?: string;
    status: VideoStorageConfigStatus;
    isDefault: boolean;
    remark?: string;
}

export interface VideoStorageConfigTestResponse {
    configId: number;
    configName: string;
    success: boolean;
    message: string;
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
