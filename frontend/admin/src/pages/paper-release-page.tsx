import { A } from "@solidjs/router";
import { AlertTriangle, Ban, Copy, RefreshCw, RotateCcw, Send, Undo2 } from "lucide-solid";
import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { AssessmentNav } from "@/components/assessments/assessment-nav";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { canPublishPaper, resolveTargetPreview } from "@/lib/assessment-rules";
import type {
    ClassroomResponse,
    PaperBlankAnswerPolicy,
    PaperReleaseResponse,
    PaperReleaseStatus,
    PaperResultVisibility,
    PaperTemplateResponse,
    UserResponse,
} from "@/types/api";

const statusLabels: Record<PaperReleaseStatus, string> = {
    SCHEDULED: "待开始",
    OPEN: "进行中",
    WITHDRAWN: "已撤回",
    INVALIDATED: "已作废",
    SUPERSEDED: "已替换",
};
const resultVisibilityLabels: Record<PaperResultVisibility, string> = {
    HIDDEN_UNTIL_RELEASED: "老师统一发布后可见",
    SCORE_ONLY: "仅分数",
    SCORE_AND_ANSWERS: "分数与答案",
};
const correctableStatuses: PaperReleaseStatus[] = ["SCHEDULED", "OPEN"];
const categoryText = (categories?: string[]) => categories?.filter(Boolean).join("、") || "-";

