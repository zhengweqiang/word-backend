import { Archive, Copy, Eye, Pencil, Plus, RefreshCw, Search, Trash2 } from "lucide-solid";
import { createEffect, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { AssessmentNav } from "@/components/assessments/assessment-nav";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { parseAcceptedAnswers, questionTypeLabels, validateQuestionDraft } from "@/lib/assessment-rules";
import type { QuestionBankItemResponse, QuestionPayload, QuestionStatus, QuestionType } from "@/types/api";

const statusLabels: Record<QuestionStatus, string> = { DRAFT: "草稿", ACTIVE: "可用", ARCHIVED: "已归档" };

const emptyForm = () => ({
    questionType: "SINGLE_CHOICE" as QuestionType,
    category: "",
    stem: "", optionA: "", optionB: "", optionC: "", optionD: "", acceptedAnswers: "",
    defaultScore: "2", difficulty: "", tags: "", explanation: "", status: "ACTIVE" as QuestionStatus,
});

export function QuestionBankPage() {
    const auth = useAuth();
    const [filters, setFilters] = createStore({ keyword: "", questionType: "", category: "", status: "ACTIVE", tag: "" });
    const [items, setItems] = createSignal<QuestionBankItemResponse[]>([]);
    const [categories, setCategories] = createSignal<string[]>([]);
    const [categoryOptions, setCategoryOptions] = createSignal<string[]>([]);
    const [selected, setSelected] = createSignal<QuestionBankItemResponse | null>(null);
    const [editingId, setEditingId] = createSignal<number | null>(null);
    const [showForm, setShowForm] = createSignal(false);
    const [form, setForm] = createStore(emptyForm());
    const [loading, setLoading] = createSignal(false);
    const [saving, setSaving] = createSignal(false);
    const [categoryName, setCategoryName] = createSignal("");
    const [editingCategory, setEditingCategory] = createSignal("");
    const [categoryBusy, setCategoryBusy] = createSignal(false);
    const [error, setError] = createSignal("");
    const [feedback, setFeedback] = createSignal("");

    const load = async () => {
        setLoading(true); setError("");
        try {
            const result = await api.listQuestions({
                page: 0, size: 100, keyword: filters.keyword || undefined,
                questionType: (filters.questionType || undefined) as QuestionType | undefined,
                category: filters.category || undefined,
                status: (filters.status || undefined) as QuestionStatus | undefined,
                tag: filters.tag || undefined,
            });
            setItems(result.content);
            if (selected() && !result.content.some((item) => item.id === selected()?.id)) setSelected(null);
        } catch (cause) { setError(cause instanceof Error ? cause.message : "加载题库失败。"); }
        finally { setLoading(false); }
    };
    createEffect(() => { void load(); });

    const loadCategories = async () => {
        try {
            const [records, bankCategories] = await Promise.all([
                api.listQuestionCategories(),
                api.listQuestionBankCategories(),
            ]);
            const recordNames = records.map((item) => item.name);
            setCategories(recordNames);
            setCategoryOptions(Array.from(new Set([...recordNames, ...bankCategories])).sort((a, b) => a.localeCompare(b)));
        } catch (cause) { setError(cause instanceof Error ? cause.message : "加载类型失败。"); }
    };
    createEffect(() => { void loadCategories(); });

    const saveCategory = async () => {
        const name = categoryName().trim();
        if (!name || categoryBusy()) return;
        setCategoryBusy(true); setError(""); setFeedback("");
        try {
            const editing = editingCategory();
            if (editing) {
                const current = (await api.listQuestionCategories()).find((item) => item.name === editing);
                if (!current) throw new Error("类型不存在或已删除。");
                await api.updateQuestionCategory(current.id, { name });
                setFeedback("类型已更新。");
            } else {
                await api.createQuestionCategory({ name });
                setFeedback("类型已创建。");
            }
            setCategoryName(""); setEditingCategory(""); await loadCategories(); await load();
        } catch (cause) { setError(cause instanceof Error ? cause.message : "保存类型失败。"); }
        finally { setCategoryBusy(false); }
    };

    const deleteCategory = async (name: string) => {
        if (!window.confirm(`确认删除类型“${name}”？`)) return;
        setCategoryBusy(true); setError(""); setFeedback("");
        try {
            const current = (await api.listQuestionCategories()).find((item) => item.name === name);
            if (!current) throw new Error("类型不存在或已删除。");
            await api.deleteQuestionCategory(current.id);
            if (filters.category === name) setFilters("category", "");
            if (form.category === name) setForm("category", "");
            setFeedback("类型已删除。"); await loadCategories(); await load();
        } catch (cause) { setError(cause instanceof Error ? cause.message : "删除类型失败。"); }
        finally { setCategoryBusy(false); }
    };

    const openCreate = () => {
        setEditingId(null); setForm(emptyForm()); setShowForm(true); setError("");
    };
    const openEdit = (item: QuestionBankItemResponse) => {
        setEditingId(item.id);
        setForm({
            questionType: item.questionType, category: item.category ?? "", stem: item.stem,
            optionA: item.options?.A ?? "", optionB: item.options?.B ?? "",
            optionC: item.options?.C ?? "", optionD: item.options?.D ?? "",
            acceptedAnswers: item.acceptedAnswers.join(item.questionType === "FILL_IN_BLANK" ? " | " : ","),
            defaultScore: String(item.defaultScore),
            difficulty: item.difficulty == null ? "" : String(item.difficulty), tags: (item.tags ?? []).join(","),
            explanation: item.explanation ?? "", status: item.status,
        });
        setShowForm(true);
    };
    const payload = (): QuestionPayload => {
        const options = Object.fromEntries([
            ["A", form.optionA], ["B", form.optionB], ["C", form.optionC], ["D", form.optionD],
        ].filter(([, value]) => value.trim()).map(([key, value]) => [key, value.trim()]));
        return {
            questionType: form.questionType,
            category: form.category.trim() || undefined,
            stem: form.stem.trim(),
            options: form.questionType === "FILL_IN_BLANK" ? {} : options,
            acceptedAnswers: parseAcceptedAnswers(form.acceptedAnswers, form.questionType),
            defaultScore: Number(form.defaultScore),
            difficulty: form.difficulty ? Number(form.difficulty) : undefined,
            tags: form.tags.split(",").map((value) => value.trim()).filter(Boolean),
            explanation: form.explanation.trim() || undefined,
            status: form.status,
        };
    };
    const save = async (event: SubmitEvent) => {
        event.preventDefault();
        if (saving()) return;
        setSaving(true); setError(""); setFeedback("");
        try {
            const body = payload();
            const validationErrors = validateQuestionDraft({
                questionType: body.questionType,
                stem: body.stem,
                options: body.options ?? {},
                acceptedAnswers: body.acceptedAnswers,
                score: form.defaultScore,
            });
            if (validationErrors.length) {
                setError(validationErrors.join(" ")); return;
            }
            const id = editingId();
            await (id ? api.updateQuestion(id, body) : api.createQuestion(body));
            setFeedback(id ? "试题已更新。" : "试题已创建。"); setShowForm(false); await load();
        } catch (cause) { setError(cause instanceof Error ? cause.message : "保存试题失败。"); }
        finally { setSaving(false); }
    };
    const copyItem = async (item: QuestionBankItemResponse) => {
        setError("");
        try { await api.copyQuestion(item.id, { stem: `${item.stem}（副本）` }); setFeedback("试题副本已创建。"); await load(); }
        catch (cause) { setError(cause instanceof Error ? cause.message : "复制试题失败。"); }
    };
    const archiveItem = async (item: QuestionBankItemResponse) => {
        if (!window.confirm(`确认归档“${item.stem}”？`)) return;
        try { await api.archiveQuestion(item.id); setFeedback("试题已归档。"); await load(); }
        catch (cause) { setError(cause instanceof Error ? cause.message : "归档试题失败。"); }
    };
    const canManage = (item: QuestionBankItemResponse) => auth.user()?.role === "ADMIN"
        || auth.user()?.id === item.createdByUserId;

    return (
        <section class="space-y-5">
            <AssessmentNav />
            <PageHeader eyebrow="ASSESSMENT BANK" title="共享题库" description="检索、维护并复用客观题，保留来源和修改记录。" actions={
                <div class="flex gap-2"><Button variant="outline" aria-label="刷新题库" onClick={() => void load()}><RefreshCw class="h-4 w-4" /></Button><Button onClick={openCreate}><Plus class="h-4 w-4" />新建试题</Button></div>
            } />
            <Show when={error()}><Alert class="flex items-center justify-between gap-3 border-destructive/30 bg-destructive/10 text-destructive"><span>{error()}</span><Button type="button" size="sm" variant="outline" aria-label="重试加载题库" onClick={() => void load()}><RefreshCw class="h-4 w-4" />重试</Button></Alert></Show>
            <Show when={feedback()}><Alert class="border-success/30 bg-success/10 text-success">{feedback()}</Alert></Show>
            <div class="grid gap-3 rounded-md border bg-background p-3 lg:grid-cols-[minmax(220px,1fr)_170px_170px_150px_180px_auto]">
                <Input aria-label="关键词" placeholder="搜索题干或解析" value={filters.keyword} onInput={(e) => setFilters("keyword", e.currentTarget.value)} />
                <select aria-label="题型筛选" class="h-10 rounded-md border bg-background px-3 text-sm" value={filters.questionType} onChange={(e) => setFilters("questionType", e.currentTarget.value)}><option value="">全部题型</option><For each={Object.entries(questionTypeLabels)}>{([value, label]) => <option value={value}>{label}</option>}</For></select>
                <select aria-label="类型筛选" class="h-10 rounded-md border bg-background px-3 text-sm" value={filters.category} onChange={(e) => setFilters("category", e.currentTarget.value)}><option value="">全部类型</option><For each={categoryOptions()}>{(category) => <option value={category}>{category}</option>}</For></select>
                <select aria-label="状态筛选" class="h-10 rounded-md border bg-background px-3 text-sm" value={filters.status} onChange={(e) => setFilters("status", e.currentTarget.value)}><option value="">全部状态</option><For each={Object.entries(statusLabels)}>{([value, label]) => <option value={value}>{label}</option>}</For></select>
                <Input aria-label="标签筛选" placeholder="标签" value={filters.tag} onInput={(e) => setFilters("tag", e.currentTarget.value)} />
                <Button variant="outline" onClick={() => void load()}><Search class="h-4 w-4" />筛选</Button>
            </div>
            <div class="space-y-3 rounded-md border bg-background p-4">
                <div class="flex flex-wrap items-end gap-3">
                    <div class="min-w-[220px] flex-1 space-y-2">
                        <Label for="question-category-name">类型名称</Label>
                        <Input id="question-category-name" value={categoryName()} onInput={(e) => setCategoryName(e.currentTarget.value)} />
                    </div>
                    <Button type="button" disabled={categoryBusy() || !categoryName().trim()} onClick={() => void saveCategory()}><Plus class="h-4 w-4" />{editingCategory() ? "保存类型" : "新增类型"}</Button>
                    <Show when={editingCategory()}><Button type="button" variant="outline" onClick={() => { setEditingCategory(""); setCategoryName(""); }}>取消编辑</Button></Show>
                </div>
                <div class="flex flex-wrap gap-2">
                    <For each={categories()} fallback={<p class="text-sm text-muted-foreground">暂无类型记录</p>}>{(category) => <span class="inline-flex items-center gap-1 rounded-md border px-2 py-1 text-sm"><button type="button" class="font-medium" onClick={() => { setEditingCategory(category); setCategoryName(category); }}>{category}</button><button type="button" aria-label={`删除类型 ${category}`} disabled={categoryBusy()} onClick={() => void deleteCategory(category)}><Trash2 class="h-3.5 w-3.5 text-muted-foreground" /></button></span>}</For>
                </div>
            </div>
            <div class="grid min-h-[420px] gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(320px,0.8fr)]">
                <div data-testid="question-table-scroll" class="overflow-x-auto rounded-md border bg-background">
                    <Show when={!loading()} fallback={<p class="p-5 text-sm text-muted-foreground">正在加载题库...</p>}>
                        <Show when={items().length} fallback={<EmptyState title="没有匹配试题" description="调整筛选条件或创建一条新试题。" />}>
                            <table class="w-full min-w-[860px] text-left text-sm"><thead class="bg-muted/70 text-xs text-muted-foreground"><tr><th class="px-3 py-2">题型</th><th class="px-3 py-2">类型</th><th class="px-3 py-2">题干</th><th class="px-3 py-2">分值</th><th class="px-3 py-2">状态</th><th class="px-3 py-2 text-right">操作</th></tr></thead><tbody>
                                <For each={items()}>{(item) => <tr class="border-t align-top"><td class="px-3 py-3 whitespace-nowrap">{questionTypeLabels[item.questionType]}</td><td class="px-3 py-3 whitespace-nowrap">{item.category || "-"}</td><td class="max-w-xl px-3 py-3"><p class="font-medium">{item.stem}</p><p class="mt-1 text-xs text-muted-foreground">{(item.tags ?? []).join(" · ") || "无标签"}</p></td><td class="px-3 py-3">{item.defaultScore}</td><td class="px-3 py-3"><Badge variant="outline">{statusLabels[item.status]}</Badge></td><td class="px-3 py-2"><div class="flex justify-end gap-1"><Button size="icon" variant="ghost" title="预览" aria-label={`预览 ${item.stem}`} onClick={() => setSelected(item)}><Eye class="h-4 w-4" /></Button><Show when={canManage(item)}><Button size="icon" variant="ghost" title="编辑" aria-label={`编辑 ${item.stem}`} onClick={() => openEdit(item)}><Pencil class="h-4 w-4" /></Button></Show><Button size="icon" variant="ghost" title="复制" aria-label={`复制 ${item.stem}`} onClick={() => void copyItem(item)}><Copy class="h-4 w-4" /></Button><Show when={canManage(item) && item.status !== "ARCHIVED"}><Button size="icon" variant="ghost" title="归档" aria-label={`归档 ${item.stem}`} onClick={() => void archiveItem(item)}><Archive class="h-4 w-4" /></Button></Show></div></td></tr>}</For>
                            </tbody></table>
                        </Show>
                    </Show>
                </div>
                <aside class="rounded-md border bg-background p-4">
                    <Show when={selected()} fallback={<p class="text-sm text-muted-foreground">选择一条试题查看答案、选项和解析。</p>}>{(item) => <div class="space-y-4"><div><div class="flex flex-wrap gap-2"><Badge>{questionTypeLabels[item().questionType]}</Badge><Show when={item().category}><Badge variant="outline">{item().category}</Badge></Show></div><h2 class="mt-3 text-lg font-semibold">{item().stem}</h2></div><Show when={Object.keys(item().options ?? {}).length}><div class="space-y-2"><For each={Object.entries(item().options ?? {})}>{([key, value]) => <p class="rounded-md bg-muted px-3 py-2 text-sm">{key}：{value}</p>}</For></div></Show><p class="text-sm"><span class="text-muted-foreground">正确答案：</span>{item().acceptedAnswers.join("、")}</p><p class="text-sm"><span class="text-muted-foreground">解析：</span>{item().explanation || "暂无解析"}</p></div>}</Show>
                </aside>
            </div>
            <Show when={showForm()}>
                <form aria-label="试题表单" class="space-y-4 rounded-md border bg-background p-4" onSubmit={save}>
                    <div class="flex items-center justify-between"><h2 class="text-lg font-semibold">{editingId() ? "编辑试题" : "新建试题"}</h2><Button type="button" variant="ghost" onClick={() => setShowForm(false)}>关闭</Button></div>
                    <div class="grid gap-4 md:grid-cols-4"><div class="space-y-2"><Label for="question-type">题型</Label><select id="question-type" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.questionType} onChange={(e) => setForm("questionType", e.currentTarget.value as QuestionType)}><For each={Object.entries(questionTypeLabels)}>{([value, label]) => <option value={value}>{label}</option>}</For></select></div><div class="space-y-2"><Label for="question-category">类型</Label><select id="question-category" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.category} onChange={(e) => setForm("category", e.currentTarget.value)}><option value="">未分类</option><For each={categoryOptions()}>{(category) => <option value={category}>{category}</option>}</For></select></div><div class="space-y-2"><Label for="question-score">分值</Label><Input id="question-score" type="number" min="0.01" step="0.01" value={form.defaultScore} onInput={(e) => setForm("defaultScore", e.currentTarget.value)} /></div><div class="space-y-2"><Label for="question-status">状态</Label><select id="question-status" class="h-10 w-full rounded-md border bg-background px-3 text-sm" value={form.status} onChange={(e) => setForm("status", e.currentTarget.value as QuestionStatus)}><For each={Object.entries(statusLabels)}>{([value, label]) => <option value={value}>{label}</option>}</For></select></div></div>
                    <div class="space-y-2"><Label for="question-stem">题干</Label><Textarea id="question-stem" value={form.stem} onInput={(e) => setForm("stem", e.currentTarget.value)} /></div>
                    <Show when={form.questionType !== "FILL_IN_BLANK"}><div class="grid gap-3 md:grid-cols-2"><For each={["A", "B", "C", "D"] as const}>{(key) => <div class="space-y-2"><Label for={`option-${key}`}>选项 {key}</Label><Input id={`option-${key}`} value={form[`option${key}`]} onInput={(e) => setForm(`option${key}`, e.currentTarget.value)} /></div>}</For></div></Show>
                    <div class="grid gap-4 md:grid-cols-3"><div class="space-y-2"><Label for="question-answer">正确答案</Label><Input id="question-answer" placeholder={form.questionType === "MULTIPLE_CHOICE" ? "A,B" : form.questionType === "FILL_IN_BLANK" ? "abandon | forsake" : "A"} value={form.acceptedAnswers} onInput={(e) => setForm("acceptedAnswers", e.currentTarget.value)} /><Show when={form.questionType === "FILL_IN_BLANK"}><p class="text-xs text-muted-foreground">多个可接受答案使用 | 分隔</p></Show></div><div class="space-y-2"><Label for="question-difficulty">难度</Label><Input id="question-difficulty" type="number" min="1" max="5" value={form.difficulty} onInput={(e) => setForm("difficulty", e.currentTarget.value)} /></div><div class="space-y-2"><Label for="question-tags">标签</Label><Input id="question-tags" placeholder="四级,核心" value={form.tags} onInput={(e) => setForm("tags", e.currentTarget.value)} /></div></div>
                    <div class="space-y-2"><Label for="question-explanation">解析</Label><Textarea id="question-explanation" value={form.explanation} onInput={(e) => setForm("explanation", e.currentTarget.value)} /></div>
                    <Button type="submit" disabled={saving()}>{saving() ? "保存中..." : "保存试题"}</Button>
                </form>
            </Show>
        </section>
    );
}
