import { createEffect, createMemo, createResource, createSignal, For, onCleanup, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatDate, formatDateTime } from "@/lib/format";
import type {
    ClassroomConversationResponse,
    ClassroomGroupFeedMessageResponse,
    PaginatedResponse,
    StudyPlanResponse,
    VideoResponse,
} from "@/types/api";

const PAGE_SIZE = 20;
const POLLING_INTERVAL_MS = 30000;

type PublishMode = "TEXT" | "STUDY_PLAN" | "VIDEO";

const messageTypeLabel = (message: ClassroomGroupFeedMessageResponse) => {
    switch (message.messageType) {
        case "TEXT":
            return "留言";
        case "DICTIONARY":
            return "词书";
        case "STUDY_PLAN":
            return "学习计划";
        case "VIDEO":
            return "视频";
        default:
            return "消息";
    }
};

const resourcePrefix = (message: ClassroomGroupFeedMessageResponse) => {
    switch (message.messageType) {
        case "STUDY_PLAN":
            return "学习计划";
        case "VIDEO":
            return "视频";
        case "DICTIONARY":
            return "词书";
        default:
            return "";
    }
};

const videoStatusLabel = (status: VideoResponse["status"]) => {
    switch (status) {
        case "READY":
            return "可预览";
        case "FAILED":
            return "失败";
        case "PROCESSING":
        default:
            return "处理中";
    }
};

const videoCloudPublishStatusLabel = (cloudPublishStatus: VideoResponse["cloudPublishStatus"]) =>
    cloudPublishStatus === "PUBLISHED" ? "云端可播" : "云端停用";

const canShareVideo = (video: VideoResponse) =>
    video.status === "READY" && video.cloudPublishStatus === "PUBLISHED" && video.canPreview;

const videoAvailabilitySummary = (video: VideoResponse) =>
    `${videoStatusLabel(video.status)} · ${videoCloudPublishStatusLabel(video.cloudPublishStatus)}`;