export function PaperReleasePage() {
    const auth = useAuth();
    const [papers, setPapers] = createSignal<PaperTemplateResponse[]>([]);
    const [classrooms, setClassrooms] = createSignal<ClassroomResponse[]>([]);
    const [classroomMembers, setClassroomMembers] = createSignal(new Map<number, UserResponse[]>());
    const [students, setStudents] = createSignal<UserResponse[]>([]);
    const [releases, setReleases] = createSignal<PaperReleaseResponse[]>([]);
    const [selectedReleaseId, setSelectedReleaseId] = createSignal<number | null>(null);
    const [reason, setReason] = createSignal("");
    const [showOriginal, setShowOriginal] = createSignal(false);
    const [form, setForm] = createStore({
        paperId: "",
        classroomIds: [] as number[],
        studentIds: [] as number[],
        startTime: "",
        deadline: "",
        blankAnswerPolicy: "ALLOW_BLANK" as PaperBlankAnswerPolicy,
        resultVisibility: "HIDDEN_UNTIL_RELEASED" as PaperResultVisibility,
    });
    const [busy, setBusy] = createSignal(false);
    const [copyingId, setCopyingId] = createSignal<number | null>(null);
    const [targetLoading, setTargetLoading] = createSignal(false);
    const [targetError, setTargetError] = createSignal("");
    const [error, setError] = createSignal("");
    const [feedback, setFeedback] = createSignal("");
    let targetRequest = 0;

    const selectedRelease = createMemo(() => releases().find((item) => item.id === selectedReleaseId()) ?? null);
    const publishablePapers = createMemo(() => {
        const user = auth.user();
        if (!user) return [];
        return papers().filter((paper) => canPublishPaper({
            role: user.role,
            userId: user.id,
            ownerUserId: paper.ownerUserId,
            status: paper.status,
        }));
    });
    const reusablePapers = createMemo(() => {
        const user = auth.user();
        if (!user || user.role === "ADMIN") return [];
        return papers().filter((paper) => paper.status === "READY" && paper.ownerUserId !== user.id);
    });
    const explicitStudents = createMemo(() => students().filter((student) => form.studentIds.includes(student.id)));
    const resolvedTargets = createMemo(() => resolveTargetPreview({
        classroomIds: form.classroomIds,
        classroomMembers: classroomMembers(),
        explicitStudents: explicitStudents(),
    }));
    const canCorrect = createMemo(() => {
        const user = auth.user();
        const current = selectedRelease();
        return Boolean(user && current && correctableStatuses.includes(current.status)
            && (user.role === "ADMIN" || current.publishedByUserId === user.id));
    });

    const load = async () => {
        setError("");
        try {
            const studentRequest = auth.user()?.role === "ADMIN" ? api.listStudents() : api.listMyStudents();
            const [paperPage, classroomList, studentList, releaseList] = await Promise.all([
                api.listPapers({ page: 0, size: 100, status: "READY" }),
                api.listClassrooms(),
                studentRequest,
                api.listPaperReleases(),
            ]);
            setPapers(paperPage.content);
            setClassrooms(classroomList);
            setStudents(studentList);
            setReleases(releaseList);
            if (!selectedReleaseId() && releaseList.length) setSelectedReleaseId(releaseList[0].id);
        } catch (cause) { setError(cause instanceof Error ? cause.message : "加载发布工作台失败。"); }
    };
    createEffect(() => { void load(); });

    const loadClassroomMembers = async (classroomIds: number[]) => {
        const missingIds = classroomIds.filter((id) => !classroomMembers().has(id));
        if (!missingIds.length) { setTargetError(""); return; }
        const requestId = ++targetRequest;
        setTargetLoading(true); setTargetError("");
        try {
            const results = await Promise.all(missingIds.map(async (id) => [id, await api.getClassroomStudents(id)] as const));
            setClassroomMembers((current) => {
                const next = new Map(current);
                results.forEach(([id, members]) => next.set(id, members));
                return next;
            });
        } catch (cause) {
            setTargetError(cause instanceof Error ? cause.message : "加载班级成员失败。");
        } finally {
            if (requestId === targetRequest) setTargetLoading(false);
        }
    };
    createEffect(() => { void loadClassroomMembers([...form.classroomIds]); });

    const toggleId = (key: "classroomIds" | "studentIds", id: number, checked: boolean) =>
        setForm(key, (items) => checked ? [...new Set([...items, id])] : items.filter((item) => item !== id));
    const copyReusablePaper = async (paper: PaperTemplateResponse) => {
        if (copyingId() !== null) return;
        setCopyingId(paper.id); setError("");
        try {
            await api.copyPaper(paper.id, { title: `${paper.title}（副本）` });
            setFeedback("试卷已复制为你的草稿，请在试卷管理中编辑并设为可发布。");
        } catch (cause) { setError(cause instanceof Error ? cause.message : "复制试卷失败。"); }
        finally { setCopyingId(null); }
    };
    const publish = async (event: SubmitEvent) => {
        event.preventDefault();
        if (busy()) return;
        const selectedPaper = publishablePapers().find((paper) => paper.id === Number(form.paperId));
        if (!selectedPaper) { setError("请选择你有权发布的可发布试卷。"); return; }
        if (targetLoading()) { setError("班级成员仍在加载，请稍后再发布。"); return; }
        if (targetError()) { setError("请先重新加载班级成员。"); return; }
        if (!resolvedTargets().length) { setError("最终目标中至少需要 1 名学生。"); return; }
        if (form.startTime && form.deadline && new Date(form.deadline) <= new Date(form.startTime)) {
            setError("作答时限必须晚于开始时间。"); return;
        }
        setBusy(true); setError("");
        try {
            const created = await api.publishPaper({
                paperId: selectedPaper.id,
                classroomIds: form.classroomIds,
                studentIds: form.studentIds,
                startTime: form.startTime || undefined,
                deadline: form.deadline || undefined,
                blankAnswerPolicy: form.blankAnswerPolicy,
                resultVisibility: form.resultVisibility,
            });
            setFeedback(`试卷已发布，最终目标 ${resolvedTargets().length} 人。`);
            setSelectedReleaseId(created.id);
            await load();
        } catch (cause) { setError(cause instanceof Error ? cause.message : "发布试卷失败。"); }
        finally { setBusy(false); }
    };
    const correct = async (action: "withdraw" | "invalidate" | "supersede") => {
        const current = selectedRelease();
        if (busy() || !canCorrect()) return;
        if (!current || !reason().trim()) { setError("终态操作必须填写原因。"); return; }
        const label = action === "withdraw" ? "撤回" : action === "invalidate" ? "作废" : "替换发布";
        if (!window.confirm(`确认${label}“${current.title}”？此操作会写入审计记录。`)) return;
        setBusy(true); setError("");
        try {
            if (action === "withdraw") await api.withdrawPaperRelease(current.id, { reason: reason().trim() });
            else if (action === "invalidate") await api.invalidatePaperRelease(current.id, { reason: reason().trim() });
            else await api.supersedePaperRelease(current.id, {
                reason: reason().trim(),
                startTime: current.startTime ?? undefined,
                deadline: current.deadline ?? undefined,
                blankAnswerPolicy: current.blankAnswerPolicy,
                resultVisibility: current.resultVisibility,
                showOriginalToStudents: showOriginal(),
            });
            setFeedback(`${label}操作已完成并写入审计记录。`);
            setReason("");
            await load();
        } catch (cause) { setError(cause instanceof Error ? cause.message : `${label}失败。`); }
        finally { setBusy(false); }
    };
    const auditDetails = (item: PaperReleaseResponse) => {
        if (item.status === "WITHDRAWN") return { reason: item.withdrawReason, at: item.withdrawnAt, by: item.withdrawnByUserId };
        if (item.status === "INVALIDATED") return { reason: item.invalidateReason, at: item.invalidatedAt, by: item.invalidatedByUserId };
        if (item.status === "SUPERSEDED") return { reason: item.supersedeReason, at: item.supersededAt, by: item.supersededByUserId };
        return null;
    };

    return <section class="space-y-5">
        <AssessmentNav />
        <PageHeader eyebrow="RELEASE OPERATIONS" title="试卷发布" description="冻结试卷内容、选择班级或学生，并维护发布后的纠正记录。" actions={<Button variant="outline" aria-label="刷新发布记录" onClick={() => void load()}><RefreshCw class="h-4 w-4" /></Button>} />
        <Show when={error()}><Alert class="border-destructive/30 bg-destructive/10 text-destructive">{error()}</Alert></Show>
        <Show when={feedback()}><Alert class="border-success/30 bg-success/10 text-success">{feedback()}</Alert></Show>
        <form class="space-y-4 rounded-md border bg-background p-4" onSubmit={publish}>
            <div class="grid gap-4 md:grid-cols-3"><div class="space-y-2"><Label for="release-paper">选择试卷</Label><select id="release-paper" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.paperId} onChange={(e) => setForm("paperId", e.currentTarget.value)}><option value="">请选择可发布试卷</option><For each={publishablePapers()}>{(paper) => <option value={paper.id}>{paper.title} · {categoryText(paper.categories)} · {paper.questionCount} 题 · {paper.totalScore} 分</option>}</For></select></div><div class="space-y-2"><Label for="release-start">开始时间</Label><Input id="release-start" type="datetime-local" value={form.startTime} onInput={(e) => setForm("startTime", e.currentTarget.value)} /></div><div class="space-y-2"><Label for="release-deadline">作答时限</Label><Input id="release-deadline" type="datetime-local" value={form.deadline} onInput={(e) => setForm("deadline", e.currentTarget.value)} /></div></div>
            <Show when={reusablePapers().length}><div class="space-y-2 rounded-md border border-dashed p-3"><p class="text-sm font-medium">可复用的他人试卷</p><For each={reusablePapers()}>{(paper) => <div class="flex items-center justify-between gap-3 text-sm"><span>{paper.title}</span><Button type="button" size="sm" variant="outline" aria-label={`复制后编辑 ${paper.title}`} disabled={copyingId() !== null} onClick={() => void copyReusablePaper(paper)}><Copy class="h-4 w-4" />复制后编辑</Button></div>}</For></div></Show>
            <div class="grid gap-4 lg:grid-cols-2"><fieldset class="space-y-2"><legend class="text-sm font-medium">目标班级</legend><div class="grid gap-2 sm:grid-cols-2"><For each={classrooms()} fallback={<p class="text-sm text-muted-foreground">暂无可用班级</p>}>{(classroom) => <label class="flex items-center justify-between rounded-md border px-3 py-2 text-sm"><span>{classroom.name}<small class="ml-2 text-muted-foreground">{classroom.studentCount ?? 0} 人</small></span><input aria-label={classroom.name} type="checkbox" checked={form.classroomIds.includes(classroom.id)} onChange={(e) => toggleId("classroomIds", classroom.id, e.currentTarget.checked)} /></label>}</For></div></fieldset><fieldset class="space-y-2"><legend class="text-sm font-medium">补充学生</legend><div class="grid max-h-40 gap-2 overflow-y-auto sm:grid-cols-2"><For each={students()} fallback={<p class="text-sm text-muted-foreground">暂无可用学生</p>}>{(student) => <label class="flex items-center justify-between rounded-md border px-3 py-2 text-sm"><span>{student.displayName}</span><input aria-label={student.displayName} type="checkbox" checked={form.studentIds.includes(student.id)} onChange={(e) => toggleId("studentIds", student.id, e.currentTarget.checked)} /></label>}</For></div></fieldset></div>
            <div class="rounded-md border bg-muted/30 p-3"><div class="flex items-center justify-between"><h2 class="font-medium">最终目标 {resolvedTargets().length} 人</h2><Show when={targetLoading()}><span class="text-xs text-muted-foreground">正在加载班级成员...</span></Show></div><Show when={targetError()}><Alert class="mt-2 flex items-center justify-between gap-2 border-destructive/30 text-destructive"><span>{targetError()}</span><Button type="button" size="sm" variant="outline" onClick={() => void loadClassroomMembers([...form.classroomIds])}>重试</Button></Alert></Show><div class="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3"><For each={resolvedTargets()} fallback={<p class="text-sm text-muted-foreground">选择班级或补充学生后显示最终名单。</p>}>{(target) => <div class="rounded-md border bg-background px-3 py-2 text-sm"><p class="font-medium">{target.student.displayName}</p><div class="mt-1 flex flex-wrap gap-1"><For each={target.classroomIds}>{(classroomId) => <Badge variant="outline">{classrooms().find((item) => item.id === classroomId)?.name ?? `班级 #${classroomId}`}</Badge>}</For><Show when={target.explicit}><Badge><span>补充选择</span></Badge></Show></div></div>}</For></div></div>
            <div class="grid gap-4 md:grid-cols-3"><div class="space-y-2"><Label for="blank-policy">空白答案规则</Label><select id="blank-policy" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.blankAnswerPolicy} onChange={(e) => setForm("blankAnswerPolicy", e.currentTarget.value as PaperBlankAnswerPolicy)}><option value="ALLOW_BLANK">允许留空提交</option><option value="REQUIRE_ALL_ANSWERED">必须全部作答</option></select></div><div class="space-y-2"><Label for="result-visibility">结果可见性</Label><select id="result-visibility" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.resultVisibility} onChange={(e) => setForm("resultVisibility", e.currentTarget.value as PaperResultVisibility)}><option value="HIDDEN_UNTIL_RELEASED">老师统一发布后可见</option><option value="SCORE_ONLY">提交后仅看分数</option><option value="SCORE_AND_ANSWERS">提交后看分数与答案</option></select></div><div class="flex items-end justify-end"><Button type="submit" disabled={busy() || targetLoading()}><Send class="h-4 w-4" />{busy() ? "发布中..." : "发布试卷"}</Button></div></div>
        </form>
        <div class="grid gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(340px,0.7fr)]">
            <div data-testid="release-table-scroll" class="overflow-x-auto rounded-md border bg-background"><Show when={releases().length} fallback={<EmptyState title="暂无发布记录" description="发布可用试卷后，记录会保存在这里。" />}><table class="w-full min-w-[800px] text-left text-sm"><thead class="bg-muted"><tr><th class="px-3 py-2">发布</th><th class="px-3 py-2">类型</th><th class="px-3 py-2">状态</th><th class="px-3 py-2">时间</th><th class="px-3 py-2">目标</th><th class="px-3 py-2">结果</th></tr></thead><tbody><For each={releases()}>{(item) => <tr class="border-t" classList={{ "bg-accent/40": selectedReleaseId() === item.id }}><td class="px-3 py-3"><button type="button" class="text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`选择发布 ${item.title}`} onClick={() => setSelectedReleaseId(item.id)}><span class="block font-medium">{item.title}</span><span class="text-xs text-muted-foreground">#{item.id}</span></button></td><td class="px-3 py-3 whitespace-nowrap">{categoryText(item.categories)}</td><td class="px-3 py-3"><Badge variant="outline">{statusLabels[item.status]}</Badge></td><td class="px-3 py-3 text-xs"><p>{item.startTime || "立即开始"}</p><p class="text-muted-foreground">至 {item.deadline || "不限时"}</p></td><td class="px-3 py-3">{item.targets.length} 人</td><td class="px-3 py-3"><A class="text-primary underline" href={`/paper-results/${item.id}`}>查看结果</A></td></tr>}</For></tbody></table></Show></div>
            <aside class="space-y-4 rounded-md border bg-background p-4">
                <Show when={selectedRelease()} fallback={<p class="text-sm text-muted-foreground">选择发布记录查看详情和纠正操作。</p>}>{(item) => <>
                    <div><div class="flex items-center justify-between"><h2 class="font-semibold">{item().title}</h2><Badge>{statusLabels[item().status]}</Badge></div><dl class="mt-3 grid grid-cols-2 gap-2 text-sm"><div><dt class="text-muted-foreground">类型</dt><dd>{categoryText(item().categories)}</dd></div><div><dt class="text-muted-foreground">题目</dt><dd>{item().questionCount}</dd></div><div><dt class="text-muted-foreground">总分</dt><dd>{item().totalScore}</dd></div><div><dt class="text-muted-foreground">空白规则</dt><dd>{item().blankAnswerPolicy === "ALLOW_BLANK" ? "允许留空" : "全部必答"}</dd></div><div><dt class="text-muted-foreground">结果</dt><dd>{resultVisibilityLabels[item().resultVisibility]}</dd></div></dl></div>
                    <Show when={canCorrect()}><div class="space-y-2"><Label for="correction-reason">纠正原因</Label><Input id="correction-reason" value={reason()} onInput={(e) => setReason(e.currentTarget.value)} placeholder="必填，写入审计记录" /><label class="flex items-center gap-2 text-sm"><input type="checkbox" checked={showOriginal()} onChange={(e) => setShowOriginal(e.currentTarget.checked)} />替换后向学生显示原发布</label></div><div class="grid grid-cols-3 gap-2"><Button variant="outline" disabled={busy() || !reason().trim()} onClick={() => void correct("withdraw")}><Undo2 class="h-4 w-4" />撤回</Button><Button variant="destructive" disabled={busy() || !reason().trim()} onClick={() => void correct("invalidate")}><Ban class="h-4 w-4" />作废</Button><Button variant="secondary" disabled={busy() || !reason().trim()} onClick={() => void correct("supersede")}><RotateCcw class="h-4 w-4" />替换</Button></div></Show>
                    <Show when={!correctableStatuses.includes(item().status)}>
                        <Alert><AlertTriangle class="mr-2 inline h-4 w-4" /><strong>当前发布已进入终态，只保留审计查看。</strong><Show when={auditDetails(item())}>{(audit) => <><span class="mt-2 block">原因：{audit().reason || "未记录"}</span><span class="block text-xs text-muted-foreground">操作人 #{audit().by ?? "-"} · {audit().at ?? "-"}</span></>}</Show></Alert>
                    </Show>
                </>}</Show>
            </aside>
        </div>
    </section>;
}
