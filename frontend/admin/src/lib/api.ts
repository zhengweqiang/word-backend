import { clearStoredToken, getStoredToken } from "@/lib/session";
import type {
    AiChatResponse,
    AiConfigResponse,
    AiConfigStatus,
    AiConfigTestResponse,
    ApiErrorPayload,
    BooksImportBatchFileResponse,
    BooksImportConflictResponse,
    BooksImportJobResponse,
    ClassroomResponse,
    CreateAiConfigPayload,
    CreateVideoStorageConfigPayload,
    Dictionary,
    DictionaryWordEntryResponse,
    GenerateDictionaryWordWithAiPayload,
    GenerateDictionaryWordWithAiResponse,
    GenerateWordDetailsPayload,
    GenerateWordDetailsResponse,
    LoginResponse,
    MetaWordEntryPayload,
    MetaWordSuggestionResponse,
    PaginatedResponse,
    QuoteResponse,
    StudyPlanOverviewResponse,
    StudyPlanResponse,
    StudyPlanStudentSummaryResponse,
    SyllableBackfillResponse,
    UpdateAiConfigPayload,
    UpdateVideoStorageConfigPayload,
    UserResponse,
    VideoAccessResponse,
    VideoPublishStatus,
    VideoResponse,
    VideoStatus,
    VideoStorageConfigResponse,
    VideoStorageConfigStatus,
    VideoStorageConfigTestResponse,
    WordListProcessResult,
} from "@/types/api";

export class ApiError extends Error {
    status: number;
    details: string[];

    constructor(message: string, status: number, details: string[] = []) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.details = details;
    }
}

type RequestOptions = Omit<RequestInit, "body"> & {
    body?: BodyInit | object | null;
};

const buildHeaders = (headers?: HeadersInit) => {
    const nextHeaders = new Headers(headers);
    const token = getStoredToken();
    if (token) {
        nextHeaders.set("Authorization", `Bearer ${token}`);
    }
    return nextHeaders;
};

const parsePayload = async (response: Response) => {
    const contentType = response.headers.get("content-type");
    if (response.status === 204) {
        return null;
    }
    if (contentType?.includes("application/json")) {
        return response.json();
    }
    const text = await response.text();
    return text ? { message: text } : null;
};

const toApiError = (status: number, payload: ApiErrorPayload | null) => {
    if (status === 401) {
        clearStoredToken();
    }
    return new ApiError(
        payload?.message ?? payload?.error ?? "请求失败",
        status,
        payload?.details ?? [],
    );
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const headers = buildHeaders(options.headers);
    let body = options.body ?? null;

    if (body && !(body instanceof FormData) && typeof body !== "string") {
        headers.set("Content-Type", "application/json");
        body = JSON.stringify(body);
    }

    const response = await fetch(path, {
        ...options,
        headers,
        body,
        credentials: "include",
    });

    const payload = await parsePayload(response);
    if (!response.ok) {
        throw toApiError(response.status, payload as ApiErrorPayload | null);
    }

    return payload as T;
}

const buildQueryString = (params: Record<string, string | number | undefined>) => {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === "") {
            return;
        }
        searchParams.set(key, String(value));
    });
    const query = searchParams.toString();
    return query ? `?${query}` : "";
};

