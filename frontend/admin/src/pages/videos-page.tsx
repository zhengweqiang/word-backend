import { CloudDownload, X } from "lucide-solid";
import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { compactFileSize, formatDateTime } from "@/lib/format";
import type {
    PaginatedResponse,
    VideoAccessResponse,
    VideoCloudSyncResponse,
    VideoCloudPublishStatus,
    VideoResponse,
    VideoStatus,
} from "@/types/api";

const toHttpPreviewUrl = (url: string) => url.replace(/^https:/i, "http:");

const PAGE_SIZE = 12;

const statusLabel: Record<VideoStatus, string> = {
    PROCESSING: "处理中",
    READY: "可预览",
    FAILED: "失败",
};

const statusVariant = (status: VideoStatus): "warning" | "success" | "destructive" => {
    switch (status) {
        case "READY":
            return "success";
        case "FAILED":
            return "destructive";
        default:
            return "warning";
    }
};

const cloudPublishStatusLabel: Record<VideoCloudPublishStatus, string> = {
    UNPUBLISHED: "云端停用",
    PUBLISHED: "云端可播",
};

const cloudPublishStatusVariant = (cloudPublishStatus: VideoCloudPublishStatus): "outline" | "success" =>
    cloudPublishStatus === "PUBLISHED" ? "success" : "outline";

const createDefaultForm = () => ({
    title: "",
    description: "",
});

