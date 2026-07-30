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

export type PointAccountStatus = "ACTIVE" | "FROZEN" | "CLOSED";
export type PointEventStatus = "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
export type PointEventAttemptStatus = "SUCCEEDED" | "FAILED";
export type PointAttemptTriggerType = "AUTO" | "MANUAL";
export type PointTransactionType = "EARN" | "DEDUCT" | "FREEZE" | "UNFREEZE" | "SPEND" | "REVERSE";
export type PointSourceType =
    | "STUDY_TASK"
    | "STUDY_RECORD"
    | "CLASSROOM_CHAT"
    | "VIDEO_WATCH"
    | "EXAM"
    | "PAPER_RELEASE_ATTEMPT"
    | "MANUAL_ADJUSTMENT"
    | "ADMIN_CORRECTION"
    | "REDEMPTION";

export interface AdminStudentPointAccountResponse {
    accountId: number;
    studentId: number;
    studentUsername?: string | null;
    studentName: string;
    availablePoints: number;
    frozenPoints: number;
    lifetimeEarnedPoints: number;
    lifetimeSpentPoints: number;
    status: PointAccountStatus;
    updatedAt?: string | null;
}

export interface StudentPointTransactionResponse {
    id: number;
    accountId: number;
    studentId: number;
    studentUsername?: string | null;
    studentName?: string | null;
    transactionType: PointTransactionType;
    amount: number;
    balanceBefore: number;
    balanceAfter: number;
    sourceType: PointSourceType;
    sourceId?: number | null;
    sourceKey: string;
    ruleCode?: string | null;
    operatorId?: number | null;
    operatorRole?: string | null;
    reason?: string | null;
    reversedTransactionId?: number | null;
    createdAt?: string | null;
}

export interface StudentPointEventResponse {
    id: number;
    studentId: number;
    studentUsername?: string | null;
    studentName?: string | null;
    sourceType: PointSourceType;
    sourceId?: number | null;
    sourceKey: string;
    ruleCode: string;
    ruleName: string;
    points: number;
    status: PointEventStatus;
    autoAttemptCount: number;
    nextRetryAt?: string | null;
    lastError?: string | null;
    operatorId?: number | null;
    operatorRole?: string | null;
    reason?: string | null;
    transactionId?: number | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    processedAt?: string | null;
}

export interface StudentPointEventAttemptResponse {
    id: number;
    eventId: number;
    attemptNo: number;
    triggerType: PointAttemptTriggerType;
    status: PointEventAttemptStatus;
    operatorId?: number | null;
    operatorRole?: string | null;
    reason?: string | null;
    errorMessage?: string | null;
    startedAt?: string | null;
    finishedAt?: string | null;
}

