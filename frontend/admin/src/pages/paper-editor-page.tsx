import { A, useParams } from "@solidjs/router";
import { Archive, ArrowDown, ArrowLeft, ArrowUp, Copy, Eye, Plus, Save, Search, Trash2 } from "lucide-solid";
import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { AssessmentNav } from "@/components/assessments/assessment-nav";
import { PageHeader } from "@/components/shared/page-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { canManagePaper, questionTypeLabels, validateAssessmentScore } from "@/lib/assessment-rules";
import type {
    EditablePaperTemplateStatus,
    PaperTemplateResponse,
    QuestionBankItemResponse,
    QuestionType,
} from "@/types/api";

export function PaperEditorPage() {
    const auth = useAuth();
    const params = useParams<{ paperId: string }>();
    const paperId = () => Number(params.paperId);
    const [paper, setPaper] = createSignal<PaperTemplateResponse | null>(null);
    const [questions, setQuestions] = createSignal<QuestionBankItemResponse[]>([]);
    const [categories, setCategories] = createSignal<string[]>([]);
    const [form, setForm] = createStore({
        title: "",
        instructions: "",
        status: "DRAFT" as EditablePaperTemplateStatus,
        shuffleQuestions: false,
        shuffleOptions: false,
    });
    const [bankFilters, setBankFilters] = createStore({ keyword: "", questionType: "", category: "" });
    const [bankPage, setBankPage] = createSignal(0);
    const [bankTotalPages, setBankTotalPages] = createSignal(0);
    const [bankLoading, setBankLoading] = createSignal(false);
    const [previewing, setPreviewing] = createSignal(false);
    const [busy, setBusy] = createSignal(false);
    const [error, setError] = createSignal("");
    const [feedback, setFeedback] = createSignal("");
    const canEdit = createMemo(() => {
        const user = auth.user();
        const current = paper();
        return Boolean(user && current && current.status !== "ARCHIVED" && canManagePaper({
            role: user.role,
            userId: user.id,
            ownerUserId: current.ownerUserId,
        }));
    });

    const applyPaper = (value: PaperTemplateResponse) => {
        setPaper(value);
        setForm({
            title: value.title,
            instructions: value.instructions ?? "",
            status: value.status === "READY" ? "READY" : "DRAFT",
            shuffleQuestions: value.shuffleQuestions,
            shuffleOptions: value.shuffleOptions,
        });
    };
    const loadPaper = async () => applyPaper(await api.getPaperPreview(paperId()));
    const loadBank = async (page: number, append: boolean, keyword: string, questionType: string, category = "") => {
        setBankLoading(true);
        try {
            const result = await api.listQuestions({
                page,
                size: 20,
                keyword: keyword.trim() || undefined,
                questionType: (questionType || undefined) as QuestionType | undefined,
                category: category || undefined,
                status: "ACTIVE",
            });
            setQuestions((current) => append
                ? [...current, ...result.content.filter((item) => !current.some((existing) => existing.id === item.id))]
                : result.content);
            setBankPage(result.number);
            setBankTotalPages(result.totalPages);
        } finally {
            setBankLoading(false);
        }
    };
    const load = async () => {
        setError("");
        try {
            await Promise.all([loadPaper(), loadBank(0, false, "", "")]);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "加载试卷编辑器失败。");
        }
    };
    createEffect(() => { if (Number.isFinite(paperId())) void load(); });
    createEffect(() => {
        void api.listQuestionCategories()
            .then((result) => setCategories(result.map((item) => item.name)))
            .catch(() => setCategories([]));
    });

    const mutate = async (runner: () => Promise<PaperTemplateResponse>, message: string) => {
        if (busy() || !canEdit()) return;
        setBusy(true); setError("");
        try { applyPaper(await runner()); setFeedback(message); }
        catch (cause) { setError(cause instanceof Error ? cause.message : "更新试卷失败。"); }
        finally { setBusy(false); }
    };
    const saveMetadata = () => {
        if (!form.title.trim()) { setError("试卷标题不能为空。"); return; }
        void mutate(() => api.updatePaper(paperId(), {
            ...form,
            title: form.title.trim(),
            instructions: form.instructions.trim() || undefined,
        }), "试卷设置已保存。");
    };
    const updateScore = (paperQuestionId: number, rawScore: string) => {
        const scoreValidation = validateAssessmentScore(rawScore);
        if (scoreValidation) { setError(scoreValidation); return; }
        void mutate(
            () => api.updatePaperQuestionScore(paperId(), paperQuestionId, { score: Number(rawScore) }),
            "分值已更新。",
        );
    };
    const move = (id: number, delta: number) => {
        const current = paper()?.questions ?? [];
        const index = current.findIndex((item) => item.id === id);
        const nextIndex = index + delta;
        if (index < 0 || nextIndex < 0 || nextIndex >= current.length) return;
        const ids = current.map((item) => item.id);
        [ids[index], ids[nextIndex]] = [ids[nextIndex], ids[index]];
        void mutate(() => api.reorderPaperQuestions(paperId(), { paperQuestionIds: ids }), "题目顺序已更新。");
    };
    const copyPaper = async () => {
        const current = paper();
        if (!current || busy()) return;
        setBusy(true); setError("");
        try {
            await api.copyPaper(current.id, { title: `${current.title}（副本）` });
            setFeedback("已复制为你的试卷草稿，请返回试卷列表继续编辑。");
        } catch (cause) { setError(cause instanceof Error ? cause.message : "复制试卷失败。"); }
        finally { setBusy(false); }
    };
    const archivePaper = async () => {
        const current = paper();
        if (!current || busy() || !window.confirm(`确认归档“${current.title}”？`)) return;
        setBusy(true); setError("");
        try { await api.archivePaper(current.id); await loadPaper(); setFeedback("试卷已归档。"); }
        catch (cause) { setError(cause instanceof Error ? cause.message : "归档试卷失败。"); }
        finally { setBusy(false); }
    };

    return <section class="space-y-5">
        <AssessmentNav />
        <PageHeader eyebrow="PAPER EDITOR" title={paper()?.title ?? "试卷编辑器"} description="编排题目顺序、分值与发布前展示配置。" actions={<A class="inline-flex h-9 items-center gap-2 rounded-md border bg-background px-3 text-sm" href="/papers"><ArrowLeft class="h-4 w-4" />返回试卷</A>} />
        <Show when={error()}><Alert class="border-destructive/30 bg-destructive/10 text-destructive">{error()}</Alert></Show>
        <Show when={feedback()}><Alert class="border-success/30 bg-success/10 text-success">{feedback()}</Alert></Show>
        <Show when={paper()} fallback={<p class="rounded-md border bg-background p-5 text-sm text-muted-foreground">正在加载试卷...</p>}>{(current) => <>
            <Show when={canEdit()} fallback={
                <Alert class="flex flex-wrap items-center justify-between gap-3">
                    <span><strong>只读预览</strong>：你不是此试卷的所有者，请复制后再修改或发布。</span>
                    <Button disabled={busy()} onClick={() => void copyPaper()}><Copy class="h-4 w-4" />复制为我的试卷</Button>
                </Alert>
            }>
                <div class="grid gap-4 rounded-md border bg-background p-4 lg:grid-cols-4">
                    <div class="space-y-2 lg:col-span-2"><Label for="editor-title">标题</Label><Input id="editor-title" value={form.title} onInput={(e) => setForm("title", e.currentTarget.value)} /></div>
                    <div class="space-y-2"><Label for="editor-status">状态</Label><select id="editor-status" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.status} onChange={(e) => setForm("status", e.currentTarget.value as EditablePaperTemplateStatus)}><option value="DRAFT">草稿</option><option value="READY">可发布</option></select></div>
                    <div class="flex items-end gap-2"><Button disabled={busy()} onClick={saveMetadata}><Save class="h-4 w-4" />保存设置</Button><Button variant="outline" aria-label="预览试卷" onClick={() => setPreviewing((value) => !value)}><Eye class="h-4 w-4" /></Button><Button size="icon" variant="outline" aria-label="归档试卷" disabled={busy()} onClick={() => void archivePaper()}><Archive class="h-4 w-4" /></Button></div>
                    <div class="space-y-2 lg:col-span-3"><Label for="editor-instructions">作答说明</Label><Textarea id="editor-instructions" value={form.instructions} onInput={(e) => setForm("instructions", e.currentTarget.value)} /></div>
                    <div class="space-y-2 pt-7"><label class="mr-4 inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={form.shuffleQuestions} onChange={(e) => setForm("shuffleQuestions", e.currentTarget.checked)} />打乱题目</label><label class="inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={form.shuffleOptions} onChange={(e) => setForm("shuffleOptions", e.currentTarget.checked)} />打乱选项</label></div>
                </div>
            </Show>
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(320px,0.8fr)]">
                <div class="space-y-3 rounded-md border bg-background p-4">
                    <div class="flex items-center justify-between"><h2 class="font-semibold">试卷题目</h2><p class="text-sm text-muted-foreground">{current().questionCount} 题 · {current().totalScore} 分</p></div>
                    <Show when={current().questions.length} fallback={<p class="py-8 text-center text-sm text-muted-foreground">{canEdit() ? "从右侧题库添加试题。" : "此试卷还没有题目。"}</p>}>
                        <For each={current().questions}>{(question, index) => <article class="grid gap-3 rounded-md border p-3 md:grid-cols-[40px_minmax(0,1fr)_100px_auto]">
                            <div class="font-semibold">{question.questionOrder}</div><div><p class="font-medium">{question.stem}</p><div class="mt-2 flex flex-wrap gap-2"><Badge variant="outline">{questionTypeLabels[question.questionType]}</Badge><Show when={question.category}><Badge variant="outline">{question.category}</Badge></Show></div></div>
                            <Show when={canEdit()} fallback={<p class="text-sm">{question.score} 分</p>}><label class="text-xs text-muted-foreground">分值<Input aria-label={`题目 ${question.questionOrder} 分值`} type="number" min="0.01" step="0.01" value={question.score} onChange={(e) => updateScore(question.id, e.currentTarget.value)} /></label></Show>
                            <Show when={canEdit()}><div class="flex gap-1"><Button size="icon" variant="ghost" aria-label={`上移题目 ${question.questionOrder}`} disabled={index() === 0 || busy()} onClick={() => move(question.id, -1)}><ArrowUp class="h-4 w-4" /></Button><Button size="icon" variant="ghost" aria-label={`下移题目 ${question.questionOrder}`} disabled={index() === current().questions.length - 1 || busy()} onClick={() => move(question.id, 1)}><ArrowDown class="h-4 w-4" /></Button><Button size="icon" variant="ghost" aria-label={`移除题目 ${question.questionOrder}`} disabled={busy()} onClick={() => window.confirm("确认从试卷移除此题？") && void mutate(() => api.removePaperQuestion(paperId(), question.id), "题目已移除。")}><Trash2 class="h-4 w-4" /></Button></div></Show>
                        </article>}</For>
                    </Show>
                </div>
                <Show when={canEdit()}><aside class="space-y-3 rounded-md border bg-background p-4">
                    <h2 class="font-semibold">可用题库</h2>
                    <div class="grid gap-2 sm:grid-cols-[minmax(0,1fr)_150px_150px_auto]"><Input aria-label="编辑器题库搜索" placeholder="搜索 ACTIVE 题库" value={bankFilters.keyword} onInput={(e) => setBankFilters("keyword", e.currentTarget.value)} /><select aria-label="编辑器题型" class="h-10 rounded-md border bg-background px-3 text-sm" value={bankFilters.questionType} onChange={(e) => setBankFilters("questionType", e.currentTarget.value)}><option value="">全部题型</option><For each={Object.entries(questionTypeLabels)}>{([value, label]) => <option value={value}>{label}</option>}</For></select><select aria-label="编辑器类型" class="h-10 rounded-md border bg-background px-3 text-sm" value={bankFilters.category} onChange={(e) => setBankFilters("category", e.currentTarget.value)}><option value="">全部类型</option><For each={categories()}>{(category) => <option value={category}>{category}</option>}</For></select><Button variant="outline" disabled={bankLoading()} onClick={() => void loadBank(0, false, bankFilters.keyword, bankFilters.questionType, bankFilters.category)}><Search class="h-4 w-4" />搜索题库</Button></div>
                    <div class="max-h-[620px] space-y-2 overflow-y-auto"><For each={questions().filter((item) => !current().questions.some((paperQuestion) => paperQuestion.sourceQuestionId === item.id))}>{(question) => <div class="rounded-md border p-3"><p class="text-sm font-medium">{question.stem}</p><div class="mt-2 flex items-center justify-between gap-3"><span class="text-xs text-muted-foreground">{question.category ? `${question.category} · ` : ""}{questionTypeLabels[question.questionType]} · 默认 {question.defaultScore} 分</span><Button size="sm" aria-label={`添加试题 ${question.stem}`} disabled={busy()} onClick={() => void mutate(() => api.addPaperQuestion(paperId(), { questionId: question.id, score: question.defaultScore }), "试题已加入试卷。") }><Plus class="h-4 w-4" />添加</Button></div></div>}</For></div>
                    <Show when={bankPage() + 1 < bankTotalPages()}><Button class="w-full" variant="outline" disabled={bankLoading()} onClick={() => void loadBank(bankPage() + 1, true, bankFilters.keyword, bankFilters.questionType, bankFilters.category)}>加载更多试题</Button></Show>
                </aside></Show>
            </div>
            <Show when={previewing() || !canEdit()}><div class="rounded-md border bg-background p-5"><h2 class="text-xl font-semibold">{current().title}</h2><p class="mt-2 text-sm text-muted-foreground">{current().instructions || "无作答说明"}</p><ol class="mt-5 space-y-4"><For each={current().questions}>{(question) => <li><p class="font-medium">{question.questionOrder}. {question.stem} <Show when={question.category}><span class="text-sm text-muted-foreground">[{question.category}] </span></Show><span class="text-sm text-muted-foreground">({question.score} 分)</span></p><For each={Object.entries(question.options ?? {})}>{([key, value]) => <p class="ml-5 mt-1 text-sm">{key}：{value}</p>}</For></li>}</For></ol></div></Show>
        </>}</Show>
    </section>;
}