export function VideosPage() {
    const auth = useAuth();
    const [feedback, setFeedback] = createSignal("");
    const [error, setError] = createSignal("");
    const [page, setPage] = createSignal(1);
    const [keyword, setKeyword] = createSignal("");
    const [status, setStatus] = createSignal<VideoStatus | "">("");
    const [cloudPublishStatus, setCloudPublishStatus] = createSignal<VideoCloudPublishStatus | "">("");
    const [form, setForm] = createStore(createDefaultForm());
    const [selectedFile, setSelectedFile] = createSignal<File | null>(null);
    const [saving, setSaving] = createSignal(false);
    const [actionKey, setActionKey] = createSignal<string | null>(null);
    const [preview, setPreview] = createSignal<VideoAccessResponse | null>(null);
    let fileInputRef: HTMLInputElement | undefined;

    const isManager = createMemo(() => {
        const role = auth.user()?.role;
        return role === "ADMIN" || role === "TEACHER";
    });

    const queryParams = createMemo(() => {
        if (!isManager()) {
            return null;
        }
        return {
            page: page(),
            size: PAGE_SIZE,
            keyword: keyword().trim() || undefined,
            status: status() || undefined,
            cloudPublishStatus: cloudPublishStatus() || undefined,
        };
    });

    const [videosPage, { refetch }] = createResource(
        queryParams,
        async (params): Promise<PaginatedResponse<VideoResponse> | null> => {
            if (!params) {
                return null;
            }
            return api.listVideosPage(params);
        },
    );

    const pageSummary = createMemo(() => {
        const current = videosPage();
        if (!current || current.totalElements === 0) {
            return "暂无视频";
        }
        const start = current.number * current.size + 1;
        const end = start + current.numberOfElements - 1;
        return `第 ${start}-${end} 条，共 ${current.totalElements} 个视频`;
    });

    const totalPages = createMemo(() => Math.max(1, videosPage()?.totalPages ?? 1));

    const resetUploadForm = () => {
        setForm(createDefaultForm());
        setSelectedFile(null);
        if (fileInputRef) {
            fileInputRef.value = "";
        }
    };

    const handleFileChange = (event: Event) => {
        const input = event.currentTarget as HTMLInputElement;
        const file = input.files?.[0] ?? null;
        setSelectedFile(file);
        if (file && !form.title.trim()) {
            const name = file.name.includes(".") ? file.name.slice(0, file.name.lastIndexOf(".")) : file.name;
            setForm("title", name);
        }
    };

    const handleUpload = async (event: SubmitEvent) => {
        event.preventDefault();
        setFeedback("");
        setError("");

        if (!selectedFile()) {
            setError("请选择要上传的视频文件。");
            return;
        }

        setSaving(true);
        try {
            const formData = new FormData();
            formData.append("file", selectedFile()!);
            if (form.title.trim()) {
                formData.append("title", form.title.trim());
            }
            if (form.description.trim()) {
                formData.append("description", form.description.trim());
            }

            await api.uploadVideo(formData);
            setFeedback("视频已上传，列表已刷新。");
            resetUploadForm();
            setPage(1);
            await refetch();
        } catch (uploadError) {
            setError(uploadError instanceof Error ? uploadError.message : "上传视频失败");
        } finally {
            setSaving(false);
        }
    };

    const handlePreview = async (video: VideoResponse) => {
        setActionKey(`preview-${video.id}`);
        setFeedback("");
        setError("");

        try {
            let targetVideo = video;
            if (!targetVideo.canPreview && targetVideo.canManage) {
                targetVideo = await api.syncVideo(video.id);
            }
            if (!targetVideo.canPreview) {
                setError(`视频「${video.title}」仍在处理中，暂时不能预览。`);
                await refetch();
                return;
            }
            const access = await api.getVideoAccess(targetVideo.id);
            setPreview({ ...access, url: toHttpPreviewUrl(access.url) });
        } catch (previewError) {
            setError(previewError instanceof Error ? previewError.message : "获取预览地址失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleSync = async (video: VideoResponse) => {
        setActionKey(`sync-${video.id}`);
        setFeedback("");
        setError("");

        try {
            await api.syncVideo(video.id);
            setFeedback(`已同步视频「${video.title}」的云端状态。`);
            await refetch();
        } catch (syncError) {
            setError(syncError instanceof Error ? syncError.message : "同步视频状态失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleSyncCloud = async () => {
        setActionKey("sync-cloud");
        setFeedback("");
        setError("");

        try {
            const result: VideoCloudSyncResponse = await api.syncCloudVideos();
            setFeedback(`火山视频同步完成：扫描 ${result.scanned} 个，新增 ${result.imported} 个，更新 ${result.updated} 个。`);
            setPage(1);
            await refetch();
        } catch (syncError) {
            setError(syncError instanceof Error ? syncError.message : "同步火山视频失败");
        } finally {
            setActionKey(null);
        }
    };

    const handlePublish = async (video: VideoResponse) => {
        setActionKey(`publish-${video.id}`);
        setFeedback("");
        setError("");

        try {
            await api.publishVideo(video.id);
            setFeedback(`视频「${video.title}」已发布给学生。`);
            await refetch();
        } catch (publishError) {
            setError(publishError instanceof Error ? publishError.message : "发布视频失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleUnpublish = async (video: VideoResponse) => {
        setActionKey(`unpublish-${video.id}`);
        setFeedback("");
        setError("");

        try {
            await api.unpublishVideo(video.id);
            setFeedback(`视频「${video.title}」已下架。`);
            await refetch();
        } catch (unpublishError) {
            setError(unpublishError instanceof Error ? unpublishError.message : "下架视频失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleDelete = async (video: VideoResponse) => {
        if (!window.confirm(`确定删除视频「${video.title}」吗？云端媒体也会一并删除。`)) {
            return;
        }

        setActionKey(`delete-${video.id}`);
        setFeedback("");
        setError("");

        try {
            await api.deleteVideo(video.id);
            setFeedback(`视频「${video.title}」已删除。`);
            await refetch();
        } catch (deleteError) {
            setError(deleteError instanceof Error ? deleteError.message : "删除视频失败");
        } finally {
            setActionKey(null);
        }
    };

    if (!isManager()) {
        return (
            <section class="space-y-6">
                <PageHeader
                    eyebrow="Media"
                    title="视频资源"
                    description="该页面仅面向管理员与老师开放。"
                />
                <Card>
                    <CardContent class="p-6">
                        <EmptyState title="无访问权限" description="当前账号没有访问视频资源页面的权限。" />
                    </CardContent>
                </Card>
            </section>
        );
    }

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Media"
                title="视频资源"
                description="管理员和老师可以在这里上传教学视频、同步云端状态，并在后台直接预览可播放资源。"
                actions={
                    <div class="flex flex-wrap items-center gap-3">
                        <Show when={auth.user()?.role === "ADMIN"}>
                            <Button
                                variant="outline"
                                disabled={Boolean(actionKey())}
                                onClick={() => void handleSyncCloud()}
                            >
                                <CloudDownload class="h-4 w-4" />
                                {actionKey() === "sync-cloud" ? "同步中..." : "同步火山视频"}
                            </Button>
                        </Show>
                        <Button variant="outline" onClick={() => void refetch()}>
                            刷新
                        </Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show when={error()}>
                <Alert class="border-destructive/20 bg-destructive/10 text-destructive">{error()}</Alert>
            </Show>

            <div class="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
                <Card>
                    <CardHeader>
                        <CardTitle>上传视频</CardTitle>
                        <CardDescription>上传文件后会自动写入默认视频点播配置，并记录当前启用的默认配置。</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form class="space-y-4" onSubmit={(event) => void handleUpload(event)}>
                            <div class="space-y-2">
                                <Label for="video-title">标题</Label>
                                <Input
                                    id="video-title"
                                    value={form.title}
                                    onInput={(event) => setForm("title", event.currentTarget.value)}
                                    placeholder="例如 Unit 1 精讲"
                                />
                            </div>

                            <div class="space-y-2">
                                <Label for="video-description">简介</Label>
                                <Textarea
                                    id="video-description"
                                    rows={4}
                                    value={form.description}
                                    onInput={(event) => setForm("description", event.currentTarget.value)}
                                    placeholder="可选，简要说明视频内容"
                                />
                            </div>

                            <div class="space-y-2">
                                <Label for="video-file">视频文件</Label>
                                <Input
                                    id="video-file"
                                    ref={fileInputRef}
                                    type="file"
                                    accept="video/mp4,video/quicktime,video/x-m4v,video/x-msvideo,video/x-matroska,video/webm"
                                    onChange={handleFileChange}
                                />
                                <Show when={selectedFile()}>
                                    <p class="text-xs text-muted-foreground">
                                        已选择 {selectedFile()?.name} · {compactFileSize(selectedFile()?.size)}
                                    </p>
                                </Show>
                            </div>

                            <div class="flex flex-wrap justify-end gap-3">
                                <Button variant="outline" onClick={resetUploadForm}>
                                    清空
                                </Button>
                                <Button type="submit" disabled={saving()}>
                                    {saving() ? "上传中..." : "上传视频"}
                                </Button>
                            </div>
                        </form>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>视频库</CardTitle>
                        <CardDescription>支持按标题搜索和按状态筛选，分页展示当前可见的视频资源。</CardDescription>
                    </CardHeader>
                    <CardContent class="space-y-4">
                        <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_180px_180px]">
                            <Input
                                value={keyword()}
                                onInput={(event) => {
                                    setKeyword(event.currentTarget.value);
                                    setPage(1);
                                }}
                                placeholder="搜索标题或原文件名"
                            />
                            <select
                                class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                value={status()}
                                onChange={(event) => {
                                    setStatus(event.currentTarget.value as VideoStatus | "");
                                    setPage(1);
                                }}
                            >
                                <option value="">全部状态</option>
                                <option value="PROCESSING">处理中</option>
                                <option value="READY">可预览</option>
                                <option value="FAILED">失败</option>
                            </select>
                            <select
                                class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                value={cloudPublishStatus()}
                                onChange={(event) => {
                                    setCloudPublishStatus(event.currentTarget.value as VideoCloudPublishStatus | "");
                                    setPage(1);
                                }}
                            >
                                <option value="">全部云端播放状态</option>
                                <option value="UNPUBLISHED">云端停用</option>
                                <option value="PUBLISHED">云端可播</option>
                            </select>
                        </div>

                        <Show
                            when={videosPage()?.content.length}
                            fallback={<EmptyState title="暂无视频" description="上传第一条教学视频后，这里会显示最新的视频资源。" />}
                        >
                            <div class="grid gap-4 lg:grid-cols-2">
                                <For each={videosPage()?.content}>
                                    {(video) => (
                                        <Card class="border-border/70 bg-background/80">
                                            <CardContent class="space-y-4 p-5">
                                                <div class="flex items-start justify-between gap-4">
                                                    <div class="space-y-1">
                                                        <p class="font-medium text-foreground">{video.title}</p>
                                                        <p class="text-xs text-muted-foreground">{video.originalFileName}</p>
                                                    </div>
                                                    <div class="flex flex-wrap justify-end gap-2">
                                                        <Badge variant={statusVariant(video.status)}>
                                                            {statusLabel[video.status]}
                                                        </Badge>
                                                        <Badge variant={cloudPublishStatusVariant(video.cloudPublishStatus)}>
                                                            {cloudPublishStatusLabel[video.cloudPublishStatus]}
                                                        </Badge>
                                                    </div>
                                                </div>

                                                <Show when={video.description}>
                                                    <p class="text-sm leading-6 text-muted-foreground">{video.description}</p>
                                                </Show>

                                                <div class="grid gap-2 text-xs text-muted-foreground sm:grid-cols-2">
                                                    <p>上传人：{video.createdByDisplayName}</p>
                                                    <p>范围：{video.scopeType === "SYSTEM" ? "系统视频" : "教师视频"}</p>
                                                    <p>大小：{compactFileSize(video.fileSize)}</p>
                                                    <p>配置：{video.storageConfigName || `#${video.storageConfigId}`}</p>
                                                    <p>上传时间：{formatDateTime(video.createdAt)}</p>
                                                    <p>最近更新：{formatDateTime(video.updatedAt)}</p>
                                                    <Show when={video.publishedAt}>
                                                        <p>发布时间：{formatDateTime(video.publishedAt)}</p>
                                                    </Show>
                                                </div>

                                                <Show when={video.errorMessage}>
                                                    <p class="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">
                                                        {video.errorMessage}
                                                    </p>
                                                </Show>

                                                <div class="flex flex-wrap gap-2">
                                                    <Button
                                                        variant="outline"
                                                        size="sm"
                                                        disabled={(!video.canPreview && !video.canManage) || Boolean(actionKey())}
                                                        onClick={() => void handlePreview(video)}
                                                    >
                                                        {actionKey() === `preview-${video.id}` ? "读取中..." : "预览"}
                                                    </Button>
                                                    <Button
                                                        variant="outline"
                                                        size="sm"
                                                        disabled={!video.canManage}
                                                        onClick={() => void handleSync(video)}
                                                    >
                                                        {actionKey() === `sync-${video.id}` ? "同步中..." : "同步状态"}
                                                    </Button>
                                                    <Show
                                                        when={video.cloudPublishStatus === "PUBLISHED"}
                                                        fallback={
                                                            <Button
                                                                variant="outline"
                                                                size="sm"
                                                                disabled={!video.canManage || Boolean(actionKey())}
                                                                onClick={() => void handlePublish(video)}
                                                            >
                                                                {actionKey() === `publish-${video.id}` ? "启用中..." : "云端启用"}
                                                            </Button>
                                                        }
                                                    >
                                                        <Button
                                                            variant="outline"
                                                            size="sm"
                                                            disabled={!video.canManage || Boolean(actionKey())}
                                                            onClick={() => void handleUnpublish(video)}
                                                        >
                                                            {actionKey() === `unpublish-${video.id}` ? "停用中..." : "云端停用"}
                                                        </Button>
                                                    </Show>
                                                    <Button
                                                        variant="destructive"
                                                        size="sm"
                                                        disabled={!video.canManage}
                                                        onClick={() => void handleDelete(video)}
                                                    >
                                                        {actionKey() === `delete-${video.id}` ? "删除中..." : "删除"}
                                                    </Button>
                                                </div>
                                            </CardContent>
                                        </Card>
                                    )}
                                </For>
                            </div>
                        </Show>

                        <div class="flex flex-wrap items-center justify-between gap-3">
                            <p class="text-sm text-muted-foreground">{pageSummary()}</p>
                            <div class="flex items-center gap-2">
                                <Button
                                    variant="outline"
                                    disabled={page() === 1}
                                    onClick={() => setPage((current) => Math.max(1, current - 1))}
                                >
                                    上一页
                                </Button>
                                <span class="text-sm text-muted-foreground">
                                    第 {page()} / {totalPages()} 页
                                </span>
                                <Button
                                    variant="outline"
                                    disabled={page() >= totalPages()}
                                    onClick={() => setPage((current) => Math.min(totalPages(), current + 1))}
                                >
                                    下一页
                                </Button>
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <Show when={preview()}>
                <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/75 px-4 py-8">
                    <div class="w-full max-w-5xl rounded-[28px] border border-white/10 bg-slate-950 p-4 shadow-2xl">
                        <div class="mb-4 flex items-center justify-between gap-4">
                            <div>
                                <p class="text-sm uppercase tracking-[0.2em] text-white/55">Preview</p>
                                <p class="text-lg font-medium text-white">后台视频预览</p>
                            </div>
                            <Button
                                variant="outline"
                                class="border-white/15 bg-white/5 text-white hover:bg-white/10"
                                onClick={() => setPreview(null)}
                            >
                                <X class="h-4 w-4" />
                                关闭
                            </Button>
                        </div>
                        <video
                            class="max-h-[70vh] w-full rounded-2xl bg-black"
                            controls
                            autoplay
                            poster={preview()?.coverUrl ?? undefined}
                            src={preview()?.url}
                        />
                    </div>
                </div>
            </Show>
        </section>
    );
}
