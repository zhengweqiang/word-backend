import { A, useParams } from "@solidjs/router";
import { ArrowLeft, CheckCircle2, Clock3, Eye, RefreshCw } from "lucide-solid";
import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { AssessmentNav } from "@/components/assessments/assessment-nav";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import type { PaperReleaseQuestionStatResponse, PaperReleaseResponse, PaperReleaseResultOverviewResponse, PaperReleaseStatus, PaperReleaseStudentResultResponse, PaperResultVisibility, StudentPaperAttemptStatus } from "@/types/api";

const attemptLabels: Record<StudentPaperAttemptStatus, string> = { NOT_STARTED: "未开始", IN_PROGRESS: "作答中", OVERDUE: "已超时", SUBMITTED: "已提交", SUBMITTED_LATE: "超时提交", INVALIDATED: "已作废" };
const releaseStatusLabels: Record<PaperReleaseStatus, string> = {
    SCHEDULED: "待开始",
    OPEN: "进行中",
    WITHDRAWN: "已撤回",
    INVALIDATED: "已作废",
    SUPERSEDED: "已替换",
};

function studentDisplayName(student: Pick<PaperReleaseStudentResultResponse, "studentId" | "studentUsername">) {
    return student.studentUsername?.trim() || `学生 #${student.studentId}`;
}

