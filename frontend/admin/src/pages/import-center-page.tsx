import { createEffect, createMemo, createResource, createSignal, For, onCleanup, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRoot,
    TableRow,
} from "@/components/ui/table";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { formatDateTime, formatNumber } from "@/lib/format";
import type {
    BooksImportBatchFileResponse,
    BooksImportConflictResponse,
    BooksImportJobResponse,
    BooksImportJobStatus,
    PaginatedResponse,
} from "@/types/api";

const RUNNING_STATUSES = new Set<BooksImportJobStatus>(["PENDING", "SCANNING", "STAGING", "AUTO_MERGING", "PUBLISHING"]);
const TASK_PAGE_SIZE = 10;
const FILE_PAGE_SIZE = 20;

const jobStatusLabel: Record<BooksImportJobStatus, string> = {
    PENDING: "等待开始",
    SCANNING: "扫描文件中",
    STAGING: "导入暂存中",
    STAGED: "暂存完成",
    AUTO_MERGING: "自动合并中",
    WAITING_REVIEW: "部分已发布，仍有冲突",
    READY_TO_PUBLISH: "可以发布",
    PUBLISHING: "发布中",
    SUCCEEDED: "已完成",
    FAILED: "失败",
    CANCELLED: "已取消",
    DISCARDED: "已丢弃",
};

const fileStatusLabel: Record<string, string> = {
    PENDING: "等待处理",
    STAGING: "导入中",
    STAGED: "已暂存",
    FAILED: "失败",
};

const clampPercent = (value: number) => Math.max(0, Math.min(100, value));

const calcPercent = (completed?: number | null, total?: number | null) => {
    if (!total || total <= 0) {
        return 0;
    }
    return clampPercent((Number(completed ?? 0) / Number(total)) * 100);
};

const isAutoRefreshStatus = (status?: BooksImportJobStatus | null) => Boolean(status && RUNNING_STATUSES.has(status));
const canDeleteBatch = (status?: BooksImportJobStatus | null) => Boolean(status && !RUNNING_STATUSES.has(status));

const jobBadgeVariant = (
    status?: BooksImportJobStatus | null,
): "warning" | "success" | "destructive" | "outline" => {
    switch (status) {
        case "PENDING":
        case "SCANNING":
        case "STAGING":
        case "AUTO_MERGING":
        case "PUBLISHING":
            return "warning";
        case "SUCCEEDED":
        case "READY_TO_PUBLISH":
            return "success";
        case "FAILED":
        case "CANCELLED":
        case "DISCARDED":
            return "destructive";
        default:
            return "outline";
    }
};

const batchActionDescription = (job?: BooksImportJobResponse | null) => {
    if (!job) {
        return "点击“开始导入”后，系统会扫描 books 目录并在本页显示进度。";
    }

    switch (job.status) {
        case "PENDING":
        case "SCANNING":
        case "STAGING":
            return "系统正在读取 books 目录并把数据写入暂存区，页面会自动刷新。";
        case "STAGED":
            return "文件已经完成暂存，系统会自动进入冲突检查和后续发布流程。";
        case "AUTO_MERGING":
            return "系统正在自动检查冲突，并会把不受影响的数据继续发布到正式辞书。";
        case "WAITING_REVIEW":
            return "不受影响的辞书、元单词和关联已经入库；剩余冲突词可以后续再处理并补发布。";
        case "READY_TO_PUBLISH":
            return "当前批次已经准备好，可以手动再发布一次，把最新确认的数据补进正式辞书。";
        case "PUBLISHING":
            return "系统正在把暂存数据发布到正式辞书，页面会自动刷新。";
        case "SUCCEEDED":
            return "本次导入已经完成，可以查看文件结果和冲突摘要。";
        case "FAILED":
            return "本次导入失败，请先检查错误信息，再决定是否重新导入。";
        case "CANCELLED":
        case "DISCARDED":
            return "当前批次已经结束，不会再继续处理。";
        default:
            return "页面会持续展示批次状态、文件结果和冲突情况。";
    }
};

