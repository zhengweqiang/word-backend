import { X } from "lucide-solid";
import { createEffect, createMemo, createSignal, For, Index, onCleanup, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { api } from "@/lib/api";
import type {
    Dictionary,
    MetaWordEntryPayload,
    MetaWordSuggestionResponse,
    WordListProcessResult,
} from "@/types/api";

interface AddWordListModalProps {
    isOpen: boolean;
    dictionary: Dictionary;
    onClose: () => void;
    onSuccess?: () => void | Promise<void>;
}

type ManualEntryMode = "quick" | "bulk" | "json";

interface QuickEntryRow {
    id: string;
    word: string;
    translation: string;
    partOfSpeech: string;
    phonetic: string;
    definition: string;
    exampleSentence: string;
    difficulty: string;
    matchedMetaWordId?: number;
}

interface ParsedEntryResult {
    entries: MetaWordEntryPayload[];
    errors: string[];
    duplicateCount: number;
    lineCount: number;
}

const INITIAL_QUICK_ROW_COUNT = 1;
const MAX_ENTRY_COUNT = 1000;
const QUICK_SUGGESTION_LIMIT = 8;
const QUICK_SUGGESTION_DEBOUNCE_MS = 180;
const BULK_EXAMPLE = `apple | 苹果 | noun | /ˈaepəl/ | a fruit that grows on trees | I ate an apple for breakfast. | 2
abandon | 放弃 | verb | /əˈbaendən/ | to leave something behind | They abandoned the plan. | 3
phrase | 短语 | noun |  | a group of words used together | Learn the phrase by heart. | 2`;
const JSON_EXAMPLE = `[
  {
    "word": "apple",
    "translation": "苹果",
    "partOfSpeech": "noun",
    "phonetic": "/ˈaepəl/",
    "definition": "a fruit that grows on trees",
    "exampleSentence": "I ate an apple for breakfast.",
    "difficulty": 2
  }
]`;

function createQuickEntryRow(): QuickEntryRow {
    return {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
        word: "",
        translation: "",
        partOfSpeech: "",
        phonetic: "",
        definition: "",
        exampleSentence: "",
        difficulty: "",
        matchedMetaWordId: undefined,
    };
}

function createQuickRows(count: number) {
    return Array.from({ length: count }, () => createQuickEntryRow());
}

function trimToUndefined(value?: string) {
    const trimmed = value?.trim();
    return trimmed ? trimmed : undefined;
}

function parseDifficulty(rawValue: string | number | undefined, locationLabel: string): number | undefined {
    if (rawValue === undefined || rawValue === null || rawValue === "") {
        return undefined;
    }

    const value = typeof rawValue === "number" ? rawValue : Number(rawValue);
    if (!Number.isInteger(value) || value < 1 || value > 5) {
        throw new Error(`${locationLabel} 的难度必须是 1-5 的整数`);
    }

    return value;
}

function buildEntry(
    fields: {
        word?: string;
        translation?: string;
        partOfSpeech?: string;
        phonetic?: string;
        definition?: string;
        exampleSentence?: string;
        difficulty?: string | number;
    },
    locationLabel: string,
): MetaWordEntryPayload | null {
    const word = trimToUndefined(fields.word);
    const hasOtherContent = Boolean(
        trimToUndefined(fields.translation)
            || trimToUndefined(fields.partOfSpeech)
            || trimToUndefined(fields.phonetic)
            || trimToUndefined(fields.definition)
            || trimToUndefined(fields.exampleSentence)
            || (fields.difficulty !== undefined && fields.difficulty !== null && String(fields.difficulty).trim() !== ""),
    );

    if (!word) {
        if (hasOtherContent) {
            throw new Error(`${locationLabel} 缺少单词内容`);
        }
        return null;
    }

    const difficulty = parseDifficulty(fields.difficulty, locationLabel);
    return {
        word,
        translation: trimToUndefined(fields.translation),
        partOfSpeech: trimToUndefined(fields.partOfSpeech),
        phonetic: trimToUndefined(fields.phonetic),
        definition: trimToUndefined(fields.definition),
        exampleSentence: trimToUndefined(fields.exampleSentence),
        difficulty: difficulty ?? 2,
    };
}

function dedupeEntries(entries: MetaWordEntryPayload[]) {
    const uniqueEntries = new Map<string, MetaWordEntryPayload>();
    entries.forEach((entry) => {
        uniqueEntries.set(entry.word.trim().toLowerCase(), entry);
    });
    return Array.from(uniqueEntries.values());
}

function removeRecordKey<T>(record: Record<string, T>, key: string) {
    if (!(key in record)) {
        return record;
    }

    const nextRecord = { ...record };
    delete nextRecord[key];
    return nextRecord;
}

function buildSuggestionSummary(suggestion: MetaWordSuggestionResponse) {
    const summaryParts = [
        suggestion.translation?.trim(),
        suggestion.partOfSpeech?.trim(),
        suggestion.phonetic?.trim(),
        suggestion.definition?.trim(),
    ].filter((part): part is string => Boolean(part));

    if (summaryParts.length === 0) {
        return "词元表中已有该单词，可直接加入当前词书。";
    }

    return summaryParts.slice(0, 3).join(" · ");
}

function parseBulkText(input: string): ParsedEntryResult {
    const parsedEntries: MetaWordEntryPayload[] = [];
    const errors: string[] = [];
    let lineCount = 0;

    input.split(/\r?\n/).forEach((rawLine, index) => {
        const line = rawLine.trim();
        if (!line) {
            return;
        }

        lineCount += 1;

        const separator = line.includes("\t") ? "\t" : "|";
        const columns = line.split(separator).map((part) => part.trim());

        try {
            const entry = buildEntry(
                {
                    word: columns[0],
                    translation: columns[1],
                    partOfSpeech: columns[2],
                    phonetic: columns[3],
                    definition: columns[4],
                    exampleSentence: columns[5],
                    difficulty: columns[6],
                },
                `第 ${index + 1} 行`,
            );

            if (entry) {
                parsedEntries.push(entry);
            }
        } catch (error) {
            errors.push(error instanceof Error ? error.message : `第 ${index + 1} 行解析失败`);
        }
    });

    const entries = dedupeEntries(parsedEntries);
    return {
        entries,
        errors,
        duplicateCount: Math.max(parsedEntries.length - entries.length, 0),
        lineCount,
    };
}

function parseJsonEntries(input: string) {
    if (!input.trim()) {
        throw new Error("请输入 JSON 内容");
    }

    let parsed: unknown;
    try {
        parsed = JSON.parse(input);
    } catch {
        throw new Error("JSON 格式错误，请检查语法");
    }

    if (!Array.isArray(parsed)) {
        throw new Error("输入必须是 JSON 数组");
    }

    const entries = parsed
        .map((item, index) => {
            if (!item || typeof item !== "object") {
                throw new Error(`第 ${index + 1} 个条目必须是对象`);
            }

            const candidate = item as Record<string, unknown>;
            return buildEntry(
                {
                    word: typeof candidate.word === "string" ? candidate.word : undefined,
                    translation: typeof candidate.translation === "string" ? candidate.translation : undefined,
                    partOfSpeech: typeof candidate.partOfSpeech === "string" ? candidate.partOfSpeech : undefined,
                    phonetic: typeof candidate.phonetic === "string" ? candidate.phonetic : undefined,
                    definition: typeof candidate.definition === "string" ? candidate.definition : undefined,
                    exampleSentence: typeof candidate.exampleSentence === "string" ? candidate.exampleSentence : undefined,
                    difficulty:
                        typeof candidate.difficulty === "number" || typeof candidate.difficulty === "string"
                            ? candidate.difficulty
                            : undefined,
                },
                `第 ${index + 1} 个 JSON 条目`,
            );
        })
        .filter((entry): entry is MetaWordEntryPayload => entry !== null);

    return dedupeEntries(entries);
}

export function AddWordListModal(props: AddWordListModalProps) {
    const [mode, setMode] = createSignal<ManualEntryMode>("quick");
    const [quickRows, setQuickRows] = createSignal<QuickEntryRow[]>(createQuickRows(INITIAL_QUICK_ROW_COUNT));
    const [quickSuggestions, setQuickSuggestions] = createSignal<Record<string, MetaWordSuggestionResponse[]>>({});
    const [quickSuggestionLoading, setQuickSuggestionLoading] = createSignal<Record<string, boolean>>({});
    const [quickAiLoading, setQuickAiLoading] = createSignal<Record<string, boolean>>({});
    const [quickAiErrors, setQuickAiErrors] = createSignal<Record<string, string>>({});
    const [activeSuggestionRowId, setActiveSuggestionRowId] = createSignal<string | null>(null);
    const [bulkInput, setBulkInput] = createSignal("");
    const [jsonInput, setJsonInput] = createSignal("");
    const [loading, setLoading] = createSignal(false);
    const [error, setError] = createSignal<string | null>(null);
    const [result, setResult] = createSignal<WordListProcessResult | null>(null);
    const quickSuggestionTimers: Record<string, number> = {};
    const quickSuggestionAbortControllers: Record<string, AbortController | null> = {};
    const quickSuggestionRequestIds: Record<string, number> = {};

    const bulkPreview = createMemo(() => parseBulkText(bulkInput()));
    const quickFilledCount = createMemo(() => quickRows().filter((row) => row.word.trim().length > 0).length);
    const canSubmit = createMemo(() => {
        switch (mode()) {
            case "quick":
                return quickFilledCount() > 0;
            case "bulk":
                return bulkInput().trim().length > 0;
            case "json":
                return jsonInput().trim().length > 0;
        }
    });

    createEffect(() => {
        if (!props.isOpen) {
            return;
        }

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        onCleanup(() => {
            document.body.style.overflow = previousOverflow;
        });
    });

    onCleanup(() => {
        Object.values(quickSuggestionTimers).forEach((timerId) => window.clearTimeout(timerId));
        Object.values(quickSuggestionAbortControllers).forEach((controller) => controller?.abort());
    });

    const clearQuickSuggestionTimer = (rowId: string) => {
        const timerId = quickSuggestionTimers[rowId];
        if (timerId) {
            window.clearTimeout(timerId);
            delete quickSuggestionTimers[rowId];
        }
    };

    const abortQuickSuggestionRequest = (rowId: string) => {
        quickSuggestionAbortControllers[rowId]?.abort();
        delete quickSuggestionAbortControllers[rowId];
    };

    const clearQuickSuggestionState = (rowId: string) => {
        clearQuickSuggestionTimer(rowId);
        abortQuickSuggestionRequest(rowId);
        delete quickSuggestionRequestIds[rowId];
        setQuickSuggestions((previous) => removeRecordKey(previous, rowId));
        setQuickSuggestionLoading((previous) => removeRecordKey(previous, rowId));
        setActiveSuggestionRowId((currentRowId) => (currentRowId === rowId ? null : currentRowId));
    };

    const clearQuickAiState = (rowId: string) => {
        setQuickAiLoading((previous) => removeRecordKey(previous, rowId));
        setQuickAiErrors((previous) => removeRecordKey(previous, rowId));
    };

    const resetQuickSuggestionState = () => {
        Object.keys(quickSuggestionTimers).forEach((rowId) => clearQuickSuggestionTimer(rowId));
        Object.keys(quickSuggestionAbortControllers).forEach((rowId) => abortQuickSuggestionRequest(rowId));
        Object.keys(quickSuggestionRequestIds).forEach((rowId) => delete quickSuggestionRequestIds[rowId]);
        setQuickSuggestions({});
        setQuickSuggestionLoading({});
        setActiveSuggestionRowId(null);
    };

    const resetQuickAiState = () => {
        setQuickAiLoading({});
        setQuickAiErrors({});
    };

    const resetAll = () => {
        setMode("quick");
        setQuickRows(createQuickRows(INITIAL_QUICK_ROW_COUNT));
        resetQuickSuggestionState();
        resetQuickAiState();
        setBulkInput("");
        setJsonInput("");
        setLoading(false);
        setError(null);
        setResult(null);
    };

    const loadQuickSuggestions = async (rowId: string, keyword: string) => {
        const trimmedKeyword = keyword.trim();
        if (!trimmedKeyword) {
            clearQuickSuggestionState(rowId);
            return;
        }

        abortQuickSuggestionRequest(rowId);

        const requestId = (quickSuggestionRequestIds[rowId] ?? 0) + 1;
        quickSuggestionRequestIds[rowId] = requestId;

        const controller = new AbortController();
        quickSuggestionAbortControllers[rowId] = controller;

        setQuickSuggestionLoading((previous) => ({
            ...previous,
            [rowId]: true,
        }));

        try {
            const suggestions = await api.listDictionaryMetaWordSuggestions(
                props.dictionary.id,
                { keyword: trimmedKeyword, limit: QUICK_SUGGESTION_LIMIT },
                controller.signal,
            );

            if (quickSuggestionRequestIds[rowId] !== requestId) {
                return;
            }

            setQuickSuggestions((previous) => ({
                ...previous,
                [rowId]: suggestions,
            }));
        } catch (suggestionError) {
            if (!(suggestionError instanceof DOMException && suggestionError.name === "AbortError")) {
                console.error("Failed to load meta word suggestions:", suggestionError);
            }

            if (quickSuggestionRequestIds[rowId] !== requestId) {
                return;
            }

            setQuickSuggestions((previous) => removeRecordKey(previous, rowId));
        } finally {
            if (quickSuggestionRequestIds[rowId] === requestId) {
                setQuickSuggestionLoading((previous) => ({
                    ...previous,
                    [rowId]: false,
                }));
            }
        }
    };

    const resetCurrentMode = () => {
        if (mode() === "quick") {
            setQuickRows(createQuickRows(INITIAL_QUICK_ROW_COUNT));
            resetQuickSuggestionState();
            resetQuickAiState();
            return;
        }

        if (mode() === "bulk") {
            setBulkInput("");
            return;
        }

        setJsonInput("");
    };

    const handleModeChange = (nextMode: ManualEntryMode) => {
        setMode(nextMode);
        setError(null);
        setResult(null);
        if (nextMode !== "quick") {
            resetQuickSuggestionState();
            resetQuickAiState();
        }
    };

    const handleQuickRowChange = (rowId: string, field: keyof Omit<QuickEntryRow, "id">, value: string) => {
        setQuickRows((previousRows) =>
            previousRows.map((row) => (row.id === rowId ? { ...row, [field]: value } : row)),
        );
        setResult(null);
        setError(null);
        setQuickAiErrors((previous) => removeRecordKey(previous, rowId));
    };

    const handleQuickWordChange = (rowId: string, value: string) => {
        setQuickRows((previousRows) =>
            previousRows.map((row) =>
                row.id === rowId
                    ? {
                          ...row,
                          word: value,
                          matchedMetaWordId: undefined,
                      }
                    : row,
            ),
        );
        setResult(null);
        setError(null);
        setActiveSuggestionRowId(rowId);
        setQuickAiErrors((previous) => removeRecordKey(previous, rowId));

        clearQuickSuggestionTimer(rowId);
        abortQuickSuggestionRequest(rowId);
        delete quickSuggestionRequestIds[rowId];
        setQuickSuggestions((previous) => removeRecordKey(previous, rowId));

        const trimmedValue = value.trim();
        if (!trimmedValue) {
            clearQuickSuggestionState(rowId);
            return;
        }

        setQuickSuggestionLoading((previous) => ({
            ...previous,
            [rowId]: true,
        }));

        quickSuggestionTimers[rowId] = window.setTimeout(() => {
            void loadQuickSuggestions(rowId, trimmedValue);
        }, QUICK_SUGGESTION_DEBOUNCE_MS);
    };

    const handleApplySuggestion = (rowId: string, suggestion: MetaWordSuggestionResponse) => {
        clearQuickSuggestionState(rowId);
        clearQuickAiState(rowId);
        setQuickRows((previousRows) =>
            previousRows.map((row) =>
                row.id === rowId
                    ? {
                          ...row,
                          word: suggestion.word,
                          translation: suggestion.translation ?? row.translation,
                          partOfSpeech: suggestion.partOfSpeech ?? row.partOfSpeech,
                          phonetic: suggestion.phonetic ?? row.phonetic,
                          definition: suggestion.definition ?? row.definition,
                          exampleSentence: suggestion.exampleSentence ?? row.exampleSentence,
                          difficulty: suggestion.difficulty !== undefined ? String(suggestion.difficulty) : row.difficulty,
                          matchedMetaWordId: suggestion.id,
                      }
                    : row,
            ),
        );
        setResult(null);
        setError(null);
    };

    const handleQuickWordBlur = (rowId: string) => {
        window.setTimeout(() => {
            setActiveSuggestionRowId((currentRowId) => (currentRowId === rowId ? null : currentRowId));
        }, 120);
    };

    const handleRemoveQuickRow = (rowId: string) => {
        clearQuickSuggestionState(rowId);
        clearQuickAiState(rowId);
        setQuickRows((previousRows) => {
            if (previousRows.length === 1) {
                return previousRows;
            }
            return previousRows.filter((row) => row.id !== rowId);
        });
    };

    const handleAddQuickRow = (count = 1) => {
        setQuickRows((previousRows) => [...previousRows, ...createQuickRows(count)]);
    };

    const handleGenerateQuickRowWithAi = async (rowId: string) => {
        const targetRow = quickRows().find((candidate) => candidate.id === rowId);
        const word = targetRow?.word.trim() ?? "";

        if (!word) {
            setQuickAiErrors((previous) => ({
                ...previous,
                [rowId]: "请先输入单词，再使用 AI 补全。",
            }));
            return;
        }

        setQuickAiLoading((previous) => ({
            ...previous,
            [rowId]: true,
        }));
        setQuickAiErrors((previous) => removeRecordKey(previous, rowId));
        setError(null);
        setResult(null);

        try {
            const details = await api.generateDictionaryWordWithAi(props.dictionary.id, { word });
            setQuickRows((previousRows) =>
                previousRows.map((row) =>
                    row.id === rowId
                        ? {
                              ...row,
                              word: details.word?.trim() || row.word,
                              translation: details.translation?.trim() || row.translation,
                              partOfSpeech: details.partOfSpeech?.trim() || row.partOfSpeech,
                              phonetic: details.phonetic?.trim() || row.phonetic,
                              definition: details.definition?.trim() || row.definition,
                              exampleSentence: details.exampleSentence?.trim() || row.exampleSentence,
                              matchedMetaWordId: details.metaWordId,
                          }
                        : row,
                ),
            );
            setResult({
                total: details.total,
                existed: details.existed,
                created: details.created,
                added: details.added,
                failed: details.failed,
            });
            await props.onSuccess?.();
        } catch (aiError) {
            setQuickAiErrors((previous) => ({
                ...previous,
                [rowId]: aiError instanceof Error ? aiError.message : "AI 补全失败，请稍后重试。",
            }));
        } finally {
            setQuickAiLoading((previous) => ({
                ...previous,
                [rowId]: false,
            }));
        }
    };

    const handleFormatJson = () => {
        try {
            if (!jsonInput().trim()) {
                setError("请输入 JSON 内容");
                return;
            }

            const parsed = JSON.parse(jsonInput());
            setJsonInput(JSON.stringify(parsed, null, 2));
            setError(null);
        } catch (formatError) {
            if (formatError instanceof SyntaxError) {
                setError("JSON 格式错误，请检查引号、逗号和括号是否完整。");
                return;
            }

            setError("JSON 格式化失败");
        }
    };

    const buildQuickEntries = () =>
        dedupeEntries(
            quickRows()
                .map((row, index) =>
                    buildEntry(
                        {
                            word: row.word,
                            translation: row.translation,
                            partOfSpeech: row.partOfSpeech,
                            phonetic: row.phonetic,
                            definition: row.definition,
                            exampleSentence: row.exampleSentence,
                            difficulty: row.difficulty,
                        },
                        `第 ${index + 1} 行`,
                    ),
                )
                .filter((entry): entry is MetaWordEntryPayload => entry !== null),
        );

    const resolveEntriesForSubmission = () => {
        if (mode() === "quick") {
            return buildQuickEntries();
        }

        if (mode() === "bulk") {
            if (bulkPreview().errors.length > 0) {
                throw new Error(bulkPreview().errors.slice(0, 3).join("；"));
            }
            return bulkPreview().entries;
        }

        return parseJsonEntries(jsonInput());
    };

    const handleSubmit = async (event: SubmitEvent) => {
        event.preventDefault();
        setLoading(true);
        setError(null);
        setResult(null);

        try {
            const entries = resolveEntriesForSubmission();

            if (entries.length === 0) {
                throw new Error("请至少录入一个单词");
            }

            if (entries.length > MAX_ENTRY_COUNT) {
                throw new Error(`每次最多添加 ${MAX_ENTRY_COUNT} 个词条`);
            }

            const response = await api.addDictionaryWordList(props.dictionary.id, entries);
            setResult(response);
            resetCurrentMode();
            await props.onSuccess?.();
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "添加单词失败");
        } finally {
            setLoading(false);
        }
    };

    const handleClose = () => {
        resetAll();
        props.onClose();
    };

    const submitLabel = createMemo(() => {
        switch (mode()) {
            case "quick":
                return "保存当前录入";
            case "bulk":
                return "解析并添加";
            case "json":
                return "导入 JSON";
        }
    });

    return (
        <Show when={props.isOpen}>
            <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-sm" onClick={handleClose}>
                <div
                    aria-labelledby="add-word-dialog-title"
                    aria-modal="true"
                    class="flex max-h-[min(92vh,980px)] w-full max-w-6xl flex-col overflow-hidden rounded-[28px] border border-border/70 bg-background shadow-2xl"
                    role="dialog"
                    onClick={(event) => event.stopPropagation()}
                >
                <div class="flex items-start justify-between gap-4 border-b border-border/60 px-6 py-5">
                    <div>
                        <h2 class="font-display text-2xl font-semibold tracking-tight" id="add-word-dialog-title">
                            手动录入单词
                        </h2>
                        <p class="mt-2 text-sm leading-6 text-muted-foreground">
                            当前词书：<span class="font-medium text-foreground">{props.dictionary.name}</span>
                        </p>
                    </div>
                    <Button aria-label="关闭" size="sm" variant="ghost" onClick={handleClose}>
                        <X class="h-4 w-4" />
                    </Button>
                </div>

                <form class="flex min-h-0 flex-1 flex-col" onSubmit={handleSubmit}>
                    <div class="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-5">
                        <div class="grid gap-2 rounded-2xl border border-border/70 bg-muted/25 p-1 sm:grid-cols-3">
                            <For each={(["quick", "bulk", "json"] as ManualEntryMode[])}>
                                {(candidateMode) => (
                                    <button
                                        class={
                                            mode() === candidateMode
                                                ? "rounded-xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground shadow-sm"
                                                : "rounded-xl px-4 py-3 text-sm font-semibold text-muted-foreground transition hover:bg-background hover:text-foreground"
                                        }
                                        onClick={() => handleModeChange(candidateMode)}
                                        type="button"
                                    >
                                        {candidateMode === "quick" && "快速录入"}
                                        {candidateMode === "bulk" && "批量粘贴"}
                                        {candidateMode === "json" && "JSON 高级版"}
                                    </button>
                                )}
                            </For>
                        </div>

                        <div class="flex flex-wrap gap-2">
                            <Show when={mode() === "quick"}>
                                <>
                                    <Badge variant="outline">逐行填写，适合边查边录</Badge>
                                    <Badge variant="outline">{quickFilledCount()} 行待提交</Badge>
                                </>
                            </Show>
                            <Show when={mode() === "bulk"}>
                                <>
                                    <Badge variant="outline">支持 `|` 或制表符分列</Badge>
                                    <Badge variant="outline">{bulkPreview().entries.length} 条已解析</Badge>
                                    <Show when={bulkPreview().duplicateCount > 0}>
                                        <Badge variant="outline">{bulkPreview().duplicateCount} 条已自动去重</Badge>
                                    </Show>
                                </>
                            </Show>
                            <Show when={mode() === "json"}>
                                <>
                                    <Badge variant="outline">适合 AI 或脚本生成后直接粘贴</Badge>
                                    <Badge variant="outline">沿用现有 JSON 数据结构</Badge>
                                </>
                            </Show>
                        </div>

                        <Show when={mode() === "quick"}>
                            <>
                                <div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3 text-sm text-muted-foreground">
                                    建议至少填写“单词 + 中文释义”。系统会自动按单词去重，已存在的词会补充信息并加入当前词书。
                                </div>

                                <div class="flex flex-wrap justify-end gap-3">
                                    <Button disabled={loading()} variant="outline" onClick={() => handleAddQuickRow(1)} type="button">
                                        新增一行
                                    </Button>
                                    <Button disabled={loading()} variant="outline" onClick={() => handleAddQuickRow(5)} type="button">
                                        再加 5 行
                                    </Button>
                                    <Button disabled={loading()} variant="outline" onClick={resetCurrentMode} type="button">
                                        清空
                                    </Button>
                                </div>

                                <div class="space-y-3">
                                    <Index each={quickRows()}>
                                        {(row, index) => (
                                            <div class="grid gap-3 rounded-[24px] border border-border/70 bg-background/70 p-4 xl:grid-cols-[auto_minmax(0,1fr)_auto]">
                                                <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                                                    {index + 1}
                                                </div>
                                                <div class="space-y-3">
                                                    <div class="relative">
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行单词`}
                                                            autocomplete="off"
                                                            disabled={loading()}
                                                            placeholder="单词 *（输入时自动联想词元表）"
                                                            value={row().word}
                                                            onBlur={() => handleQuickWordBlur(row().id)}
                                                            onFocus={() => setActiveSuggestionRowId(row().id)}
                                                            onInput={(event) => handleQuickWordChange(row().id, event.currentTarget.value)}
                                                        />
                                                        <Show when={row().matchedMetaWordId}>
                                                            <p class="mt-2 text-xs leading-5 text-muted-foreground">
                                                                已从词元表匹配到该单词，释义信息已自动回填。
                                                            </p>
                                                        </Show>
                                                        <Show
                                                            when={
                                                                activeSuggestionRowId() === row().id
                                                                && row().word.trim().length > 0
                                                                && (quickSuggestionLoading()[row().id] || quickSuggestions()[row().id] !== undefined)
                                                            }
                                                        >
                                                            <div class="absolute inset-x-0 top-[calc(100%+0.5rem)] z-10 flex max-h-72 flex-col gap-2 overflow-y-auto rounded-2xl border border-border/70 bg-background p-2 shadow-xl">
                                                                <Show
                                                                    when={!quickSuggestionLoading()[row().id]}
                                                                    fallback={<div class="px-3 py-2 text-sm text-muted-foreground">正在匹配词元表...</div>}
                                                                >
                                                                    <Show
                                                                        when={(quickSuggestions()[row().id]?.length ?? 0) > 0}
                                                                        fallback={
                                                                            <div class="px-3 py-2 text-sm text-muted-foreground">
                                                                                词元表里暂时没有这个前缀的候选词，你也可以继续手动录入。
                                                                            </div>
                                                                        }
                                                                    >
                                                                        <For each={quickSuggestions()[row().id] ?? []}>
                                                                            {(suggestion) => (
                                                                                <button
                                                                                    class="rounded-2xl bg-muted/40 px-4 py-3 text-left transition hover:-translate-y-0.5 hover:bg-muted/70"
                                                                                    onMouseDown={(event) => {
                                                                                        event.preventDefault();
                                                                                        handleApplySuggestion(row().id, suggestion);
                                                                                    }}
                                                                                    type="button"
                                                                                >
                                                                                    <p class="text-sm font-semibold text-foreground">{suggestion.word}</p>
                                                                                    <p class="mt-1 text-xs leading-5 text-muted-foreground">
                                                                                        {buildSuggestionSummary(suggestion)}
                                                                                    </p>
                                                                                </button>
                                                                            )}
                                                                        </For>
                                                                    </Show>
                                                                </Show>
                                                            </div>
                                                        </Show>
                                                    </div>

                                                    <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行中文释义`}
                                                            disabled={loading()}
                                                            placeholder="中文释义"
                                                            value={row().translation}
                                                            onInput={(event) =>
                                                                handleQuickRowChange(row().id, "translation", event.currentTarget.value)
                                                            }
                                                        />
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行词性`}
                                                            disabled={loading()}
                                                            placeholder="词性"
                                                            value={row().partOfSpeech}
                                                            onInput={(event) =>
                                                                handleQuickRowChange(row().id, "partOfSpeech", event.currentTarget.value)
                                                            }
                                                        />
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行音标`}
                                                            disabled={loading()}
                                                            placeholder="音标"
                                                            value={row().phonetic}
                                                            onInput={(event) =>
                                                                handleQuickRowChange(row().id, "phonetic", event.currentTarget.value)
                                                            }
                                                        />
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行难度`}
                                                            disabled={loading()}
                                                            placeholder="难度 1-5"
                                                            value={row().difficulty}
                                                            onInput={(event) =>
                                                                handleQuickRowChange(row().id, "difficulty", event.currentTarget.value)
                                                            }
                                                        />
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行英文释义`}
                                                            class="md:col-span-2"
                                                            disabled={loading()}
                                                            placeholder="英文释义"
                                                            value={row().definition}
                                                            onInput={(event) =>
                                                                handleQuickRowChange(row().id, "definition", event.currentTarget.value)
                                                            }
                                                        />
                                                        <Input
                                                            aria-label={`第 ${index + 1} 行例句`}
                                                            class="md:col-span-2"
                                                            disabled={loading()}
                                                            placeholder="例句"
                                                            value={row().exampleSentence}
                                                            onInput={(event) =>
                                                                handleQuickRowChange(row().id, "exampleSentence", event.currentTarget.value)
                                                            }
                                                        />
                                                    </div>
                                                </div>
                                                <div class="flex min-w-[5.5rem] flex-col gap-3">
                                                    <Button
                                                        disabled={loading() || quickRows().length === 1}
                                                        variant="outline"
                                                        onClick={() => handleRemoveQuickRow(row().id)}
                                                        type="button"
                                                    >
                                                        移除
                                                    </Button>
                                                    <Button
                                                        disabled={loading() || quickAiLoading()[row().id] || row().word.trim().length === 0}
                                                        variant="secondary"
                                                        onClick={() => void handleGenerateQuickRowWithAi(row().id)}
                                                        type="button"
                                                    >
                                                        {quickAiLoading()[row().id] ? "AI中..." : "AI"}
                                                    </Button>
                                                    <Show when={quickAiErrors()[row().id]}>
                                                        <p class="text-center text-xs leading-5 text-destructive">
                                                            {quickAiErrors()[row().id]}
                                                        </p>
                                                    </Show>
                                                </div>
                                            </div>
                                        )}
                                    </Index>
                                </div>
                            </>
                        </Show>

                        <Show when={mode() === "bulk"}>
                            <>
                                <div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3 text-sm text-muted-foreground">
                                    每行一条记录，推荐顺序：`单词 | 中文释义 | 词性 | 音标 | 英文释义 | 例句 | 难度`。如果你是从表格里复制，直接粘贴制表符分列也可以。
                                </div>

                                <div class="flex flex-wrap justify-end gap-3">
                                    <Button disabled={loading()} variant="outline" onClick={() => setBulkInput(BULK_EXAMPLE)} type="button">
                                        填入示例
                                    </Button>
                                    <Button disabled={loading()} variant="outline" onClick={resetCurrentMode} type="button">
                                        清空
                                    </Button>
                                </div>

                                <Textarea
                                    class="min-h-[320px] font-mono text-[13px]"
                                    disabled={loading()}
                                    placeholder={BULK_EXAMPLE}
                                    value={bulkInput()}
                                    onInput={(event) => {
                                        setBulkInput(event.currentTarget.value);
                                        setError(null);
                                        setResult(null);
                                    }}
                                />

                                <Show when={bulkPreview().errors.length > 0}>
                                    <Alert class="border-destructive/30 bg-destructive/10 text-destructive">
                                        以下内容暂时无法解析：{bulkPreview().errors.slice(0, 5).join("；")}
                                    </Alert>
                                </Show>

                                <Show when={bulkPreview().entries.length > 0}>
                                    <div class="rounded-[24px] border border-border/70 bg-background/70 p-4">
                                        <div class="flex flex-wrap items-center justify-between gap-3">
                                            <p class="text-sm font-medium text-foreground">解析预览</p>
                                            <p class="text-xs text-muted-foreground">
                                                共 {bulkPreview().lineCount} 行，成功解析 {bulkPreview().entries.length} 条
                                            </p>
                                        </div>
                                        <div class="mt-4 space-y-2">
                                            <For each={bulkPreview().entries.slice(0, 6)}>
                                                {(entry) => (
                                                    <div class="grid gap-3 rounded-2xl bg-muted/40 px-4 py-3 md:grid-cols-[minmax(7rem,1.2fr)_minmax(10rem,2fr)_minmax(6rem,1fr)_auto]">
                                                        <strong class="text-foreground">{entry.word}</strong>
                                                        <span class="text-sm text-muted-foreground">{entry.translation || "未填中文释义"}</span>
                                                        <span class="text-sm text-muted-foreground">{entry.partOfSpeech || "未填词性"}</span>
                                                        <span class="text-sm text-muted-foreground">难度 {entry.difficulty ?? 2}</span>
                                                    </div>
                                                )}
                                            </For>
                                        </div>
                                        <Show when={bulkPreview().entries.length > 6}>
                                            <p class="mt-3 text-xs text-muted-foreground">
                                                仅展示前 6 条，提交时会完整处理全部解析结果。
                                            </p>
                                        </Show>
                                    </div>
                                </Show>
                            </>
                        </Show>

                        <Show when={mode() === "json"}>
                            <>
                                <div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3 text-sm text-muted-foreground">
                                    适合已经有结构化数据时直接粘贴。字段沿用现有接口：`word`、`translation`、`partOfSpeech`、`phonetic`、`definition`、`exampleSentence`、`difficulty`。
                                </div>

                                <div class="flex flex-wrap justify-end gap-3">
                                    <Button disabled={loading()} variant="outline" onClick={() => setJsonInput(JSON_EXAMPLE)} type="button">
                                        填入示例
                                    </Button>
                                    <Button disabled={loading()} variant="outline" onClick={handleFormatJson} type="button">
                                        格式化 JSON
                                    </Button>
                                    <Button disabled={loading()} variant="outline" onClick={resetCurrentMode} type="button">
                                        清空
                                    </Button>
                                </div>

                                <Textarea
                                    class="min-h-[340px] font-mono text-[13px]"
                                    disabled={loading()}
                                    placeholder={JSON_EXAMPLE}
                                    value={jsonInput()}
                                    onInput={(event) => {
                                        setJsonInput(event.currentTarget.value);
                                        setError(null);
                                        setResult(null);
                                    }}
                                />
                            </>
                        </Show>

                        <Show when={error()}>
                            {(message) => (
                                <Alert class="border-destructive/30 bg-destructive/10 text-destructive">
                                    {message()}
                                </Alert>
                            )}
                        </Show>

                        <Show when={result()}>
                            {(summary) => (
                                <div class="rounded-[24px] border border-emerald-500/25 bg-emerald-500/8 p-4">
                                    <p class="text-sm font-medium text-foreground">处理结果</p>
                                    <div class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
                                        <div class="rounded-2xl bg-background/70 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">总词条</p>
                                            <p class="mt-2 text-2xl font-semibold text-foreground">{summary().total}</p>
                                        </div>
                                        <div class="rounded-2xl bg-background/70 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">已存在元词</p>
                                            <p class="mt-2 text-2xl font-semibold text-foreground">{summary().existed}</p>
                                        </div>
                                        <div class="rounded-2xl bg-background/70 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">新建元词</p>
                                            <p class="mt-2 text-2xl font-semibold text-foreground">{summary().created}</p>
                                        </div>
                                        <div class="rounded-2xl bg-background/70 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">加入当前词书</p>
                                            <p class="mt-2 text-2xl font-semibold text-foreground">{summary().added}</p>
                                        </div>
                                        <div class="rounded-2xl bg-background/70 p-4">
                                            <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">失败</p>
                                            <p class="mt-2 text-2xl font-semibold text-foreground">{summary().failed}</p>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </Show>
                    </div>

                    <div class="flex flex-wrap items-center justify-end gap-3 border-t border-border/60 px-6 py-4">
                        <Button disabled={loading()} variant="outline" onClick={handleClose} type="button">
                            关闭
                        </Button>
                        <Button disabled={loading() || !canSubmit()} type="submit">
                            {loading() ? "处理中..." : submitLabel()}
                        </Button>
                    </div>
                </form>
            </div>
            </div>
        </Show>
    );
}