export function TeacherClassChatPage() {
    const auth = useAuth();
    const [selectedClassroomId, setSelectedClassroomId] = createSignal<number | null>(null);
    const [publishMode, setPublishMode] = createSignal<PublishMode>("TEXT");
    const [textDraft, setTextDraft] = createSignal("");
    const [selectedStudyPlanId, setSelectedStudyPlanId] = createSignal("");
    const [selectedVideoId, setSelectedVideoId] = createSignal("");
    const [feedback, setFeedback] = createSignal("");
    const [error, setError] = createSignal("");
    const [saving, setSaving] = createSignal(false);

    const [conversationsPage, { refetch: refetchConversations }] = createResource(
        () => auth.user()?.id,
        async (userId): Promise<PaginatedResponse<ClassroomConversationResponse> | null> => {
            if (!userId) {
                return null;
            }
            return api.listClassroomConversations({ page: 1, size: PAGE_SIZE });
        },
    );

    const conversations = createMemo(() => conversationsPage()?.content ?? []);

    createEffect(() => {
        const items = conversations();
        if (items.length === 0) {
            setSelectedClassroomId(null);
            return;
        }
        const current = selectedClassroomId();
        if (!current || !items.some((item) => item.classroomId === current)) {
            setSelectedClassroomId(items[0].classroomId);
        }
    });

    const [messagesPage, { refetch: refetchMessages }] = createResource(
        selectedClassroomId,
        async (classroomId): Promise<PaginatedResponse<ClassroomGroupFeedMessageResponse> | null> => {
            if (!classroomId) {
                return null;
            }
            return api.listClassroomGroupFeedMessages(classroomId, { page: 1, size: PAGE_SIZE });
        },
    );

    const [studyPlans] = createResource(
        () => auth.user()?.id,
        async (userId): Promise<StudyPlanResponse[]> => {
            if (!userId) {
                return [];
            }
            return api.listStudyPlans();
        },
    );

    const [videos] = createResource(
        () => auth.user()?.id,
        async (userId): Promise<VideoResponse[]> => {
            if (!userId) {
                return [];
            }
            const page = await api.listVideosPage({
                page: 1,
                size: 50,
            });
            return page.content;
        },
    );

    const messages = createMemo(() => messagesPage()?.content ?? []);
    const selectedConversation = createMemo(() =>
        conversations().find((item) => item.classroomId === selectedClassroomId()) ?? null,
    );
    const sharedStudyPlanIds = createMemo(() =>
        new Set(
            messages()
                .filter((message) => message.messageType === "STUDY_PLAN" && message.resourceId)
                .map((message) => message.resourceId as number),
        ),
    );
    const availableStudyPlans = createMemo(() => {
        const classroomId = selectedClassroomId();
        if (!classroomId) {
            return [];
        }
        return (studyPlans() ?? []).filter((plan) =>
            plan.status === "PUBLISHED"
            && plan.classroomIds.includes(classroomId)
            && !sharedStudyPlanIds().has(plan.id),
        );
    });
    const allVideos = createMemo(() => videos() ?? []);
    const availableVideos = createMemo(() => allVideos().filter(canShareVideo));
    const unavailableVideos = createMemo(() => allVideos().filter((video) => !canShareVideo(video)));
    const selectedStudyPlan = createMemo(() =>
        availableStudyPlans().find((plan) => String(plan.id) === selectedStudyPlanId()) ?? null,
    );
    const selectedVideo = createMemo(() =>
        availableVideos().find((video) => String(video.id) === selectedVideoId()) ?? null,
    );

    createEffect(() => {
        const plans = availableStudyPlans();
        if (plans.length === 0) {
            setSelectedStudyPlanId("");
            return;
        }
        if (!plans.some((plan) => String(plan.id) === selectedStudyPlanId())) {
            setSelectedStudyPlanId(String(plans[0].id));
        }
    });

    createEffect(() => {
        const items = availableVideos();
        if (items.length === 0) {
            setSelectedVideoId("");
            return;
        }
        if (!items.some((video) => String(video.id) === selectedVideoId())) {
            setSelectedVideoId(String(items[0].id));
        }
    });

    const refreshCurrentView = async () => {
        await Promise.all([
            refetchConversations(),
            selectedClassroomId() ? refetchMessages() : Promise.resolve(null),
        ]);
    };

    const poll = () => {
        if (document.hidden) {
            return;
        }
        void refreshCurrentView();
    };

    const interval = window.setInterval(poll, POLLING_INTERVAL_MS);
    onCleanup(() => window.clearInterval(interval));

    const runMutation = async (runner: () => Promise<unknown>, successMessage: string) => {
        setSaving(true);
        setError("");
        setFeedback("");
        try {
            await runner();
            setFeedback(successMessage);
            await refreshCurrentView();
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "操作失败，请稍后重试。");
        } finally {
            setSaving(false);
        }
    };

    const handleTextSubmit = async (event: SubmitEvent) => {
        event.preventDefault();
        const classroomId = selectedClassroomId();
        const content = textDraft().trim();
        if (!classroomId || !content) {
            return;
        }
        await runMutation(
            () => api.createClassroomGroupFeedTextMessage(classroomId, { content }),
            "留言已发布。",
        );
        setTextDraft("");
    };

    const shareStudyPlan = async () => {
        const classroomId = selectedClassroomId();
        const studyPlanId = Number(selectedStudyPlanId());
        if (!classroomId || !studyPlanId) {
            return;
        }
        await runMutation(
            () => api.shareClassroomGroupFeedStudyPlan(classroomId, { studyPlanId }),
            "学习计划已分享到班级。",
        );
    };

    const shareVideo = async () => {
        const classroomId = selectedClassroomId();
        const videoId = Number(selectedVideoId());
        if (!classroomId || !videoId) {
            return;
        }
        await runMutation(
            () => api.shareClassroomGroupFeedVideo(classroomId, { videoId }),
            "视频已分享到班级。",
        );
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Class Chat"
                title="班级聊天"
                description="按会话查看班级消息，向学生分享已发布学习计划和视频资源。"
                actions={
                    <Button variant="outline" onClick={() => void refreshCurrentView()}>
                        刷新
                    </Button>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>
            <Show when={error()}>
                <Alert class="border-destructive/20 bg-destructive/10 text-destructive">{error()}</Alert>
            </Show>

            <Show
                when={conversations().length > 0}
                fallback={
                    <EmptyState
                        title="暂无可管理班级"
                        description="创建班级或联系管理员分配班级后，就可以在这里查看班级聊天。"
                        actions={
                            <a
                                class="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all duration-200 hover:bg-accent hover:text-accent-foreground"
                                href="/classrooms"
                            >
                                去班级管理
                            </a>
                        }
                    />
                }
            >
                <div class="grid min-h-[620px] gap-4 xl:grid-cols-[280px_minmax(0,1fr)_320px]">
                    <aside class="overflow-hidden rounded-lg border border-border/70 bg-background/80">
                        <div class="border-b border-border/70 px-4 py-3">
                            <p class="text-sm font-semibold">会话</p>
                            <p class="text-xs text-muted-foreground">按最后消息时间排序</p>
                        </div>
                        <div class="max-h-[560px] overflow-y-auto p-2">
                            <For each={conversations()}>
                                {(conversation) => (
                                    <button
                                        type="button"
                                        class={cn(
                                            "w-full rounded-md px-3 py-3 text-left transition hover:bg-accent",
                                            selectedClassroomId() === conversation.classroomId && "bg-accent",
                                        )}
                                        onClick={() => setSelectedClassroomId(conversation.classroomId)}
                                    >
                                        <div class="flex items-start justify-between gap-3">
                                            <span class="font-medium">{conversation.classroomName}</span>
                                            <span class="shrink-0 text-xs text-muted-foreground">
                                                {conversation.lastMessageAt ? formatDate(conversation.lastMessageAt) : ""}
                                            </span>
                                        </div>
                                        <p class="mt-1 truncate text-sm text-muted-foreground">
                                            {conversation.lastMessageSummary}
                                        </p>
                                    </button>
                                )}
                            </For>
                        </div>
                    </aside>

                    <main class="flex min-h-[620px] flex-col rounded-lg border border-border/70 bg-background/80">
                        <div class="border-b border-border/70 px-5 py-4">
                            <h2 class="text-lg font-semibold">{selectedConversation()?.classroomName ?? "班级会话"}</h2>
                            <p class="text-sm text-muted-foreground">学生回复以普通消息展示。</p>
                        </div>
                        <div class="flex-1 overflow-y-auto px-5 py-4">
                            <Show
                                when={messages().length > 0}
                                fallback={
                                    <EmptyState
                                        title="暂无消息"
                                        description="发布一条留言，或分享已发布学习计划和视频资源。"
                                    />
                                }
                            >
                                <div class="space-y-3">
                                    <For each={messages()}>
                                        {(message) => (
                                            <article class="rounded-lg border border-border/70 bg-white px-4 py-3 shadow-sm">
                                                <div class="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                                                    <Badge variant="outline">{messageTypeLabel(message)}</Badge>
                                                    <span>{message.authorName}</span>
                                                    <span>{message.createdAt ? formatDateTime(message.createdAt) : ""}</span>
                                                </div>
                                                <Show
                                                    when={message.messageType === "TEXT"}
                                                    fallback={
                                                        <div class="mt-3 rounded-md border border-border/70 bg-muted/30 p-3">
                                                            <p class="text-xs font-semibold text-muted-foreground">
                                                                {resourcePrefix(message)}
                                                            </p>
                                                            <h3 class="mt-1 font-semibold">{message.resourceTitle ?? "未命名资源"}</h3>
                                                            <Show when={message.resourceSummary}>
                                                                <p class="mt-1 text-sm text-muted-foreground">{message.resourceSummary}</p>
                                                            </Show>
                                                        </div>
                                                    }
                                                >
                                                    <p class="mt-3 text-sm leading-6">{message.content}</p>
                                                </Show>
                                            </article>
                                        )}
                                    </For>
                                </div>
                            </Show>
                        </div>
                        <form class="border-t border-border/70 p-4" onSubmit={handleTextSubmit}>
                            <Label for="chat-text">发布留言</Label>
                            <div class="mt-2 flex gap-2">
                                <Input
                                    id="chat-text"
                                    placeholder="给这个班级发布一条文字留言"
                                    value={textDraft()}
                                    onInput={(event) => setTextDraft(event.currentTarget.value)}
                                />
                                <Button disabled={saving() || !textDraft().trim()} type="submit">
                                    发布留言
                                </Button>
                            </div>
                        </form>
                    </main>

                    <aside class="rounded-lg border border-border/70 bg-background/80 p-4">
                        <div class="space-y-3">
                            <div>
                                <h2 class="text-base font-semibold">发布工具</h2>
                                <p class="text-sm text-muted-foreground">只分享已存在且可用的资源。</p>
                            </div>
                            <div class="grid grid-cols-2 gap-2">
                                <Button
                                    variant={publishMode() === "STUDY_PLAN" ? "default" : "outline"}
                                    onClick={() => setPublishMode("STUDY_PLAN")}
                                >
                                    学习计划
                                </Button>
                                <Button
                                    variant={publishMode() === "VIDEO" ? "default" : "outline"}
                                    onClick={() => setPublishMode("VIDEO")}
                                >
                                    视频
                                </Button>
                            </div>

                            <Show when={publishMode() === "STUDY_PLAN"}>
                                <div class="space-y-3">
                                    <Label for="study-plan-share-select">选择已发布学习计划</Label>
                                    <select
                                        id="study-plan-share-select"
                                        aria-label="选择已发布学习计划"
                                        class="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                                        value={selectedStudyPlanId()}
                                        onChange={(event) => setSelectedStudyPlanId(event.currentTarget.value)}
                                    >
                                        <For each={availableStudyPlans()}>
                                            {(plan) => <option value={String(plan.id)}>{plan.name}</option>}
                                        </For>
                                    </select>
                                    <Show
                                        when={selectedStudyPlan()}
                                        fallback={<p class="text-sm text-muted-foreground">没有可分享的已发布学习计划。</p>}
                                    >
                                        {(plan) => (
                                            <div class="rounded-md border border-border/70 bg-muted/30 p-3 text-sm">
                                                <p class="font-semibold">{plan().name}</p>
                                                <p class="mt-1 text-muted-foreground">
                                                    {plan().dictionaryName} · 每日新词 {plan().dailyNewCount}
                                                </p>
                                                <p class="mt-1 text-muted-foreground">
                                                    {plan().startDate} 至 {plan().endDate ?? "长期"} · 截止 {plan().dailyDeadlineTime}
                                                </p>
                                            </div>
                                        )}
                                    </Show>
                                    <Button
                                        class="w-full"
                                        disabled={saving() || !selectedStudyPlanId()}
                                        onClick={() => void shareStudyPlan()}
                                    >
                                        分享学习计划
                                    </Button>
                                </div>
                            </Show>

                            <Show when={publishMode() === "VIDEO"}>
                                <div class="space-y-3">
                                    <Label for="video-share-select">选择云端可播视频</Label>
                                    <select
                                        id="video-share-select"
                                        class="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                                        value={selectedVideoId()}
                                        onChange={(event) => setSelectedVideoId(event.currentTarget.value)}
                                    >
                                        <Show when={availableVideos().length === 0}>
                                            <option value="">暂无可分享视频</option>
                                        </Show>
                                        <For each={availableVideos()}>
                                            {(video) => <option value={String(video.id)}>{video.title}</option>}
                                        </For>
                                        <For each={unavailableVideos()}>
                                            {(video) => (
                                                <option disabled value={`unavailable-${video.id}`}>
                                                    {video.title}（{videoAvailabilitySummary(video)}）
                                                </option>
                                            )}
                                        </For>
                                    </select>
                                    <Show
                                        when={selectedVideo()}
                                        fallback={<p class="text-sm text-muted-foreground">没有可分享的云端可播视频。</p>}
                                    >
                                        {(video) => (
                                            <div class="rounded-md border border-border/70 bg-muted/30 p-3 text-sm">
                                                <p class="font-semibold">{video().title}</p>
                                                <p class="mt-1 text-muted-foreground">
                                                    {video().status} · {video().cloudPublishStatus}
                                                </p>
                                            </div>
                                        )}
                                    </Show>
                                    <Show when={unavailableVideos().length > 0}>
                                        <div class="space-y-2 rounded-md border border-border/70 bg-muted/30 p-3 text-sm">
                                            <p class="font-semibold">暂不可分享的视频</p>
                                            <For each={unavailableVideos()}>
                                                {(video) => (
                                                    <p class="text-muted-foreground">
                                                        {video.title} · {videoAvailabilitySummary(video)}
                                                    </p>
                                                )}
                                            </For>
                                            <p class="text-xs text-muted-foreground">
                                                视频需要同步到可预览并启用云端播放后，才能分享到班级。
                                            </p>
                                        </div>
                                    </Show>
                                    <Button
                                        class="w-full"
                                        disabled={saving() || !selectedVideoId()}
                                        onClick={() => void shareVideo()}
                                    >
                                        分享视频
                                    </Button>
                                </div>
                            </Show>
                        </div>
                    </aside>
                </div>
            </Show>
        </section>
    );
}