export function PaperResultPage() {
    const auth = useAuth();
    const params = useParams<{ releaseId?: string }>();
    const releaseId = () => params.releaseId ? Number(params.releaseId) : null;
    const [releaseList, setReleaseList] = createSignal<PaperReleaseResponse[]>([]);
    const [release, setRelease] = createSignal<PaperReleaseResponse | null>(null);
    const [overview, setOverview] = createSignal<PaperReleaseResultOverviewResponse | null>(null);
    const [stats, setStats] = createSignal<PaperReleaseQuestionStatResponse[]>([]);
    const [detail, setDetail] = createSignal<PaperReleaseStudentResultResponse | null>(null);
    const [statusFilter, setStatusFilter] = createSignal("");
    const [visibility, setVisibility] = createSignal<PaperResultVisibility>("SCORE_ONLY");
    const [busy, setBusy] = createSignal(false);
    const [error, setError] = createSignal("");
    const [feedback, setFeedback] = createSignal("");
    const filteredStudents = createMemo(() => overview()?.students.filter((item) => !statusFilter() || item.status === statusFilter()) ?? []);
    const canReleaseResults = createMemo(() => auth.user()?.role === "ADMIN"
        || release()?.publishedByUserId === auth.user()?.id);

    const load = async () => {
        setError(""); const id = releaseId();
        try {
            if (!id) { setReleaseList(await api.listPaperReleases()); return; }
            const [summary, questionStats, releaseDetail] = await Promise.all([
                api.getPaperReleaseResults(id),
                api.getPaperReleaseQuestionStats(id),
                api.getPaperRelease(id),
            ]);
            setOverview(summary); setStats(questionStats); setRelease(releaseDetail);
            setVisibility(summary.resultVisibility === "HIDDEN_UNTIL_RELEASED" ? "SCORE_ONLY" : summary.resultVisibility);
        } catch (cause) { setError(cause instanceof Error ? cause.message : "加载结果失败。"); }
    };
    createEffect(() => { void load(); });
    const loadStudent = async (attemptId: number) => { const id = releaseId(); if (!id) return; setBusy(true); setError(""); try { setDetail(await api.getPaperReleaseStudentResult(id, attemptId)); } catch (cause) { setError(cause instanceof Error ? cause.message : "加载学生答卷失败。"); } finally { setBusy(false); } };
    const releaseResults = async () => { const id = releaseId(); if (!id || !window.confirm("确认向本次发布的学生开放结果？")) return; setBusy(true); try { setOverview(await api.releasePaperResults(id, { resultVisibility: visibility() })); setFeedback("结果可见性已发布。"); } catch (cause) { setError(cause instanceof Error ? cause.message : "发布结果失败。"); } finally { setBusy(false); } };

    return <section class="space-y-5"><AssessmentNav /><PageHeader eyebrow="RESULT REVIEW" title={overview()?.title ?? "试卷结果"} description="查看完成情况、超时状态、每题正确率和冻结答卷明细。" actions={<div class="flex gap-2"><Show when={releaseId()}><A class="inline-flex h-9 items-center gap-2 rounded-md border bg-background px-3 text-sm" href="/paper-results"><ArrowLeft class="h-4 w-4" />发布列表</A></Show><Button variant="outline" aria-label="刷新结果" onClick={() => void load()}><RefreshCw class="h-4 w-4" /></Button></div>} />
        <Show when={error()}><Alert class="border-destructive/30 bg-destructive/10 text-destructive">{error()}</Alert></Show><Show when={feedback()}><Alert class="border-success/30 bg-success/10 text-success">{feedback()}</Alert></Show>
        <Show when={!releaseId()}><div class="overflow-x-auto rounded-md border bg-background"><Show when={releaseList().length} fallback={<EmptyState title="暂无可查看结果" description="发布试卷后从这里进入结果审阅。" />}><table class="w-full min-w-[560px] text-left text-sm"><thead class="bg-muted"><tr><th class="px-3 py-2">发布</th><th class="px-3 py-2">状态</th><th class="px-3 py-2">人数</th><th class="px-3 py-2">操作</th></tr></thead><tbody><For each={releaseList()}>{(item) => <tr class="border-t"><td class="px-3 py-3">{item.title}</td><td class="px-3 py-3">{releaseStatusLabels[item.status]}</td><td class="px-3 py-3">{item.targets.length}</td><td class="px-3 py-3"><A class="text-primary underline" href={`/paper-results/${item.id}`}>查看结果</A></td></tr>}</For></tbody></table></Show></div></Show>
        <Show when={overview()}>{(summary) => <><div class="grid gap-3 sm:grid-cols-3 xl:grid-cols-6"><For each={[ ["已分配", summary().assignedCount], ["未开始", summary().notStartedCount], ["作答中", summary().inProgressCount], ["已超时", summary().overdueCount], ["按时提交", summary().submittedCount], ["超时提交", summary().submittedLateCount] ]}>{([label, value]) => <div class="rounded-md border bg-background p-3"><p class="text-xs text-muted-foreground">{label}</p><p class="text-xl font-semibold">{value}</p></div>}</For></div>
            <div class="flex flex-wrap items-end gap-3 rounded-md border bg-background p-4"><div class="space-y-2"><label for="student-status-filter" class="text-sm font-medium">学生状态</label><select id="student-status-filter" class="h-10 rounded-md border bg-background px-3 text-sm" value={statusFilter()} onChange={(e) => setStatusFilter(e.currentTarget.value)}><option value="">全部状态</option><For each={Object.entries(attemptLabels)}>{([value, label]) => <option value={value}>{label}</option>}</For></select></div><div class="ml-auto space-y-2"><label for="release-visibility" class="text-sm font-medium">开放范围</label><select id="release-visibility" class="h-10 rounded-md border bg-background px-3 text-sm" disabled={!canReleaseResults()} value={visibility()} onChange={(e) => setVisibility(e.currentTarget.value as PaperResultVisibility)}><option value="SCORE_ONLY">仅分数</option><option value="SCORE_AND_ANSWERS">分数与答案</option></select></div><Button disabled={busy() || !canReleaseResults()} onClick={() => void releaseResults()}><Eye class="h-4 w-4" />{summary().resultsReleased ? "更新可见范围" : "发布结果"}</Button></div>
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,0.8fr)]"><div class="space-y-4"><div class="overflow-x-auto rounded-md border bg-background"><table class="w-full min-w-[720px] text-left text-sm"><thead class="bg-muted"><tr><th class="px-3 py-2">学生</th><th class="px-3 py-2">状态</th><th class="px-3 py-2">作答</th><th class="px-3 py-2">得分</th><th class="px-3 py-2">提交时间</th><th class="px-3 py-2"></th></tr></thead><tbody><For each={filteredStudents()} fallback={<tr><td class="p-5 text-center text-muted-foreground" colSpan="6">没有匹配学生</td></tr>}>{(student) => <tr class="border-t"><td class="px-3 py-3">{studentDisplayName(student)}</td><td class="px-3 py-3"><Badge variant={student.late ? "destructive" : "outline"}>{attemptLabels[student.status]}</Badge></td><td class="px-3 py-3">{student.answeredCount}</td><td class="px-3 py-3">{student.earnedScore} / {student.totalScore}</td><td class="px-3 py-3 text-xs">{student.submittedAt || "-"}</td><td class="px-3 py-2"><Button size="sm" variant="ghost" aria-label={`查看学生 ${student.studentId}`} disabled={busy()} onClick={() => void loadStudent(student.attemptId)}>查看</Button></td></tr>}</For></tbody></table></div>
                <div class="overflow-x-auto rounded-md border bg-background"><div class="border-b bg-muted px-3 py-2 font-medium">每题正确率</div><table class="w-full min-w-[560px] text-left text-sm"><thead><tr><th class="px-3 py-2">题目</th><th class="px-3 py-2">提交</th><th class="px-3 py-2">正确</th><th class="px-3 py-2">正确率</th></tr></thead><tbody><For each={stats()}>{(stat) => <tr class="border-t"><td class="max-w-lg px-3 py-3">{stat.questionOrder}. {stat.stem}</td><td class="px-3 py-3">{stat.submissionCount}</td><td class="px-3 py-3">{stat.correctCount}</td><td class="px-3 py-3">{stat.correctnessRate}%</td></tr>}</For></tbody></table></div></div>
                <aside class="rounded-md border bg-background p-4"><Show when={detail()} fallback={<p class="text-sm text-muted-foreground">选择学生查看冻结答卷、正确答案和解析。</p>}>{(student) => <div class="space-y-4"><div class="flex items-center justify-between"><div><h2 class="font-semibold">{studentDisplayName(student())}</h2><p class="text-sm text-muted-foreground">{student().earnedScore} / {student().totalScore} 分</p></div><Show when={student().late} fallback={<CheckCircle2 class="h-5 w-5 text-success" />}><span class="inline-flex items-center gap-1 text-sm text-destructive"><Clock3 class="h-4 w-4" />超时提交</span></Show></div><For each={student().questions}>{(question) => <article class="space-y-2 rounded-md border p-3"><p class="font-medium">{question.questionOrder}. {question.stem}</p><For each={Object.entries(question.options ?? {})}>{([key, value]) => <p class="text-sm">{key}：{value}</p>}</For><p class="text-sm">学生答案：{[...question.selectedAnswers, ...question.blankAnswers].join("、") || "未作答"}</p><p class="text-sm font-medium text-success">正确答案：{question.acceptedAnswers.join("、")}</p><p class="text-xs text-muted-foreground">{question.explanation || "暂无解析"}</p></article>}</For></div>}</Show></aside>
            </div>
        </>}</Show>
    </section>;
}