export interface StudentPointRuleResponse {
    id: number;
    code: string;
    name: string;
    description?: string | null;
    sourceType: PointSourceType;
    basePoints: number;
    scopeType?: string | null;
    scopeId?: number | null;
    enabled: boolean;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface StudentPointRuleCreatePayload {
    code: string;
    name: string;
    description?: string;
    sourceType: PointSourceType;
    basePoints: number;
    scopeType?: string;
    scopeId?: number;
    enabled: boolean;
    reason?: string;
}

export type StudentPointRuleUpdatePayload = Omit<StudentPointRuleCreatePayload, "code">;

export interface StudentPointAdjustmentPayload {
    requestKey: string;
    amount: number;
    reason: string;
    replacesAdjustmentRequestId?: number;
}

export interface StudentPointRedemptionPayload {
    requestKey: string;
    points: number;
    reason: string;
}

export interface StudentPointAdjustmentOutcome {
    requestId: number;
    eventId: number;
    status: string;
    transactionId?: number | null;
    availableBalance?: number | null;
}

export interface TeacherStudentPointResponse {
    studentId: number;
    studentName: string;
    availablePoints: number;
    lifetimeEarnedPoints: number;
    lifetimeSpentPoints: number;
    todayEarnedPoints: number;
}

export interface StudentPointSummaryResponse {
    studentId: number;
    availablePoints: number;
    frozenPoints: number;
    lifetimeEarnedPoints: number;
    lifetimeSpentPoints: number;
    todayEarnedPoints: number;
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

export type ClassroomGroupFeedMessageType = "TEXT" | "DICTIONARY" | "STUDY_PLAN" | "VIDEO";

export interface ClassroomConversationResponse {
    classroomId: number;
    classroomName: string;
    lastMessageSummary: string;
    lastMessageAt?: string | null;
}

export interface ClassroomGroupFeedMessageResponse {
    id: number;
    classroomId: number;
    messageType: ClassroomGroupFeedMessageType;
    content?: string | null;
    resourceId?: number | null;
    resourceTitle?: string | null;
    resourceSummary?: string | null;
    authorUserId: number;
    authorName: string;
    createdAt?: string | null;
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
export type VideoCloudPublishStatus = "UNPUBLISHED" | "PUBLISHED";
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
    cloudPublishStatus: VideoCloudPublishStatus;
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

export interface VideoCloudSyncResponse {
    scanned: number;
    imported: number;
    updated: number;
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

export type QuestionType = "SINGLE_CHOICE" | "MULTIPLE_CHOICE" | "FILL_IN_BLANK";
export type QuestionStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type QuestionImportBatchStatus = "PREVIEWED" | "CONFIRMED" | "EXPIRED";
export type QuestionImportRowStatus = "VALID" | "INVALID" | "DUPLICATE_CANDIDATE";
export type PaperTemplateStatus = "DRAFT" | "READY" | "ARCHIVED";
export type EditablePaperTemplateStatus = Exclude<PaperTemplateStatus, "ARCHIVED">;
export type PaperReleaseStatus = "SCHEDULED" | "OPEN" | "WITHDRAWN" | "INVALIDATED" | "SUPERSEDED";
export type PaperBlankAnswerPolicy = "ALLOW_BLANK" | "REQUIRE_ALL_ANSWERED";
export type PaperResultVisibility = "HIDDEN_UNTIL_RELEASED" | "SCORE_ONLY" | "SCORE_AND_ANSWERS";
export type StudentPaperAttemptStatus =
    | "NOT_STARTED"
    | "IN_PROGRESS"
    | "OVERDUE"
    | "SUBMITTED"
    | "SUBMITTED_LATE"
    | "INVALIDATED";

export interface QuestionPayload {
    questionType: QuestionType;
    category?: string | null;
    stem: string;
    options?: Record<string, string>;
    acceptedAnswers: string[];
    defaultScore: number;
    difficulty?: number;
    tags?: string[];
    explanation?: string;
    dictionaryId?: number;
    metaWordId?: number;
    status: QuestionStatus;
}

export interface QuestionBankItemResponse extends QuestionPayload {
    id: number;
    sourceQuestionId?: number | null;
    importBatchId?: number | null;
    createdByUserId: number;
    importedByUserId?: number | null;
    lastModifiedByUserId?: number | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    archivedAt?: string | null;
}

export interface QuestionCategoryResponse {
    id: number;
    name: string;
    createdByUserId: number;
    lastModifiedByUserId?: number | null;
    createdAt?: string | null;
    updatedAt?: string | null;
}

export interface QuestionImportPreviewRowResponse {
    id: number;
    rowNumber: number;
    status: QuestionImportRowStatus;
    questionType?: QuestionType | null;
    category?: string | null;
    stem?: string | null;
    options?: Record<string, string> | null;
    acceptedAnswers?: string[] | null;
    score?: number | null;
    difficulty?: number | null;
    tags?: string[] | null;
    explanation?: string | null;
    dictionaryName?: string | null;
    word?: string | null;
    dictionaryId?: number | null;
    metaWordId?: number | null;
    message?: string | null;
    duplicateQuestionId?: number | null;
    rawRow?: Record<string, unknown> | null;
}

export interface QuestionImportPreviewResponse {
    batchId: number;
    fileName: string;
    totalRows: number;
    validRows: number;
    invalidRows: number;
    duplicateRows: number;
    status: QuestionImportBatchStatus;
    expiresAt?: string | null;
    rows: QuestionImportPreviewRowResponse[];
}

export interface QuestionImportConfirmResponse {
    batchId: number;
    importedCount: number;
    importedQuestionIds: number[];
    status: QuestionImportBatchStatus;
    confirmedAt?: string | null;
}

export interface PaperTemplateQuestionResponse {
    id: number;
    sourceQuestionId: number;
    questionOrder: number;
    questionType: QuestionType;
    category?: string | null;
    stem: string;
    options?: Record<string, string> | null;
    acceptedAnswers: string[];
    explanation?: string | null;
    score: number;
    dictionaryId?: number | null;
    metaWordId?: number | null;
    createdAt?: string | null;
}

export interface PaperTemplateResponse {
    id: number;
    title: string;
    instructions?: string | null;
    ownerUserId: number;
    sourcePaperId?: number | null;
    status: PaperTemplateStatus;
    shuffleQuestions: boolean;
    shuffleOptions: boolean;
    totalScore: number;
    questionCount: number;
    categories?: string[];
    questions: PaperTemplateQuestionResponse[];
    createdAt?: string | null;
    updatedAt?: string | null;
    archivedAt?: string | null;
}

export interface PaperReleaseTargetResponse {
    id: number;
    studentId: number;
    sourceClassroomIds: number[];
    attemptId?: number | null;
    attemptStatus?: StudentPaperAttemptStatus | null;
}

export interface PaperReleaseResponse {
    id: number;
    paperTemplateId: number;
    title: string;
    instructions?: string | null;
    publishedByUserId: number;
    status: PaperReleaseStatus;
    questionCount: number;
    totalScore: number;
    categories?: string[];
    shuffleQuestions: boolean;
    shuffleOptions: boolean;
    startTime?: string | null;
    deadline?: string | null;
    blankAnswerPolicy: PaperBlankAnswerPolicy;
    resultVisibility: PaperResultVisibility;
    withdrawnAt?: string | null;
    withdrawnByUserId?: number | null;
    withdrawReason?: string | null;
    invalidatedAt?: string | null;
    invalidatedByUserId?: number | null;
    invalidateReason?: string | null;
    supersedesReleaseId?: number | null;
    supersededByReleaseId?: number | null;
    supersededAt?: string | null;
    supersededByUserId?: number | null;
    supersedeReason?: string | null;
    showSupersededToStudents?: boolean | null;
    createdAt?: string | null;
    targets: PaperReleaseTargetResponse[];
}

export interface PublishPaperPayload {
    paperId: number;
    studentIds: number[];
    classroomIds: number[];
    startTime?: string;
    deadline?: string;
    blankAnswerPolicy: PaperBlankAnswerPolicy;
    resultVisibility: PaperResultVisibility;
}

export interface PaperReleaseStudentResultResponse {
    releaseId: number;
    attemptId: number;
    studentId: number;
    studentUsername?: string | null;
    status: StudentPaperAttemptStatus;
    late: boolean;
    answeredCount: number;
    correctCount: number;
    earnedScore: number;
    totalScore: number;
    scorePercentage?: number | null;
    submittedAt?: string | null;
    questions: StudentPaperResultQuestionResponse[];
}

export interface StudentPaperResultQuestionResponse {
    releaseQuestionId: number;
    questionOrder: number;
    questionType: QuestionType;
    category?: string | null;
    stem: string;
    options?: Record<string, string> | null;
    selectedAnswers: string[];
    blankAnswers: string[];
    correct?: boolean | null;
    earnedScore?: number | null;
    questionScore: number;
    acceptedAnswers: string[];
    explanation?: string | null;
}

export interface PaperReleaseResultOverviewResponse {
    releaseId: number;
    title: string;
    releaseStatus: PaperReleaseStatus;
    assignedCount: number;
    notStartedCount: number;
    inProgressCount: number;
    overdueCount: number;
    submittedCount: number;
    submittedLateCount: number;
    completedCount: number;
    resultVisibility: PaperResultVisibility;
    resultsReleased: boolean;
    resultsReleasedAt?: string | null;
    resultsReleasedByUserId?: number | null;
    students: PaperReleaseStudentResultResponse[];
}

export interface PaperReleaseQuestionStatResponse {
    releaseQuestionId: number;
    questionOrder: number;
    questionType: QuestionType;
    category?: string | null;
    stem: string;
    submissionCount: number;
    answeredCount: number;
    correctCount: number;
    correctnessRate: number;
}
