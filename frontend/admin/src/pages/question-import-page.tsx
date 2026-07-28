import { CheckCircle2, FileSpreadsheet, Upload } from "lucide-solid";
import { createMemo, createSignal, For, Show } from "solid-js";
import { AssessmentNav } from "@/components/assessments/assessment-nav";
import { PageHeader } from "@/components/shared/page-header";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api } from "@/lib/api";
import type { QuestionImportPreviewResponse } from "@/types/api";

export function QuestionImportPage() {
    const [file, setFile] = createSignal<File | null>(null);
    const [preview, setPreview] = createSignal<QuestionImportPreviewResponse | null>(null);
    const [selectedIds, setSelectedIds] = createSignal<number[]>([]);
    const [busy, setBusy] = createSignal(false);
    const [error, setError] = createSignal("");
    const [feedback, setFeedback] = createSignal("");
    const selectableRows = createMemo(() => preview()?.rows.filter((row) => row.status !== "INVALID") ?? []);

    const previewFile = async () => {
        const selectedFile = file(); if (!selectedFile) { setError("请选择 CSV 文件。"); return; }
        setBusy(true); setError(""); setFeedback("");
        try {
            const result = await api.previewQuestionImport(selectedFile); setPreview(result);
            setSelectedIds(result.rows.filter((row) => row.status === "VALID").map((row) => row.id));
        } catch (cause) { setError(cause instanceof Error ? cause.message : "预览导入失败。"); }
        finally { setBusy(false); }
    };
    const confirmImport = async () => {
        const batch = preview(); if (!batch || batch.status !== "PREVIEWED" || !selectedIds().length || busy()) return;
        setBusy(true); setError("");
        try {
            const result = await api.confirmQuestionImport(batch.batchId, { selectedRowIds: selectedIds() });
            setPreview((current) => current ? { ...current, status: result.status } : current);
            setFeedback(`已导入 ${result.importedCount} 道试题。`);
        }
        catch (cause) { setError(cause instanceof Error ? cause.message : "确认导入失败。"); }
        finally { setBusy(false); }
    };
    const toggle = (id: number, checked: boolean) => setSelectedIds((ids) => checked ? [...ids, id] : ids.filter((item) => item !== id));

    return <section class="space-y-5">
        <AssessmentNav />
        <PageHeader eyebrow="CSV IMPORT" title="批量导入试题" description="先校验和预览，再选择有效行写入共享题库。" actions={<FileSpreadsheet class="h-5 w-5 text-primary" />} />
        <Show when={error()}><Alert class="border-destructive/30 bg-destructive/10 text-destructive">{error()}</Alert></Show><Show when={feedback()}><Alert class="border-success/30 bg-success/10 text-success">{feedback()}</Alert></Show>
        <div class="flex flex-wrap items-end gap-3 rounded-md border bg-background p-4"><div class="min-w-[280px] flex-1 space-y-2"><Label for="question-import-file">CSV 文件</Label><Input id="question-import-file" type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.currentTarget.files?.[0] ?? null)} /></div><Button disabled={!file() || busy()} onClick={() => void previewFile()}><Upload class="h-4 w-4" />{busy() ? "处理中..." : "预览导入"}</Button></div>
        <Show when={preview()}>{(batch) => <div class="space-y-3"><div class="grid gap-3 sm:grid-cols-4"><div class="rounded-md border bg-background p-3"><p class="text-xs text-muted-foreground">总行数</p><p class="text-xl font-semibold">{batch().totalRows}</p></div><div class="rounded-md border bg-background p-3"><p class="text-xs text-muted-foreground">有效</p><p class="text-xl font-semibold text-success">{batch().validRows}</p></div><div class="rounded-md border bg-background p-3"><p class="text-xs text-muted-foreground">无效</p><p class="text-xl font-semibold text-destructive">{batch().invalidRows}</p></div><div class="rounded-md border bg-background p-3"><p class="text-xs text-muted-foreground">重复候选</p><p class="text-xl font-semibold text-warning">{batch().duplicateRows}</p></div></div>
            <div class="overflow-x-auto rounded-md border bg-background"><table class="w-full min-w-[840px] text-left text-sm"><thead class="bg-muted"><tr><th class="px-3 py-2">选择</th><th class="px-3 py-2">行</th><th class="px-3 py-2">状态</th><th class="px-3 py-2">题型</th><th class="px-3 py-2">题干</th><th class="px-3 py-2">答案</th><th class="px-3 py-2">校验信息</th></tr></thead><tbody><For each={batch().rows}>{(row) => <tr class="border-t"><td class="px-3 py-2"><input aria-label={`选择第 ${row.rowNumber} 行`} type="checkbox" disabled={row.status === "INVALID" || batch().status !== "PREVIEWED"} checked={selectedIds().includes(row.id)} onChange={(e) => toggle(row.id, e.currentTarget.checked)} /></td><td class="px-3 py-2">{row.rowNumber}</td><td class="px-3 py-2"><Badge variant="outline">{row.status === "VALID" ? "有效" : row.status === "INVALID" ? "无效" : "重复候选"}</Badge></td><td class="px-3 py-2">{row.questionType ?? "-"}</td><td class="max-w-md px-3 py-2">{row.stem || "-"}</td><td class="px-3 py-2">{row.acceptedAnswers?.join("、") || "-"}</td><td class="px-3 py-2 text-destructive">{row.message || (row.duplicateQuestionId ? `可能重复：#${row.duplicateQuestionId}` : "-")}</td></tr>}</For></tbody></table></div>
            <div class="flex items-center justify-between"><p class="text-sm text-muted-foreground">已选择 {selectedIds().length} / {selectableRows().length} 行</p><Button disabled={busy() || !selectedIds().length || batch().status !== "PREVIEWED"} onClick={() => void confirmImport()}><CheckCircle2 class="h-4 w-4" />{batch().status === "CONFIRMED" ? "导入已确认" : `确认导入 ${selectedIds().length} 题`}</Button></div>
        </div>}</Show>
    </section>;
}