export function ImportCenterPage() {
    const auth = useAuth();
    const [feedback, setFeedback] = createSignal("");
    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");
    const [selectedBatchId, setSelectedBatchId] = createSignal<string | null>(null);
    const [taskPage, setTaskPage] = createSignal(1);
    const [filePage, setFilePage] = createSignal(1);
    const [lastRefreshedAt, setLastRefreshedAt] = createSignal<string | null>(null);

    const [latestBatch, { refetch: refetchLatest }] = createResource(
        () => auth.user(),
        async (user) => {
            if (!user || user.role !== "ADMIN") {
                return null;
            }
            const batch = await api.getLatestImportBatch().catch(() => null);
            if (batch && !selectedBatchId()) {
                setSelectedBatchId(batch.jobId);
            }
            return batch;
        },
    );

    const [batchTasksPage, { refetch: refetchTasksPage }] = createResource(
        () => ({ user: auth.user(), page: taskPage() }),
        async ({ user, page }) => {
            if (!user || user.role !== "ADMIN") {
                return null;
            }
            const result = await api.listImportBatchesPage({ page, size: TASK_PAGE_SIZE });
            if (!selectedBatchId() && result.content.length > 0) {
                setSelectedBatchId(result.content[0].jobId);
            }
            return result;
        },
    );

    const [batchDetails, { refetch: refetchDetails }] = createResource(selectedBatchId, async (batchId) => {
        if (!batchId) {
            return null;
        }
        const [job, conflicts] = await Promise.all([
            api.getImportBatch(batchId),
            api.getImportBatchConflicts(batchId),
        ]);
        setLastRefreshedAt(new Date().toISOString());
        return { job, conflicts };
    });

    const filePageParams = createMemo(() => {
        const batchId = selectedBatchId();
        if (!batchId) {
            return null;
        }
        return {
            batchId,
            page: filePage(),
            size: FILE_PAGE_SIZE,
        };
    });

    const [batchFilesPage, { refetch: refetchFilesPage }] = createResource(
        filePageParams,
        async (params): Promise<PaginatedResponse<BooksImportBatchFileResponse> | null> => {
            if (!params) {
                return null;
            }
            return api.getImportBatchFilesPage(params.batchId, { page: params.page, size: params.size });
        },
    );

    const selectedTask = createMemo(
        () => batchTasksPage()?.content.find((job) => job.jobId === selectedBatchId()) ?? null,
    );
    const activeJob = createMemo(() => batchDetails()?.job ?? selectedTask() ?? latestBatch() ?? null);

    createEffect(() => {
        selectedBatchId();
        setFilePage(1);
    });

    createEffect(() => {
        const batchId = selectedBatchId();
        const status = activeJob()?.status;
        if (!batchId || !isAutoRefreshStatus(status)) {
            return;
        }

        const timer = window.setInterval(() => {
            void refetchLatest();
            void refetchTasksPage();
            void refetchDetails();
            void refetchFilesPage();
        }, 3000);

        onCleanup(() => window.clearInterval(timer));
    });

    const mutate = async (runner: () => Promise<BooksImportJobResponse>, successMessage: string) => {
        const batch = await runner();
        setFeedback(successMessage);
        setSelectedBatchId(batch.jobId);
        await refetchLatest();
        await refetchTasksPage();
        await refetchDetails();
        await refetchFilesPage();
    };

    const deleteBatch = async (batchId: string) => {
        const confirmed = window.confirm("删除后会移除这个导入任务，以及它发布的辞书数据。这个操作不能恢复，确认继续吗？");
        if (!confirmed) {
            return;
        }

        if (selectedBatchId() === batchId) {
            setSelectedBatchId(null);
        }

        await api.deleteImportBatch(batchId);
        setFeedback("导入任务已删除，对应导入数据已清理。");

        const tasks = await refetchTasksPage();
        if (taskPage() > 1 && (tasks?.content.length ?? 0) === 0) {
            setTaskPage((page) => Math.max(1, page - 1));
        }

        await refetchLatest();
        await refetchDetails();
        await refetchFilesPage();
    };

    const fileProgress = createMemo(() => calcPercent(activeJob()?.processedFiles, activeJob()?.totalFiles));
    const rowProgress = createMemo(() => calcPercent(activeJob()?.processedRows, activeJob()?.totalRows));
    const unresolvedConflicts = createMemo(
        () => batchDetails()?.conflicts.filter((item) => !item.resolution).length ?? 0,
    );
    const filePageSummary = createMemo(() => {
        const current = batchFilesPage();
        if (!current || current.totalElements === 0) {
            return "暂无文件";
        }
        const start = current.number * current.size + 1;
        const end = start + current.numberOfElements - 1;
        return `第 ${start}-${end} 条，共 ${current.totalElements} 个文件`;
    });
    const taskPageSummary = createMemo(() => {
        const current = batchTasksPage();
        if (!current || current.totalElements === 0) {
            return "暂无导入任务";
        }
        const start = current.number * current.size + 1;
        const end = start + current.numberOfElements - 1;
        return `第 ${start}-${end} 条，共 ${current.totalElements} 个任务`;
    });
    const taskTotalPages = createMemo(() => Math.max(1, batchTasksPage()?.totalPages ?? 1));
    const fileTotalPages = createMemo(() => Math.max(1, batchFilesPage()?.totalPages ?? 1));

    if (!isAdmin()) {
        return (
            <section class="space-y-6">
                <PageHeader eyebrow="Imports" title="辞书导入" description="该页面仅对管理员开放。" />
                <EmptyState title="没有访问权限" description="老师角色不参与辞书批量导入流程。" />
            </section>
        );
    }

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Dictionary Import"
                title="辞书导入"
                description="从 books 目录读取文件，查看导入进度。冲突词不会阻塞其余辞书、元单词和关联发布。"
                actions={
                    <div class="flex flex-wrap items-center gap-3">
                        <Button
                            variant="outline"
                            onClick={() => {
                                void refetchLatest();
                                void refetchTasksPage();
                                void refetchDetails();
                                void refetchFilesPage();
                            }}
                        >
                            刷新
                        </Button>
                        <Button onClick={() => void mutate(() => api.createImportBatch(), "已开始新的辞书导入。")}>
                            开始导入
                        </Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Card>
                <CardHeader>
                    <CardTitle>导入任务</CardTitle>
                    <CardDescription>每次开始导入都会生成一条任务记录。点击某一行，可以切换查看该批次的详情。</CardDescription>
                </CardHeader>
                <CardContent>
                    <Show
                        when={(batchTasksPage()?.content.length ?? 0) > 0}
                        fallback={<EmptyState title="暂无导入任务" description="点击右上角“开始导入”后，这里会按表格记录每次任务。" />}
                    >
                        <Table>
                            <TableRoot>
                                <TableHead>
                                    <tr>
                                        <TableHeaderCell>批次 ID</TableHeaderCell>
                                        <TableHeaderCell>创建时间</TableHeaderCell>
                                        <TableHeaderCell>状态</TableHeaderCell>
                                        <TableHeaderCell>文件进度</TableHeaderCell>
                                        <TableHeaderCell>数据进度</TableHeaderCell>
                                        <TableHeaderCell>冲突 / 错误</TableHeaderCell>
                                        <TableHeaderCell>操作</TableHeaderCell>
                                    </tr>
                                </TableHead>
                                <TableBody>
                                    <For each={batchTasksPage()?.content || []}>
                                        {(job: BooksImportJobResponse) => (
                                            <TableRow
                                                class={selectedBatchId() === job.jobId ? "cursor-pointer bg-primary/5" : "cursor-pointer hover:bg-muted/30"}
                                                onClick={() => setSelectedBatchId(job.jobId)}
                                            >
                                                <TableCell class="font-medium text-foreground">{job.jobId}</TableCell>
                                                <TableCell>{formatDateTime(job.createdAt)}</TableCell>
                                                <TableCell>
                                                    <Badge variant={jobBadgeVariant(job.status)}>
                                                        {jobStatusLabel[job.status]}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell>
                                                    {formatNumber(job.processedFiles)} / {formatNumber(job.totalFiles)}
                                                </TableCell>
                                                <TableCell>
                                                    {formatNumber(job.processedRows)} / {formatNumber(job.totalRows)}
                                                </TableCell>
                                                <TableCell class="max-w-[320px] text-sm text-muted-foreground">
                                                    {job.errorMessage
                                                        ? job.errorMessage
                                                        : `冲突 ${formatNumber(job.conflictCount)} 个`}
                                                </TableCell>
                                                <TableCell onClick={(event) => event.stopPropagation()}>
                                                    <Button
                                                        disabled={!canDeleteBatch(job.status)}
                                                        size="sm"
                                                        variant="destructive"
                                                        onClick={() => void deleteBatch(job.jobId)}
                                                    >
                                                        删除
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </For>
                                </TableBody>
                            </TableRoot>
                        </Table>
                        <div class="mt-5 flex flex-col gap-3 border-t border-border/60 pt-4 md:flex-row md:items-center md:justify-between">
                            <p class="text-sm text-muted-foreground">{taskPageSummary()}</p>
                            <div class="flex items-center gap-2">
                                <Button
                                    disabled={taskPage() === 1}
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setTaskPage((page) => Math.max(1, page - 1))}
                                >
                                    上一页
                                </Button>
                                <span class="min-w-[88px] text-center text-sm text-muted-foreground">
                                    {taskPage()} / {taskTotalPages()}
                                </span>
                                <Button
                                    disabled={taskPage() === taskTotalPages()}
                                    size="sm"
                                    variant="outline"
                                    onClick={() => setTaskPage((page) => Math.min(taskTotalPages(), page + 1))}
                                >
                                    下一页
                                </Button>
                            </div>
                        </div>
                    </Show>
                </CardContent>
            </Card>

            <div class="grid gap-6 xl:grid-cols-[0.78fr_1.22fr]">
                <Card>
                    <CardHeader>
                        <CardTitle>怎么使用</CardTitle>
                        <CardDescription>按这个顺序操作，导入过程就会更容易理解。</CardDescription>
                    </CardHeader>
                    <CardContent class="space-y-4">
                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                            <p class="text-sm font-medium text-foreground">1. 点击“开始导入”</p>
                            <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                系统会扫描服务器上的 `books` 目录，并自动创建一个新的辞书导入批次。
                            </p>
                        </div>
                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                            <p class="text-sm font-medium text-foreground">2. 等待页面自动刷新</p>
                            <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                扫描、暂存、自动合并、发布这些过程都会在右侧区域持续回显，不需要手工反复点刷新。
                            </p>
                        </div>
                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                            <p class="text-sm font-medium text-foreground">3. 有冲突也会先发布可用数据</p>
                            <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                如果只有一部分单词冲突，系统会先把不受影响的辞书、元单词和关联正常入库，不会整批卡住。
                            </p>
                        </div>
                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                            <p class="text-sm font-medium text-foreground">4. 剩余冲突词可以后续补齐</p>
                            <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                如果后面处理了冲突词，可以再点一次发布，把遗漏的元单词和辞书关系补进去。
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <div class="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <CardTitle>{activeJob()?.jobId || "当前批次"}</CardTitle>
                                <CardDescription>{batchActionDescription(activeJob())}</CardDescription>
                            </div>
                            <div class="flex flex-wrap gap-2">
                                <Show when={batchDetails()}>
                                    {(details) => (
                                        <>
                                            <Badge variant={jobBadgeVariant(details().job.status)}>
                                                {jobStatusLabel[details().job.status]}
                                            </Badge>
                                            <Show
                                                when={
                                                    details().job.status === "STAGED"
                                                    || details().job.status === "WAITING_REVIEW"
                                                    || details().job.status === "FAILED"
                                                }
                                            >
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() =>
                                                        void mutate(
                                                            () => api.autoMergeImportBatch(details().job.jobId),
                                                            "已开始自动合并。",
                                                        )
                                                    }
                                                >
                                                    自动合并
                                                </Button>
                                            </Show>
                                            <Show
                                                when={
                                                    details().job.status === "WAITING_REVIEW"
                                                    || details().job.status === "READY_TO_PUBLISH"
                                                }
                                            >
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() =>
                                                        void mutate(
                                                            () => api.publishImportBatch(details().job.jobId),
                                                            "已开始补发布可用数据。",
                                                        )
                                                    }
                                                >
                                                    重新发布
                                                </Button>
                                            </Show>
                                            <Button
                                                size="sm"
                                                variant="destructive"
                                                onClick={() =>
                                                    void mutate(
                                                        () => api.discardImportBatch(details().job.jobId),
                                                        "当前批次已丢弃。",
                                                    )
                                                }
                                            >
                                                放弃批次
                                            </Button>
                                        </>
                                    )}
                                </Show>
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent class="space-y-5">
                        <Show
                            when={activeJob()}
                            fallback={<EmptyState title="还没有导入批次" description="点击右上角“开始导入”即可发起一次辞书导入。" />}
                        >
                            <>
                                <Show when={isAutoRefreshStatus(activeJob()?.status)}>
                                    <Alert class="border-primary/20 bg-primary/8 text-foreground">
                                        当前批次正在处理中，页面每 3 秒自动刷新一次。
                                    </Alert>
                                </Show>

                                <div class="grid gap-4 md:grid-cols-4">
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">状态</p>
                                        <p class="mt-2 text-sm font-medium text-foreground">
                                            {activeJob()?.status ? jobStatusLabel[activeJob()!.status] : "未开始"}
                                        </p>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">当前文件</p>
                                        <p class="mt-2 text-sm font-medium text-foreground">
                                            {activeJob()?.currentFile || "暂无"}
                                        </p>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">最近刷新</p>
                                        <p class="mt-2 text-sm font-medium text-foreground">{formatDateTime(lastRefreshedAt())}</p>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">错误信息</p>
                                        <p class="mt-2 text-sm font-medium text-foreground">
                                            {activeJob()?.errorMessage || "无"}
                                        </p>
                                    </div>
                                </div>

                                <div class="grid gap-4 lg:grid-cols-2">
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                        <div class="flex items-center justify-between gap-3">
                                            <p class="text-sm font-medium text-foreground">文件处理进度</p>
                                            <p class="text-sm text-muted-foreground">
                                                {formatNumber(activeJob()?.processedFiles)} / {formatNumber(activeJob()?.totalFiles)}
                                            </p>
                                        </div>
                                        <Progress class="mt-3" value={fileProgress()} />
                                        <p class="mt-3 text-sm text-muted-foreground">
                                            失败文件：{formatNumber(activeJob()?.failedFiles)}
                                        </p>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                        <div class="flex items-center justify-between gap-3">
                                            <p class="text-sm font-medium text-foreground">数据处理进度</p>
                                            <p class="text-sm text-muted-foreground">
                                                {formatNumber(activeJob()?.processedRows)} / {formatNumber(activeJob()?.totalRows)}
                                            </p>
                                        </div>
                                        <Progress class="mt-3" value={rowProgress()} />
                                        <p class="mt-3 text-sm text-muted-foreground">
                                            成功 {formatNumber(activeJob()?.successRows)} 行，失败 {formatNumber(activeJob()?.failedRows)} 行
                                        </p>
                                    </div>
                                </div>

                                <div class="grid gap-4 md:grid-cols-4">
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                        导入词书：<span class="font-medium">{formatNumber(activeJob()?.importedDictionaryCount)}</span>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                        导入单词：<span class="font-medium">{formatNumber(activeJob()?.importedWordCount)}</span>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                        冲突数：<span class="font-medium">{formatNumber(activeJob()?.conflictCount)}</span>
                                    </div>
                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                        未解决冲突：<span class="font-medium">{formatNumber(unresolvedConflicts())}</span>
                                    </div>
                                </div>
                            </>
                        </Show>
                    </CardContent>
                </Card>
            </div>

            <div class="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
                <Card>
                    <CardHeader>
                        <CardTitle>文件明细</CardTitle>
                        <CardDescription>按页查看文件导入结果，并直接看到每个失败文件的原因。</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <Show
                            when={(batchFilesPage()?.content.length ?? 0) > 0}
                            fallback={<EmptyState title="暂无文件明细" description="开始导入后，文件处理结果会显示在这里。" />}
                        >
                            <Table>
                                <TableRoot>
                                    <TableHead>
                                        <tr>
                                            <TableHeaderCell>文件名</TableHeaderCell>
                                            <TableHeaderCell>词书名</TableHeaderCell>
                                            <TableHeaderCell>状态</TableHeaderCell>
                                            <TableHeaderCell>成功/失败</TableHeaderCell>
                                            <TableHeaderCell>失败原因</TableHeaderCell>
                                        </tr>
                                    </TableHead>
                                    <TableBody>
                                        <For each={batchFilesPage()?.content || []}>
                                            {(file: BooksImportBatchFileResponse) => (
                                                <TableRow>
                                                    <TableCell>{file.fileName}</TableCell>
                                                    <TableCell>{file.dictionaryName || "未识别"}</TableCell>
                                                    <TableCell>
                                                        <Badge variant={file.status === "FAILED" ? "destructive" : "outline"}>
                                                            {fileStatusLabel[file.status] || file.status}
                                                        </Badge>
                                                    </TableCell>
                                                    <TableCell>
                                                        {formatNumber(file.successRows)} / {formatNumber(file.failedRows)}
                                                    </TableCell>
                                                    <TableCell class="max-w-[320px] text-sm text-muted-foreground">
                                                        {file.errorMessage || (file.status === "FAILED" ? "系统未返回失败原因" : "-")}
                                                    </TableCell>
                                                </TableRow>
                                            )}
                                        </For>
                                    </TableBody>
                                </TableRoot>
                            </Table>
                            <div class="mt-5 flex flex-col gap-3 border-t border-border/60 pt-4 md:flex-row md:items-center md:justify-between">
                                <p class="text-sm text-muted-foreground">{filePageSummary()}</p>
                                <div class="flex items-center gap-2">
                                    <Button
                                        disabled={filePage() === 1}
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setFilePage((page) => Math.max(1, page - 1))}
                                    >
                                        上一页
                                    </Button>
                                    <span class="min-w-[88px] text-center text-sm text-muted-foreground">
                                        {filePage()} / {fileTotalPages()}
                                    </span>
                                    <Button
                                        disabled={filePage() === fileTotalPages()}
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setFilePage((page) => Math.min(fileTotalPages(), page + 1))}
                                    >
                                        下一页
                                    </Button>
                                </div>
                            </div>
                        </Show>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>冲突与发布</CardTitle>
                        <CardDescription>冲突词不会阻塞可用数据发布；这里只处理还没确认的少量问题词。</CardDescription>
                    </CardHeader>
                    <CardContent class="space-y-4">
                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                            <p class="text-sm font-medium text-foreground">当前结论</p>
                            <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                {unresolvedConflicts() > 0
                                    ? `还有 ${unresolvedConflicts()} 个冲突词未处理，但其他可发布的数据已经或将会继续入库。`
                                    : "当前没有未解决冲突，系统会继续自动推进后续发布。"}
                            </p>
                        </div>

                        <Show
                            when={(batchDetails()?.conflicts.length ?? 0) > 0}
                            fallback={<EmptyState title="没有冲突" description="如果导入过程没有发现冲突，这里会保持空白。" />}
                        >
                            <div class="space-y-3">
                                <For each={batchDetails()?.conflicts.slice(0, 12) || []}>
                                    {(conflict: BooksImportConflictResponse) => (
                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                            <div class="flex items-center justify-between gap-4">
                                                <div>
                                                    <p class="font-medium text-foreground">{conflict.displayWord}</p>
                                                    <p class="mt-1 text-sm text-muted-foreground">
                                                        {conflict.dictionaryNames.join(" / ")}
                                                    </p>
                                                </div>
                                                <Badge variant={conflict.resolution ? "success" : "warning"}>
                                                    {conflict.resolution || conflict.conflictType}
                                                </Badge>
                                            </div>
                                        </div>
                                    )}
                                </For>
                            </div>
                        </Show>
                    </CardContent>
                </Card>
            </div>
        </section>
    );
}