export const api = {
    authQuote: () => request<QuoteResponse>("/api/auth/quote"),
    authMe: () => request<UserResponse>("/api/auth/me"),
    login: (payload: { username: string; password: string }) =>
        request<LoginResponse>("/api/auth/login", { method: "POST", body: payload }),
    logout: () => request<void>("/api/auth/logout", { method: "POST" }),

    listUsers: () => request<UserResponse[]>("/api/users"),
    listUsersPage: (params: { page?: number; size?: number; role?: string; name?: string }) =>
        request<PaginatedResponse<UserResponse>>(`/api/users/page${buildQueryString(params)}`),
    listStudents: () => request<UserResponse[]>("/api/users/students"),
    createUser: (payload: {
        username: string;
        password: string;
        displayName: string;
        email?: string;
        phone?: string;
        role: string;
    }) => request<UserResponse>("/api/users", { method: "POST", body: payload }),
    updateUserRole: (id: number, role: string) =>
        request<UserResponse>(`/api/users/${id}/role`, { method: "PATCH", body: { role } }),
    updateUserStatus: (id: number, status: string) =>
        request<UserResponse>(`/api/users/${id}/status`, { method: "PATCH", body: { status } }),

    listTeacherStudents: (teacherId: number) => request<UserResponse[]>(`/api/teachers/${teacherId}/students`),
    listMyStudents: () => request<UserResponse[]>("/api/teachers/me/students"),
    listMyStudentsPage: (params: { page?: number; size?: number; name?: string }) =>
        request<PaginatedResponse<UserResponse>>(`/api/teachers/me/students/page${buildQueryString(params)}`),
    assignStudentToTeacher: (teacherId: number, studentId: number) =>
        request<void>(`/api/teachers/${teacherId}/students/${studentId}`, { method: "POST" }),
    removeStudentFromTeacher: (teacherId: number, studentId: number) =>
        request<void>(`/api/teachers/${teacherId}/students/${studentId}`, { method: "DELETE" }),

    listClassrooms: () => request<ClassroomResponse[]>("/api/classrooms"),
    listClassroomsPage: (params: { page?: number; size?: number; keyword?: string; sortBy?: string; sortDir?: string }) =>
        request<PaginatedResponse<ClassroomResponse>>(`/api/classrooms/page${buildQueryString(params)}`),
    createClassroom: (payload: { name: string; description?: string; teacherId?: number | null }) =>
        request<ClassroomResponse>("/api/classrooms", { method: "POST", body: payload }),
    deleteClassroom: (classroomId: number) =>
        request<{ message: string; id: number }>(`/api/classrooms/${classroomId}`, { method: "DELETE" }),
    getClassroomStudents: (classroomId: number) => request<UserResponse[]>(`/api/classrooms/${classroomId}/students`),
    getClassroomDictionaries: (classroomId: number) =>
        request<Dictionary[]>(`/api/classrooms/${classroomId}/dictionaries`),
    assignDictionariesToClassroom: (classroomId: number, dictionaryIds: number[]) =>
        request<{ message: string; classroomId: number; assignedCount: number }>(`/api/classrooms/${classroomId}/dictionaries`, {
            method: "POST",
            body: { dictionaryIds },
        }),
    removeDictionaryFromClassroom: (classroomId: number, dictionaryId: number) =>
        request<void>(`/api/classrooms/${classroomId}/dictionaries/${dictionaryId}`, { method: "DELETE" }),
    addStudentToClassroom: (classroomId: number, studentId: number) =>
        request<void>(`/api/classrooms/${classroomId}/students/${studentId}`, { method: "POST" }),
    removeStudentFromClassroom: (classroomId: number, studentId: number) =>
        request<void>(`/api/classrooms/${classroomId}/students/${studentId}`, { method: "DELETE" }),

    listDictionaries: () => request<Dictionary[]>("/api/dictionaries"),
    listDictionaryEntriesPage: (
        dictionaryId: number,
        params: { page?: number; size?: number; keyword?: string; sortBy?: string; sortDir?: string },
    ) => request<PaginatedResponse<DictionaryWordEntryResponse>>(
        `/api/dictionary-words/dictionary/${dictionaryId}/entries${buildQueryString(params)}`,
    ),
    listDictionaryMetaWordSuggestions: (
        dictionaryId: number,
        params: { keyword: string; limit?: number },
        signal?: AbortSignal,
    ) =>
        request<MetaWordSuggestionResponse[]>(
            `/api/dictionary-words/dictionary/${dictionaryId}/meta-word-suggestions${buildQueryString(params)}`,
            { signal },
        ),
    addDictionaryWord: (dictionaryId: number, metaWordId: number) =>
        request<void>(`/api/dictionary-words/${dictionaryId}/${metaWordId}`, { method: "POST" }),
    generateDictionaryWordWithAi: (dictionaryId: number, payload: GenerateDictionaryWordWithAiPayload) =>
        request<GenerateDictionaryWordWithAiResponse>(`/api/dictionary-words/${dictionaryId}/words/ai-generate`, {
            method: "POST",
            body: payload,
        }),
    addDictionaryWordList: (dictionaryId: number, words: MetaWordEntryPayload[]) =>
        request<WordListProcessResult>(`/api/dictionary-words/${dictionaryId}/words/list`, {
            method: "POST",
            body: { words },
        }),
    createDictionary: (payload: { name: string; category?: string; scopeType?: string | null }) =>
        request<Dictionary>("/api/dictionaries", { method: "POST", body: payload }),
    assignDictionaryToClassrooms: (dictionaryId: number, classroomIds: number[]) =>
        request<{ message: string; assignedCount: number }>(`/api/dictionaries/${dictionaryId}/assign/classrooms`, {
            method: "POST",
            body: { classroomIds },
        }),

    listStudyPlans: () => request<StudyPlanResponse[]>("/api/study-plans"),
    createStudyPlan: (payload: Record<string, unknown>) =>
        request<StudyPlanResponse>("/api/study-plans", { method: "POST", body: payload }),
    publishStudyPlan: (planId: number) =>
        request<StudyPlanResponse>(`/api/study-plans/${planId}/publish`, { method: "POST" }),
    getStudyPlanOverview: (planId: number) =>
        request<StudyPlanOverviewResponse>(`/api/study-plans/${planId}/overview`),
    getStudyPlanStudents: (planId: number) =>
        request<StudyPlanStudentSummaryResponse[]>(`/api/study-plans/${planId}/students`),

    createImportBatch: () => request<BooksImportJobResponse>("/api/books-import/batches", { method: "POST" }),
    getLatestImportBatch: () => request<BooksImportJobResponse>("/api/books-import/batches/latest"),
    listImportBatchesPage: (params: { page?: number; size?: number }) =>
        request<PaginatedResponse<BooksImportJobResponse>>(`/api/books-import/batches/page${buildQueryString(params)}`),
    getImportBatch: (batchId: string) => request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}`),
    getImportBatchFiles: (batchId: string) =>
        request<BooksImportBatchFileResponse[]>(`/api/books-import/batches/${batchId}/files`),
    getImportBatchFilesPage: (batchId: string, params: { page?: number; size?: number }) =>
        request<PaginatedResponse<BooksImportBatchFileResponse>>(
            `/api/books-import/batches/${batchId}/files/page${buildQueryString(params)}`,
        ),
    getImportBatchConflicts: (batchId: string) =>
        request<BooksImportConflictResponse[]>(`/api/books-import/batches/${batchId}/conflicts`),
    autoMergeImportBatch: (batchId: string) =>
        request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}/auto-merge`, { method: "POST" }),
    publishImportBatch: (batchId: string) =>
        request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}/publish`, { method: "POST" }),
    discardImportBatch: (batchId: string) =>
        request<BooksImportJobResponse>(`/api/books-import/batches/${batchId}/discard`, { method: "POST" }),
    deleteImportBatch: (batchId: string) => request<void>(`/api/books-import/batches/${batchId}`, { method: "DELETE" }),

    listAiConfigs: (params?: { status?: AiConfigStatus; providerName?: string }) =>
        request<AiConfigResponse[]>(
            `/api/ai-configs${buildQueryString({
                status: params?.status,
                providerName: params?.providerName,
            })}`,
        ),
    getAiConfig: (id: number) => request<AiConfigResponse>(`/api/ai-configs/${id}`),
    createAiConfig: (payload: CreateAiConfigPayload) =>
        request<AiConfigResponse>("/api/ai-configs", { method: "POST", body: payload }),
    updateAiConfig: (id: number, payload: UpdateAiConfigPayload) =>
        request<AiConfigResponse>(`/api/ai-configs/${id}`, { method: "PUT", body: payload }),
    updateAiConfigStatus: (id: number, status: AiConfigStatus) =>
        request<AiConfigResponse>(`/api/ai-configs/${id}/status`, {
            method: "PATCH",
            body: { status },
        }),
    setDefaultAiConfig: (id: number) =>
        request<AiConfigResponse>(`/api/ai-configs/${id}/default`, { method: "PATCH" }),
    testAiConfig: (id: number) =>
        request<AiConfigTestResponse>(`/api/ai-configs/${id}/test`, { method: "POST" }),
    deleteAiConfig: (id: number) => request<void>(`/api/ai-configs/${id}`, { method: "DELETE" }),
    chatWithAi: (payload: { configId: number; messages: { role: "system" | "user" | "assistant"; content: string }[] }) =>
        request<AiChatResponse>("/api/ai/chat", { method: "POST", body: payload }),
    generateWordDetails: (payload: GenerateWordDetailsPayload) =>
        request<GenerateWordDetailsResponse>("/api/ai/generate-word-details", { method: "POST", body: payload }),
    backfillSyllables: (limit = 200) =>
        request<SyllableBackfillResponse>(`/api/admin/syllables/backfill?limit=${limit}`, { method: "POST" }),

    listVideosPage: (params: {
        page?: number;
        size?: number;
        keyword?: string;
        status?: VideoStatus;
        publishStatus?: VideoPublishStatus;
        scopeType?: string;
    }) =>
        request<PaginatedResponse<VideoResponse>>(`/api/videos/page${buildQueryString(params)}`),
    getVideo: (id: number) => request<VideoResponse>(`/api/videos/${id}`),
    uploadVideo: (formData: FormData) =>
        request<VideoResponse>("/api/videos/upload", { method: "POST", body: formData }),
    getVideoAccess: (id: number) => request<VideoAccessResponse>(`/api/videos/${id}/access`),
    syncVideo: (id: number) => request<VideoResponse>(`/api/videos/${id}/sync`, { method: "POST" }),
    publishVideo: (id: number) => request<VideoResponse>(`/api/videos/${id}/publish`, { method: "POST" }),
    unpublishVideo: (id: number) => request<VideoResponse>(`/api/videos/${id}/unpublish`, { method: "POST" }),
    deleteVideo: (id: number) => request<void>(`/api/videos/${id}`, { method: "DELETE" }),

    listVideoStorageConfigs: (params?: { status?: VideoStorageConfigStatus }) =>
        request<VideoStorageConfigResponse[]>(
            `/api/video-storage-configs${buildQueryString({
                status: params?.status,
            })}`,
        ),
    getVideoStorageConfig: (id: number) => request<VideoStorageConfigResponse>(`/api/video-storage-configs/${id}`),
    createVideoStorageConfig: (payload: CreateVideoStorageConfigPayload) =>
        request<VideoStorageConfigResponse>("/api/video-storage-configs", { method: "POST", body: payload }),
    updateVideoStorageConfig: (id: number, payload: UpdateVideoStorageConfigPayload) =>
        request<VideoStorageConfigResponse>(`/api/video-storage-configs/${id}`, { method: "PUT", body: payload }),
    updateVideoStorageConfigStatus: (id: number, status: VideoStorageConfigStatus) =>
        request<VideoStorageConfigResponse>(`/api/video-storage-configs/${id}/status`, {
            method: "PATCH",
            body: { status },
        }),
    setDefaultVideoStorageConfig: (id: number) =>
        request<VideoStorageConfigResponse>(`/api/video-storage-configs/${id}/default`, { method: "PATCH" }),
    testVideoStorageConfig: (id: number) =>
        request<VideoStorageConfigTestResponse>(`/api/video-storage-configs/${id}/test`, { method: "POST" }),
    deleteVideoStorageConfig: (id: number) =>
        request<void>(`/api/video-storage-configs/${id}`, { method: "DELETE" }),
};
