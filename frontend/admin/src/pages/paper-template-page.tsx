import { A } from "@solidjs/router";
import { Archive, Copy, Eye, FilePlus2, Pencil, RefreshCw } from "lucide-solid";
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
import { canArchivePaper, canManagePaper } from "@/lib/assessment-rules";
import type { PaperTemplateResponse, PaperTemplateStatus } from "@/types/api";

const statusLabel: Record<PaperTemplateStatus, string> = { DRAFT: "草稿", READY: "可发布", ARCHIVED: "已归档" };

export function PaperTemplatePage() {
    const auth = useAuth();
    const [papers, setPapers] = createSignal<PaperTemplateResponse[]>([]);
    const [selected, setSelected] = createSignal<PaperTemplateResponse | null>(null);
    const [filters, setFilters] = createStore({ keyword: "", status: "" });
    const [form, setForm] = createStore({ title: "", instructions: "", shuffleQuestions: false, shuffleOptions: false });
    const [loading, setLoading] = createSignal(false);
    const [saving, setSaving] = createSignal(false);
    const [error, setError] = createSignal("");
    const [feedback, setFeedback] = createSignal("");

    const load = async () => {
        setLoading(true); setError("");
        try {
            const result = await api.listPapers({ page: 0, size: 100, keyword: filters.keyword || undefined, status: (filters.status || undefined) as PaperTemplateStatus | undefined });
            setPapers(result.content);
        } catch (cause) { setError(cause instanceof Error ? cause.message : "加载试卷失败。"); }
        finally { setLoading(false); }
    };
    createEffect(() => { void load(); });
    const createPaper = async (event: SubmitEvent) => {
        event.preventDefault(); if (saving()) return; if (!form.title.trim()) { setError("请填写试卷标题。"); return; }
        setSaving(true); setError("");
        try { await api.createPaper({ title: form.title.trim(), instructions: form.instructions.trim() || undefined, shuffleQuestions: form.shuffleQuestions, shuffleOptions: form.shuffleOptions }); setFeedback("试卷草稿已创建。"); setForm({ title: "", instructions: "", shuffleQuestions: false, shuffleOptions: false }); await load(); }
        catch (cause) { setError(cause instanceof Error ? cause.message : "创建试卷失败。"); }
        finally { setSaving(false); }
    };
    const copy = async (paper: PaperTemplateResponse) => { try { await api.copyPaper(paper.id, { title: `${paper.title}（副本）` }); setFeedback("试卷副本已创建。"); await load(); } catch (cause) { setError(cause instanceof Error ? cause.message : "复制试卷失败。"); } };
    const archive = async (paper: PaperTemplateResponse) => { if (!window.confirm(`确认归档“${paper.title}”？`)) return; try { await api.archivePaper(paper.id); setFeedback("试卷已归档。"); await load(); } catch (cause) { setError(cause instanceof Error ? cause.message : "归档试卷失败。"); } };
    const canEdit = (paper: PaperTemplateResponse) => {
        const user = auth.user();
        return Boolean(user && canManagePaper({ role: user.role, userId: user.id, ownerUserId: paper.ownerUserId }));
    };
    const canArchive = (paper: PaperTemplateResponse) => {
        const user = auth.user();
        return Boolean(user && canArchivePaper({ role: user.role, userId: user.id, ownerUserId: paper.ownerUserId }));
    };

    return <section class="space-y-5">
        <AssessmentNav />
        <PageHeader eyebrow="PAPER LIBRARY" title="试卷管理" description="创建可复用试卷模板，进入编辑器编排题目和分值。" actions={<Button variant="outline" aria-label="刷新试卷" onClick={() => void load()}><RefreshCw class="h-4 w-4" /></Button>} />
        <Show when={error()}><Alert class="border-destructive/30 bg-destructive/10 text-destructive">{error()}</Alert></Show><Show when={feedback()}><Alert class="border-success/30 bg-success/10 text-success">{feedback()}</Alert></Show>
        <form class="grid gap-4 rounded-md border bg-background p-4 lg:grid-cols-[1fr_1.4fr_auto]" onSubmit={createPaper}><div class="space-y-2"><Label for="paper-title">试卷标题</Label><Input id="paper-title" value={form.title} onInput={(e) => setForm("title", e.currentTarget.value)} /></div><div class="space-y-2"><Label for="paper-instructions">作答说明</Label><Textarea id="paper-instructions" class="min-h-10" value={form.instructions} onInput={(e) => setForm("instructions", e.currentTarget.value)} /></div><div class="flex flex-col justify-end gap-2"><label class="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.shuffleQuestions} onChange={(e) => setForm("shuffleQuestions", e.currentTarget.checked)} />打乱题目</label><label class="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.shuffleOptions} onChange={(e) => setForm("shuffleOptions", e.currentTarget.checked)} />打乱选项</label><Button type="submit" disabled={saving()}><FilePlus2 class="h-4 w-4" />{saving() ? "创建中..." : "创建试卷"}</Button></div></form>
        <div class="flex flex-wrap gap-3 rounded-md border bg-background p-3"><Input class="max-w-sm" aria-label="试卷关键词" placeholder="搜索试卷标题" value={filters.keyword} onInput={(e) => setFilters("keyword", e.currentTarget.value)} /><select aria-label="试卷状态" class="h-10 rounded-md border bg-background px-3 text-sm" value={filters.status} onChange={(e) => setFilters("status", e.currentTarget.value)}><option value="">全部状态</option><For each={Object.entries(statusLabel)}>{([value, label]) => <option value={value}>{label}</option>}</For></select><Button variant="outline" onClick={() => void load()}>筛选</Button></div>
        <div class="grid gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.7fr)]"><div class="overflow-x-auto rounded-md border bg-background"><Show when={!loading()} fallback={<p class="p-5 text-sm text-muted-foreground">正在加载试卷...</p>}><Show when={papers().length} fallback={<EmptyState title="暂无试卷" description="创建第一份试卷后，从共享题库添加试题。" />}><table class="w-full min-w-[720px] text-left text-sm"><thead class="bg-muted"><tr><th class="px-3 py-2">标题</th><th class="px-3 py-2">题数</th><th class="px-3 py-2">总分</th><th class="px-3 py-2">状态</th><th class="px-3 py-2 text-right">操作</th></tr></thead><tbody><For each={papers()}>{(paper) => <tr class="border-t"><td class="px-3 py-3"><p class="font-medium">{paper.title}</p><p class="text-xs text-muted-foreground">{paper.instructions || "无作答说明"}</p></td><td class="px-3 py-3">{paper.questionCount}</td><td class="px-3 py-3">{paper.totalScore}</td><td class="px-3 py-3"><Badge variant="outline">{statusLabel[paper.status]}</Badge></td><td class="px-3 py-2"><div class="flex justify-end gap-1"><Button size="icon" variant="ghost" aria-label={`预览 ${paper.title}`} title="预览" onClick={() => setSelected(paper)}><Eye class="h-4 w-4" /></Button><Show when={canEdit(paper)}><A class="inline-flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground" aria-label={`编辑 ${paper.title}`} title="编辑" href={`/papers/${paper.id}/edit`}><Pencil class="h-4 w-4" /></A></Show><Button size="icon" variant="ghost" aria-label={`复制 ${paper.title}`} title="复制" onClick={() => void copy(paper)}><Copy class="h-4 w-4" /></Button><Show when={canArchive(paper) && paper.status !== "ARCHIVED"}><Button size="icon" variant="ghost" aria-label={`归档 ${paper.title}`} title="归档" onClick={() => void archive(paper)}><Archive class="h-4 w-4" /></Button></Show></div></td></tr>}</For></tbody></table></Show></Show></div>
            <aside class="rounded-md border bg-background p-4"><Show when={selected()} fallback={<p class="text-sm text-muted-foreground">选择试卷查看配置与题目摘要。</p>}>{(paper) => <div class="space-y-3"><h2 class="text-lg font-semibold">{paper().title}</h2><div class="grid grid-cols-2 gap-2 text-sm"><p>题目：{paper().questionCount}</p><p>总分：{paper().totalScore}</p><p>打乱题目：{paper().shuffleQuestions ? "是" : "否"}</p><p>打乱选项：{paper().shuffleOptions ? "是" : "否"}</p></div><ol class="space-y-2"><For each={paper().questions}>{(question) => <li class="rounded-md bg-muted p-2 text-sm">{question.questionOrder}. {question.stem} <span class="text-muted-foreground">({question.score} 分)</span></li>}</For></ol></div>}</Show></aside>
        </div>
    </section>;
}
