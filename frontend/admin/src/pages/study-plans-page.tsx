import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { SearchableDictionarySelect } from "@/components/study-plans/searchable-dictionary-select";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { formatDate, formatPercent } from "@/lib/format";
import type {
    ClassroomResponse,
    Dictionary,
    StudyPlanOverviewResponse,
    StudyPlanResponse,
    StudyPlanStudentSummaryResponse,
} from "@/types/api";

interface StudyPlansPageData {
    plans: StudyPlanResponse[];
    classrooms: ClassroomResponse[];
}

const dictionaryClassroomMismatchMessage = "所选词书未分配给全部班级，请重新选择。";
const reviewIntervalsStartMessage = "复习间隔必须从 0 开始，例如：0,1,3,7,14。";

const toStudyPlanCreateError = (error: unknown) => {
    const message = error instanceof Error ? error.message : "创建学习计划失败，请稍后重试。";
    if (message.includes("dictionaryId is not associated with all selected classrooms")) {
        return dictionaryClassroomMismatchMessage;
    }
    if (message.includes("reviewIntervals must start with 0")) {
        return reviewIntervalsStartMessage;
    }
    return message;
};

export function StudyPlansPage() {
    const auth = useAuth();
    const [feedback, setFeedback] = createSignal("");
    const [createError, setCreateError] = createSignal("");
    const [creating, setCreating] = createSignal(false);
    const [selectedPlanId, setSelectedPlanId] = createSignal<number | null>(null);
    const [form, setForm] = createStore({
        name: "",
        description: "",
        dictionaryId: "",
        classroomIds: [] as number[],
        startDate: new Date().toISOString().slice(0, 10),
        endDate: "",
        timezone: "Asia/Shanghai",
        dailyNewCount: "20",
        dailyReviewLimit: "60",
        reviewMode: "FIXED_INTERVAL",
        reviewIntervals: "0,1,3,7,14",
        completionThreshold: "85",
        dailyDeadlineTime: "21:00",
        attentionTrackingEnabled: true,
        minFocusSecondsPerWord: "2",
        maxFocusSecondsPerWord: "18",
        longStayWarningSeconds: "25",
        idleTimeoutSeconds: "12",
    });

    const [pageData, { refetch }] = createResource(
        () => auth.user(),
        async (user): Promise<StudyPlansPageData | null> => {
            if (!user) {
                return null;
            }
            const [plans, classrooms] = await Promise.all([
                api.listStudyPlans(),
                api.listClassrooms(),
            ]);
            if (!selectedPlanId() && plans.length > 0) {
                setSelectedPlanId(plans[0].id);
            }
            return { plans, classrooms };
        },
    );

    const [availableDictionaries] = createResource(
        () => [...form.classroomIds],
        async (classroomIds): Promise<Dictionary[]> => {
            try {
                const dictionaries = await api.listDictionaries(classroomIds);
                if (form.dictionaryId && !dictionaries.some(
                    (dictionary) => String(dictionary.id) === form.dictionaryId,
                )) {
                    setForm("dictionaryId", "");
                    setCreateError("所选词书不适用于当前班级，请重新选择共同可用的词书。");
                }
                return dictionaries;
            } catch (error) {
                setCreateError(toStudyPlanCreateError(error));
                return [];
            }
        },
    );

    const [planInsights, { refetch: refetchInsights }] = createResource(
        selectedPlanId,
        async (planId): Promise<{ overview: StudyPlanOverviewResponse; students: StudyPlanStudentSummaryResponse[] } | null> => {
            if (!planId) {
                return null;
            }
            const [overview, students] = await Promise.all([
                api.getStudyPlanOverview(planId),
                api.getStudyPlanStudents(planId),
            ]);
            return { overview, students };
        },
    );

    const selectedPlan = createMemo(() => pageData()?.plans.find((plan) => plan.id === selectedPlanId()) ?? null);

    const mutateWithRefetch = async (runner: () => Promise<unknown>, successMessage: string) => {
        setFeedback("");
        await runner();
        setFeedback(successMessage);
        await refetch();
        if (selectedPlanId()) {
            await refetchInsights();
        }
    };

    const handleToggleClassroom = (classroomId: number, checked: boolean) => {
        setForm(
            "classroomIds",
            checked
                ? [...form.classroomIds, classroomId]
                : form.classroomIds.filter((item) => item !== classroomId),
        );
    };

    const handleCreate = async (event: SubmitEvent) => {
        event.preventDefault();
        setFeedback("");
        setCreateError("");
        const reviewIntervals = form.reviewIntervals
            .split(",")
            .map((item) => Number(item.trim()))
            .filter((value) => !Number.isNaN(value));
        if (reviewIntervals[0] !== 0) {
            setCreateError(reviewIntervalsStartMessage);
            return;
        }
        setCreating(true);
        try {
            const created = await api.createStudyPlan({
                name: form.name.trim(),
                description: form.description.trim() || undefined,
                dictionaryId: Number(form.dictionaryId),
                classroomIds: form.classroomIds,
                startDate: form.startDate,
                endDate: form.endDate || null,
                timezone: form.timezone,
                dailyNewCount: Number(form.dailyNewCount),
                dailyReviewLimit: Number(form.dailyReviewLimit),
                reviewMode: form.reviewMode,
                reviewIntervals,
                completionThreshold: Number(form.completionThreshold),
                dailyDeadlineTime: form.dailyDeadlineTime,
                attentionTrackingEnabled: form.attentionTrackingEnabled,
                minFocusSecondsPerWord: Number(form.minFocusSecondsPerWord),
                maxFocusSecondsPerWord: Number(form.maxFocusSecondsPerWord),
                longStayWarningSeconds: Number(form.longStayWarningSeconds),
                idleTimeoutSeconds: Number(form.idleTimeoutSeconds),
            });
            setFeedback("学习计划已创建。");
            setSelectedPlanId(created.id);
            await refetch();
            await refetchInsights();
        } catch (error) {
            setCreateError(toStudyPlanCreateError(error));
        } finally {
            setCreating(false);
        }
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Study Plans"
                title="学习计划"
                description="从词书和班级交集生成学习计划，再集中查看当天完成率与注意力分布。"
                actions={
                    <Button variant="outline" onClick={() => void refetch()}>
                        刷新
                    </Button>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show
                when={pageData()}
                fallback={
                    <Card>
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载计划数据...</CardContent>
                    </Card>
                }
            >
                {(data) => (
                    <div class="space-y-6">
                        <Card>
                            <CardHeader>
                                <CardTitle>创建学习计划</CardTitle>
                                <CardDescription>按班级交集筛出可分发的词书，然后生成统一学习编排。</CardDescription>
                            </CardHeader>
                            <CardContent>
                                <form class="grid gap-5" onSubmit={handleCreate}>
                                    <div class="grid gap-4 md:grid-cols-2">
                                        <div class="space-y-2">
                                            <Label for="study-plan-name">计划名称</Label>
                                            <Input
                                                id="study-plan-name"
                                                value={form.name}
                                                onInput={(event) => setForm("name", event.currentTarget.value)}
                                            />
                                        </div>
                                        <div class="space-y-2">
                                            <Label for="study-plan-dictionary">词书</Label>
                                            <SearchableDictionarySelect
                                                id="study-plan-dictionary"
                                                dictionaries={availableDictionaries() ?? []}
                                                value={form.dictionaryId}
                                                onChange={(value) => {
                                                    setCreateError("");
                                                    setForm("dictionaryId", value);
                                                }}
                                            />
                                        </div>
                                    </div>

                                    <div class="space-y-2">
                                        <Label>计划描述</Label>
                                        <Textarea value={form.description} onInput={(event) => setForm("description", event.currentTarget.value)} />
                                    </div>

                                    <div class="space-y-3">
                                        <Label>作用班级</Label>
                                        <Show
                                            when={data().classrooms.length > 0}
                                            fallback={
                                                <EmptyState
                                                    title="暂无可用班级"
                                                    description="请先在“班级管理”中创建班级，再回来创建学习计划。"
                                                    actions={
                                                        <a
                                                            class="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all duration-200 hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                                                            href="/classrooms"
                                                        >
                                                            去班级管理
                                                        </a>
                                                    }
                                                />
                                            }
                                        >
                                            <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                                                <For each={data().classrooms}>
                                                    {(classroom) => (
                                                        <label class="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/60 px-4 py-3 text-sm">
                                                            <input
                                                                checked={form.classroomIds.includes(classroom.id)}
                                                                type="checkbox"
                                                                onChange={(event) =>
                                                                    handleToggleClassroom(classroom.id, event.currentTarget.checked)
                                                                }
                                                            />
                                                            <span>{classroom.name}</span>
                                                        </label>
                                                    )}
                                                </For>
                                            </div>
                                        </Show>
                                    </div>

                                    <div class="grid gap-4 md:grid-cols-4">
                                        <div class="space-y-2">
                                            <Label>开始日期</Label>
                                            <Input type="date" value={form.startDate} onInput={(event) => setForm("startDate", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>结束日期</Label>
                                            <Input type="date" value={form.endDate} onInput={(event) => setForm("endDate", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>每日新词</Label>
                                            <Input value={form.dailyNewCount} onInput={(event) => setForm("dailyNewCount", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>每日复习上限</Label>
                                            <Input value={form.dailyReviewLimit} onInput={(event) => setForm("dailyReviewLimit", event.currentTarget.value)} />
                                        </div>
                                    </div>

                                    <div class="grid gap-4 md:grid-cols-4">
                                        <div class="space-y-2">
                                            <Label>复习模式</Label>
                                            <select
                                                class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                value={form.reviewMode}
                                                onChange={(event) => setForm("reviewMode", event.currentTarget.value)}
                                            >
                                                <option value="FIXED_INTERVAL">固定间隔</option>
                                                <option value="EBBINGHAUS">艾宾浩斯</option>
                                                <option value="CUSTOM">自定义</option>
                                            </select>
                                        </div>
                                        <div class="space-y-2">
                                            <Label for="study-plan-review-intervals">复习间隔</Label>
                                            <Input
                                                id="study-plan-review-intervals"
                                                value={form.reviewIntervals}
                                                onInput={(event) => setForm("reviewIntervals", event.currentTarget.value)}
                                            />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>完成阈值%</Label>
                                            <Input value={form.completionThreshold} onInput={(event) => setForm("completionThreshold", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>每日截止时间</Label>
                                            <Input type="time" value={form.dailyDeadlineTime} onInput={(event) => setForm("dailyDeadlineTime", event.currentTarget.value)} />
                                        </div>
                                    </div>

                                    <div class="grid gap-4 md:grid-cols-4">
                                        <div class="space-y-2">
                                            <Label>最短专注秒数</Label>
                                            <Input value={form.minFocusSecondsPerWord} onInput={(event) => setForm("minFocusSecondsPerWord", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>最长专注秒数</Label>
                                            <Input value={form.maxFocusSecondsPerWord} onInput={(event) => setForm("maxFocusSecondsPerWord", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>停留预警秒数</Label>
                                            <Input value={form.longStayWarningSeconds} onInput={(event) => setForm("longStayWarningSeconds", event.currentTarget.value)} />
                                        </div>
                                        <div class="space-y-2">
                                            <Label>空闲超时秒数</Label>
                                            <Input value={form.idleTimeoutSeconds} onInput={(event) => setForm("idleTimeoutSeconds", event.currentTarget.value)} />
                                        </div>
                                    </div>

                                    <label class="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/60 px-4 py-3 text-sm">
                                        <input
                                            checked={form.attentionTrackingEnabled}
                                            type="checkbox"
                                            onChange={(event) => setForm("attentionTrackingEnabled", event.currentTarget.checked)}
                                        />
                                        启用注意力追踪
                                    </label>

                                    <Show when={createError()}>
                                        <Alert class="border-destructive/30 bg-destructive/10 text-destructive">
                                            {createError()}
                                        </Alert>
                                    </Show>

                                    <Button
                                        class="w-full md:w-auto"
                                        disabled={
                                            form.classroomIds.length === 0 ||
                                            !form.name.trim() ||
                                            !form.dictionaryId ||
                                            availableDictionaries.loading ||
                                            creating()
                                        }
                                        type="submit"
                                    >
                                        {creating() ? "正在创建..." : "创建计划"}
                                    </Button>
                                </form>
                            </CardContent>
                        </Card>

                        <div class="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
                            <Card>
                                <CardHeader>
                                    <CardTitle>计划列表</CardTitle>
                                    <CardDescription>点击任意计划查看当日概览，并可直接发布。</CardDescription>
                                </CardHeader>
                                <CardContent class="space-y-3">
                                    <Show when={data().plans.length > 0} fallback={<EmptyState title="暂无学习计划" description="创建第一份计划后，这里会成为日常编排入口。" />}>
                                        <For each={data().plans}>
                                            {(plan) => (
                                                <button
                                                    class={`w-full rounded-2xl border px-4 py-4 text-left transition ${selectedPlanId() === plan.id ? "border-primary bg-primary/5" : "border-border/70 bg-background/60 hover:border-primary/40"}`}
                                                    onClick={() => setSelectedPlanId(plan.id)}
                                                >
                                                    <div class="flex items-start justify-between gap-4">
                                                        <div>
                                                            <p class="font-medium text-foreground">{plan.name}</p>
                                                            <p class="mt-1 text-sm text-muted-foreground">{plan.dictionaryName}</p>
                                                        </div>
                                                        <Badge variant={plan.status === "PUBLISHED" ? "success" : "outline"}>
                                                            {plan.status}
                                                        </Badge>
                                                    </div>
                                                    <div class="mt-4 flex flex-wrap gap-3 text-xs uppercase tracking-[0.18em] text-muted-foreground">
                                                        <span>{formatDate(plan.startDate)}</span>
                                                        <span>{plan.studentCount} students</span>
                                                        <span>{plan.dailyNewCount} new/day</span>
                                                    </div>
                                                </button>
                                            )}
                                        </For>
                                    </Show>
                                </CardContent>
                            </Card>

                            <Card>
                                <CardHeader>
                                    <div class="flex items-start justify-between gap-4">
                                        <div>
                                            <CardTitle>{selectedPlan()?.name || "选择一个计划"}</CardTitle>
                                            <CardDescription>
                                                {selectedPlan()
                                                    ? `${selectedPlan()?.dictionaryName} · ${selectedPlan()?.studentCount} 名学生`
                                                    : "右侧会显示当日完成率和学生明细。"}
                                            </CardDescription>
                                        </div>
                                        <Show when={selectedPlan() && selectedPlan()?.status !== "PUBLISHED"}>
                                            <Button
                                                onClick={() =>
                                                    selectedPlan() &&
                                                    void mutateWithRefetch(
                                                        () => api.publishStudyPlan(selectedPlan()!.id),
                                                        "学习计划已发布。",
                                                    )
                                                }
                                            >
                                                发布计划
                                            </Button>
                                        </Show>
                                    </div>
                                </CardHeader>
                                <CardContent class="space-y-5">
                                    <Show when={planInsights()} fallback={<p class="text-sm text-muted-foreground">请选择一份学习计划查看概览。</p>}>
                                        {(insights) => (
                                            <>
                                                <div class="grid gap-4 md:grid-cols-2">
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">完成率</p>
                                                        <p class="mt-2 font-display text-3xl font-semibold">
                                                            {formatPercent(insights().overview.averageCompletionRate)}
                                                        </p>
                                                        <Progress class="mt-4" value={Number(insights().overview.averageCompletionRate || 0)} />
                                                    </div>
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">注意力得分</p>
                                                        <p class="mt-2 font-display text-3xl font-semibold">
                                                            {formatPercent(insights().overview.averageAttentionScore)}
                                                        </p>
                                                        <Progress class="mt-4" value={Number(insights().overview.averageAttentionScore || 0)} />
                                                    </div>
                                                </div>

                                                <div class="grid gap-4 md:grid-cols-4">
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                                        已完成：<span class="font-medium">{insights().overview.completedStudents}</span>
                                                    </div>
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                                        进行中：<span class="font-medium">{insights().overview.inProgressStudents}</span>
                                                    </div>
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                                        未开始：<span class="font-medium">{insights().overview.notStartedStudents}</span>
                                                    </div>
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4 text-sm">
                                                        已遗漏：<span class="font-medium">{insights().overview.missedStudents}</span>
                                                    </div>
                                                </div>

                                                <div class="space-y-3">
                                                    <p class="text-sm font-medium text-foreground">学生明细</p>
                                                    <Show when={insights().students.length > 0} fallback={<EmptyState title="暂无学生任务" description="计划发布后，学生会出现在这里。" />}>
                                                        <div class="space-y-3">
                                                            <For each={insights().students}>
                                                                {(student) => (
                                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                                        <div class="flex flex-wrap items-center justify-between gap-4">
                                                                            <div>
                                                                                <p class="font-medium text-foreground">{student.studentName}</p>
                                                                                <p class="mt-1 text-sm text-muted-foreground">
                                                                                    今日完成 {student.completedCount}/{student.totalTaskCount}
                                                                                </p>
                                                                            </div>
                                                                            <div class="text-right">
                                                                                <Badge variant={student.todayStatus === "COMPLETED" ? "success" : "outline"}>
                                                                                    {student.todayStatus}
                                                                                </Badge>
                                                                                <p class="mt-2 text-sm text-muted-foreground">
                                                                                    注意力 {formatPercent(student.attentionScore)}
                                                                                </p>
                                                                            </div>
                                                                        </div>
                                                                        <Progress class="mt-4" value={Number(student.completionRate || 0)} />
                                                                    </div>
                                                                )}
                                                            </For>
                                                        </div>
                                                    </Show>
                                                </div>
                                            </>
                                        )}
                                    </Show>
                                </CardContent>
                            </Card>
                        </div>
                    </div>
                )}
            </Show>
        </section>
    );
}
