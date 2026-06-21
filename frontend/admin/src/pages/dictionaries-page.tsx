import { ArrowDown, ArrowUp, ArrowUpDown, ChevronDown, ChevronRight, Plus, X } from "lucide-solid";
import { createEffect, createMemo, createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRoot,
    TableRow,
} from "@/components/ui/table";
import { AddWordListModal } from "@/components/dictionaries/add-word-list-modal";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { compactFileSize, formatDateTime } from "@/lib/format";
import type {
    Dictionary,
    DictionaryWordEntryResponse,
    PaginatedResponse,
} from "@/types/api";

interface DictionariesPageData {
    dictionaries: Dictionary[];
}

interface DictionaryTreeGroup {
    key: string;
    label: string;
    dictionaries: Dictionary[];
}

type DictionaryViewMode = "compact" | "tree";
type DictionaryEntrySortField = "entryOrder" | "word" | "translation" | "chapter";
type DictionaryEntrySortDirection = "asc" | "desc";

const WORD_PAGE_SIZE = 20;

const createDefaultForm = () => ({
    name: "",
    category: "",
});

const getDictionaryCategoryLabel = (dictionary: Dictionary) => dictionary.category?.trim() || "未分类";

const compareLabels = (left: string, right: string) => {
    if (left === "未分类") {
        return 1;
    }
    if (right === "未分类") {
        return -1;
    }
    return left.localeCompare(right, "zh-CN");
};

export function DictionariesPage() {
    const auth = useAuth();
    const [feedback, setFeedback] = createSignal("");
    const [entryAiLoading, setEntryAiLoading] = createSignal<Record<number, boolean>>({});
    const [isCreateDialogOpen, setIsCreateDialogOpen] = createSignal(false);
    const [selectedDictionaryId, setSelectedDictionaryId] = createSignal<number | null>(null);
    const [entryKeyword, setEntryKeyword] = createSignal("");
    const [entrySortBy, setEntrySortBy] = createSignal<DictionaryEntrySortField>("entryOrder");
    const [entrySortDir, setEntrySortDir] = createSignal<DictionaryEntrySortDirection>("asc");
    const [entryPage, setEntryPage] = createSignal(1);
    const [dictionaryKeyword, setDictionaryKeyword] = createSignal("");
    const [dictionaryViewMode, setDictionaryViewMode] = createSignal<DictionaryViewMode>("compact");
    const [isAddWordDialogOpen, setIsAddWordDialogOpen] = createSignal(false);
    const [form, setForm] = createStore(createDefaultForm());
    const [expandedGroups, setExpandedGroups] = createStore<Record<string, boolean>>({});

    const [pageData, { refetch }] = createResource(
        () => auth.user(),
        async (user): Promise<DictionariesPageData | null> => {
            if (!user) {
                return null;
            }
            const dictionaries = await api.listDictionaries();
            return { dictionaries };
        },
    );

    const filteredDictionaries = createMemo(() => {
        const keyword = dictionaryKeyword().trim().toLocaleLowerCase("zh-CN");
        const dictionaries = pageData()?.dictionaries ?? [];

        if (!keyword) {
            return dictionaries;
        }

        return dictionaries.filter((dictionary) => {
            const searchText = [
                dictionary.name,
                getDictionaryCategoryLabel(dictionary),
                dictionary.scopeType || "",
            ]
                .join(" ")
                .toLocaleLowerCase("zh-CN");
            return searchText.includes(keyword);
        });
    });

    const groupedDictionaries = createMemo<DictionaryTreeGroup[]>(() => {
        const dictionaries = filteredDictionaries();
        const groups = new Map<string, Dictionary[]>();

        for (const dictionary of dictionaries) {
            const label = getDictionaryCategoryLabel(dictionary);
            const current = groups.get(label) ?? [];
            current.push(dictionary);
            groups.set(label, current);
        }

        return Array.from(groups.entries())
            .sort(([left], [right]) => compareLabels(left, right))
            .map(([label, items]) => ({
                key: label,
                label,
                dictionaries: items.sort((left, right) => left.name.localeCompare(right.name, "zh-CN")),
            }));
    });

    const orderedDictionaries = createMemo(() => groupedDictionaries().flatMap((group) => group.dictionaries));
    const totalDictionaryCount = createMemo(() => pageData()?.dictionaries.length ?? 0);

    createEffect(() => {
        for (const group of groupedDictionaries()) {
            if (expandedGroups[group.key] === undefined) {
                setExpandedGroups(group.key, true);
            }
        }
    });

    createEffect(() => {
        const dictionaries = orderedDictionaries();
        if (dictionaries.length === 0) {
            setSelectedDictionaryId(null);
            return;
        }

        const currentSelection = selectedDictionaryId();
        if (!currentSelection || !dictionaries.some((dictionary) => dictionary.id === currentSelection)) {
            setSelectedDictionaryId(dictionaries[0].id);
        }
    });

    const selectedDictionary = createMemo(
        () => pageData()?.dictionaries.find((dictionary) => dictionary.id === selectedDictionaryId()) ?? null,
    );
    const canManageSelectedDictionary = createMemo(() => {
        const user = auth.user();
        const dictionary = selectedDictionary();
        if (!user || !dictionary) {
            return false;
        }
        if (user.role === "ADMIN") {
            return true;
        }
        return user.role === "TEACHER"
            && dictionary.scopeType !== "SYSTEM"
            && dictionary.ownerUserId === user.id;
    });

    const selectedDictionaryEntriesRequest = createMemo(() => {
        const dictionaryId = selectedDictionaryId();
        if (!dictionaryId) {
            return null;
        }

        return {
            dictionaryId,
            page: entryPage(),
            size: WORD_PAGE_SIZE,
            keyword: entryKeyword().trim() || undefined,
            sortBy: entrySortBy(),
            sortDir: entrySortDir(),
        };
    });

    const [entriesPageData, { refetch: refetchEntries }] = createResource(
        selectedDictionaryEntriesRequest,
        async (params): Promise<PaginatedResponse<DictionaryWordEntryResponse> | null> => {
            if (!params) {
                return null;
            }
            return api.listDictionaryEntriesPage(params.dictionaryId, {
                page: params.page,
                size: params.size,
                keyword: params.keyword,
                sortBy: params.sortBy,
                sortDir: params.sortDir,
            });
        },
    );

    const entryPageSummary = createMemo(() => {
        const current = entriesPageData();
        if (!current || current.totalElements === 0) {
            return "暂无单词数据";
        }
        const start = current.number * current.size + 1;
        const end = start + current.numberOfElements - 1;
        return `第 ${start}-${end} 条，共 ${current.totalElements} 个词条`;
    });

    const entryTotalPages = createMemo(() => Math.max(1, entriesPageData()?.totalPages ?? 1));

    const closeCreateDialog = () => {
        setIsCreateDialogOpen(false);
        setForm(createDefaultForm());
    };

    const handleCreate = async (event: SubmitEvent) => {
        event.preventDefault();
        setFeedback("");
        const createdDictionary = await api.createDictionary({
            name: form.name.trim(),
            category: form.category.trim() || undefined,
        });
        setFeedback("词书已创建。");
        closeCreateDialog();
        await refetch();
        setSelectedDictionaryId(createdDictionary.id);
        setEntryPage(1);
    };

    const handleSelectDictionary = (dictionaryId: number) => {
        setSelectedDictionaryId(dictionaryId);
        setEntryPage(1);
        setEntryKeyword("");
        setEntrySortBy("entryOrder");
        setEntrySortDir("asc");
        setIsAddWordDialogOpen(false);
    };

    const handleEntryKeywordInput = (value: string) => {
        setEntryKeyword(value);
        setEntryPage(1);
    };

    const handleEntrySortToggle = (field: DictionaryEntrySortField) => {
        if (entrySortBy() === field) {
            setEntrySortDir((current) => (current === "asc" ? "desc" : "asc"));
        } else {
            setEntrySortBy(field);
            setEntrySortDir("asc");
        }
        setEntryPage(1);
    };

    const getEntrySortLabel = (field: DictionaryEntrySortField) => {
        switch (field) {
            case "word":
                return "单词";
            case "translation":
                return "释义";
            case "chapter":
                return "章节";
            case "entryOrder":
            default:
                return "顺序";
        }
    };

    const SortableEntryHeader = (props: { field: DictionaryEntrySortField; label: string; class?: string }) => {
        const isActive = () => entrySortBy() === props.field;

        return (
            <TableHeaderCell class={props.class}>
                <button
                    type="button"
                    class="flex w-full items-center gap-2 text-left text-inherit transition-colors hover:text-foreground"
                    aria-label={`按${props.label}${isActive() && entrySortDir() === "asc" ? "降序" : "升序"}排序`}
                    onClick={() => handleEntrySortToggle(props.field)}
                >
                    <span>{props.label}</span>
                    <Show
                        when={isActive()}
                        fallback={<ArrowUpDown class="h-3.5 w-3.5 text-muted-foreground/80" />}
                    >
                        <Show
                            when={entrySortDir() === "asc"}
                            fallback={<ArrowDown class="h-3.5 w-3.5 text-foreground" />}
                        >
                            <ArrowUp class="h-3.5 w-3.5 text-foreground" />
                        </Show>
                    </Show>
                </button>
            </TableHeaderCell>
        );
    };

    const handleRefresh = async () => {
        await refetch();
        if (selectedDictionaryId()) {
            await refetchEntries();
        }
    };

    const handleEntryAiGenerate = async (entry: DictionaryWordEntryResponse) => {
        if (!entry.word || !selectedDictionaryId()) {
            setFeedback("当前词条缺少单词内容，无法使用单词AI。");
            return;
        }

        setEntryAiLoading((previous) => ({
            ...previous,
            [entry.entryId]: true,
        }));
        setFeedback("");

        try {
            const response = await api.generateDictionaryWordWithAi(selectedDictionaryId()!, {
                metaWordId: entry.metaWordId,
                word: entry.word,
            });
            await Promise.all([refetch(), refetchEntries()]);
            setFeedback(
                response.added > 0
                    ? `单词AI已生成并保存，已同步到当前词书：${response.word}`
                    : `单词AI已更新元单词数据：${response.word}`,
            );
        } catch (error) {
            setFeedback(error instanceof Error ? error.message : "单词AI处理失败");
        } finally {
            setEntryAiLoading((previous) => ({
                ...previous,
                [entry.entryId]: false,
            }));
        }
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Dictionaries"
                title="词书资源"
                description="左侧集中浏览词书树，右侧查看当前词书的单词清单、搜索结果和排序状态。"
                actions={
                    <div class="flex flex-wrap items-center gap-3">
                        <Button onClick={() => setIsCreateDialogOpen(true)}>
                            <Plus class="h-4 w-4" />
                            添加辞书
                        </Button>
                        <Button variant="outline" onClick={() => void handleRefresh()}>
                            刷新
                        </Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show
                when={pageData()}
                fallback={
                    <Card>
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载词书数据...</CardContent>
                    </Card>
                }
            >
                <div class="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
                    <Card class="h-fit xl:sticky xl:top-6">
                        <CardHeader class="gap-4">
                            <div>
                                <CardTitle>词书导航</CardTitle>
                                <CardDescription>支持树形和紧凑两种浏览方式，右侧联动查看词条详情。</CardDescription>
                            </div>
                            <div class="space-y-3">
                                <Input
                                    placeholder="筛选词书名、分类或归属"
                                    value={dictionaryKeyword()}
                                    onInput={(event) => setDictionaryKeyword(event.currentTarget.value)}
                                />
                                <div class="flex items-center justify-between gap-3">
                                    <p class="text-xs text-muted-foreground">
                                        显示 {orderedDictionaries().length} / {totalDictionaryCount()} 本
                                    </p>
                                    <div class="inline-flex rounded-xl border border-border/70 bg-background/70 p-1">
                                        <button
                                            class={
                                                dictionaryViewMode() === "compact"
                                                    ? "rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground"
                                                    : "rounded-lg px-3 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-muted/60 hover:text-foreground"
                                            }
                                            onClick={() => setDictionaryViewMode("compact")}
                                            type="button"
                                        >
                                            紧凑
                                        </button>
                                        <button
                                            class={
                                                dictionaryViewMode() === "tree"
                                                    ? "rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground"
                                                    : "rounded-lg px-3 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-muted/60 hover:text-foreground"
                                            }
                                            onClick={() => setDictionaryViewMode("tree")}
                                            type="button"
                                        >
                                            树形
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </CardHeader>
                        <CardContent class="max-h-[28rem] overflow-y-auto pr-2 xl:max-h-[calc(100vh-9rem)]">
                            <Show
                                when={totalDictionaryCount() > 0}
                                fallback={
                                    <EmptyState
                                        title="暂无词书"
                                        description="可以先通过右上角按钮添加词书，再进入词条查看。"
                                    />
                                }
                            >
                                <Show
                                    when={groupedDictionaries().length > 0}
                                    fallback={
                                        <EmptyState
                                            title="没有匹配的词书"
                                            description="换个筛选词，或者切换回全部词书继续浏览。"
                                        />
                                    }
                                >
                                    <Show
                                        when={dictionaryViewMode() === "compact"}
                                        fallback={
                                            <div class="space-y-3">
                                                <For each={groupedDictionaries()}>
                                                    {(group) => {
                                                        const isExpanded = () => expandedGroups[group.key] ?? true;
                                                        return (
                                                            <div class="rounded-2xl border border-border/70 bg-background/70">
                                                                <button
                                                                    class="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left"
                                                                    onClick={() => setExpandedGroups(group.key, !isExpanded())}
                                                                    type="button"
                                                                >
                                                                    <div class="flex items-center gap-2">
                                                                        <Show when={isExpanded()} fallback={<ChevronRight class="h-4 w-4 text-muted-foreground" />}>
                                                                            <ChevronDown class="h-4 w-4 text-muted-foreground" />
                                                                        </Show>
                                                                        <span class="text-sm font-medium text-foreground">{group.label}</span>
                                                                    </div>
                                                                    <Badge variant="outline">{group.dictionaries.length}</Badge>
                                                                </button>

                                                                <Show when={isExpanded()}>
                                                                    <div class="space-y-2 border-t border-border/60 p-2">
                                                                        <For each={group.dictionaries}>
                                                                            {(dictionary) => {
                                                                                const isActive = () => dictionary.id === selectedDictionaryId();
                                                                                return (
                                                                                    <button
                                                                                        class={
                                                                                            isActive()
                                                                                                ? "w-full rounded-xl border border-primary/35 bg-primary/10 px-3 py-2.5 text-left"
                                                                                                : "w-full rounded-xl border border-transparent px-3 py-2.5 text-left transition hover:border-border/70 hover:bg-muted/50"
                                                                                        }
                                                                                        onClick={() => handleSelectDictionary(dictionary.id)}
                                                                                        type="button"
                                                                                    >
                                                                                        <div class="flex items-center justify-between gap-3">
                                                                                            <div class="min-w-0">
                                                                                                <p class="truncate text-sm font-medium text-foreground">
                                                                                                    {dictionary.name}
                                                                                                </p>
                                                                                                <p class="mt-1 text-xs text-muted-foreground">
                                                                                                    {dictionary.wordCount || 0} 词
                                                                                                    · {dictionary.scopeType || "SYSTEM"}
                                                                                                </p>
                                                                                            </div>
                                                                                            <Badge variant={isActive() ? "default" : "outline"}>
                                                                                                {dictionary.entryCount || 0}
                                                                                            </Badge>
                                                                                        </div>
                                                                                    </button>
                                                                                );
                                                                            }}
                                                                        </For>
                                                                    </div>
                                                                </Show>
                                                            </div>
                                                        );
                                                    }}
                                                </For>
                                            </div>
                                        }
                                    >
                                        <div class="space-y-3">
                                            <For each={groupedDictionaries()}>
                                                {(group) => (
                                                    <section class="rounded-2xl border border-border/70 bg-background/70 p-3">
                                                        <div class="mb-3 flex items-center justify-between gap-3">
                                                            <div class="min-w-0">
                                                                <p class="truncate text-sm font-medium text-foreground">
                                                                    {group.label}
                                                                </p>
                                                                <p class="text-xs text-muted-foreground">
                                                                    点击词书标签可直接切换
                                                                </p>
                                                            </div>
                                                            <Badge variant="outline">{group.dictionaries.length}</Badge>
                                                        </div>
                                                        <div class="flex flex-wrap gap-2">
                                                            <For each={group.dictionaries}>
                                                                {(dictionary) => {
                                                                    const isActive = () => dictionary.id === selectedDictionaryId();
                                                                    return (
                                                                        <button
                                                                            class={
                                                                                isActive()
                                                                                    ? "inline-flex max-w-full items-center gap-2 rounded-full border border-primary/40 bg-primary/10 px-3 py-1.5 text-sm text-primary"
                                                                                    : "inline-flex max-w-full items-center gap-2 rounded-full border border-border/70 bg-background/85 px-3 py-1.5 text-sm text-foreground transition hover:border-border hover:bg-muted/60"
                                                                            }
                                                                            onClick={() => handleSelectDictionary(dictionary.id)}
                                                                            type="button"
                                                                        >
                                                                            <span class="max-w-[11rem] truncate font-medium">
                                                                                {dictionary.name}
                                                                            </span>
                                                                            <span
                                                                                class={
                                                                                    isActive()
                                                                                        ? "rounded-full bg-primary/15 px-2 py-0.5 text-[11px] font-medium text-primary"
                                                                                        : "rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground"
                                                                                }
                                                                            >
                                                                                {dictionary.wordCount || 0} 词
                                                                            </span>
                                                                        </button>
                                                                    );
                                                                }}
                                                            </For>
                                                        </div>
                                                    </section>
                                                )}
                                            </For>
                                        </div>
                                    </Show>
                                </Show>
                            </Show>
                        </CardContent>
                    </Card>

                    <div class="space-y-6">
                        <Show
                            when={selectedDictionary()}
                            fallback={
                                <Card>
                                    <CardContent class="p-10">
                                        <EmptyState
                                            title="请选择词书"
                                            description="从左侧词书树选择一个词书后，这里会展示词条表格。"
                                        />
                                    </CardContent>
                                </Card>
                            }
                        >
                            {(dictionary) => (
                                <>
                                    <Card>
                                        <CardHeader class="gap-4">
                                            <div class="flex flex-wrap items-start justify-between gap-4">
                                                <div>
                                                    <CardTitle>{dictionary().name}</CardTitle>
                                                    <CardDescription>
                                                        {getDictionaryCategoryLabel(dictionary())} · 最近更新
                                                        于 {formatDateTime(dictionary().updatedAt || dictionary().createdAt)}
                                                    </CardDescription>
                                                </div>
                                                <div class="flex flex-wrap items-center gap-2">
                                                    <Badge variant="outline">{dictionary().scopeType || "SYSTEM"}</Badge>
                                                    <Badge variant="outline">{getDictionaryCategoryLabel(dictionary())}</Badge>
                                                </div>
                                            </div>
                                        </CardHeader>
                                        <CardContent class="space-y-4">
                                            <div class="grid gap-3 lg:grid-cols-4">
                                                <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                    <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Words</p>
                                                    <p class="mt-2 font-display text-2xl font-semibold">
                                                        {dictionary().wordCount || 0}
                                                    </p>
                                                </div>
                                                <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                    <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Entries</p>
                                                    <p class="mt-2 font-display text-2xl font-semibold">
                                                        {dictionary().entryCount || 0}
                                                    </p>
                                                </div>
                                                <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                    <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Size</p>
                                                    <p class="mt-2 font-display text-2xl font-semibold">
                                                        {compactFileSize(dictionary().fileSize)}
                                                    </p>
                                                </div>
                                                <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                    <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Created</p>
                                                    <p class="mt-2 text-sm text-muted-foreground">
                                                        {formatDateTime(dictionary().createdAt)}
                                                    </p>
                                                </div>
                                            </div>

                                            <div class="rounded-2xl border border-dashed border-border/70 bg-background/50 p-4 text-sm text-muted-foreground">
                                                词书归属班级的维护已移至“班级管理”，请在对应班级详情中添加或移除词书。
                                            </div>
                                        </CardContent>
                                    </Card>

                                    <Card>
                                        <CardHeader class="gap-4">
                                            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
                                                <div class="space-y-4">
                                                    <div>
                                                        <CardTitle>词书单词表</CardTitle>
                                                        <CardDescription>
                                                            支持按关键词搜索，并可直接点击表头按单词、释义、章节或录入顺序排序。
                                                        </CardDescription>
                                                    </div>
                                                    <div class="rounded-2xl border border-dashed border-border/70 bg-background/60 p-4">
                                                        <div class="flex flex-wrap items-start justify-between gap-3">
                                                            <div>
                                                                <p class="text-sm font-medium text-foreground">添加单词</p>
                                                                <p class="mt-1 text-xs leading-5 text-muted-foreground">
                                                                    在浮层里按行录入、批量粘贴或直接粘贴 JSON，交互与用户端保持一致。
                                                                </p>
                                                            </div>
                                                            <Badge variant="outline">弹层录入</Badge>
                                                        </div>

                                                        <Show
                                                            when={canManageSelectedDictionary()}
                                                            fallback={
                                                                <Alert class="mt-4 border-border/80 bg-background/70 text-muted-foreground">
                                                                    当前词书仅支持查看，不能直接添加单词。教师只能编辑自己创建的非系统词书。
                                                                </Alert>
                                                            }
                                                        >
                                                            <div class="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border/70 bg-background/80 p-4">
                                                                <div class="space-y-1">
                                                                    <p class="text-sm font-medium text-foreground">打开添加单词浮层</p>
                                                                    <p class="text-xs leading-5 text-muted-foreground">
                                                                        支持快速录入、批量粘贴和 JSON 导入，提交后会自动刷新当前词书单词表。
                                                                    </p>
                                                                </div>
                                                                <Button onClick={() => setIsAddWordDialogOpen(true)}>
                                                                    <Plus class="h-4 w-4" />
                                                                    添加单词
                                                                </Button>
                                                            </div>
                                                        </Show>
                                                    </div>
                                                </div>
                                                <div class="w-full xl:w-[360px]">
                                                    <div class="space-y-2">
                                                        <Label>搜索</Label>
                                                        <Input
                                                            placeholder="搜索单词、释义、定义或章节"
                                                            value={entryKeyword()}
                                                            onInput={(event) =>
                                                                handleEntryKeywordInput(
                                                                    event.currentTarget.value,
                                                                )
                                                            }
                                                        />
                                                    </div>
                                                </div>
                                            </div>
                                        </CardHeader>
                                        <CardContent>
                                            <Show
                                                when={entriesPageData()}
                                                fallback={
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-6 text-sm text-muted-foreground">
                                                        正在加载词条数据...
                                                    </div>
                                                }
                                            >
                                                {(entries) => (
                                                    <Show
                                                        when={entries().content.length > 0}
                                                        fallback={
                                                            <EmptyState
                                                                title="暂无匹配词条"
                                                                description="当前词书没有单词，或搜索条件没有匹配结果。"
                                                            />
                                                        }
                                                    >
                                                        <Table>
                                                            <div class="overflow-x-auto">
                                                                <TableRoot>
                                                                    <TableHead>
                                                                        <tr>
                                                                            <SortableEntryHeader field="word" label="单词" />
                                                                            <SortableEntryHeader field="translation" label="释义" />
                                                                            <TableHeaderCell>音标</TableHeaderCell>
                                                                            <SortableEntryHeader field="chapter" label="章节" />
                                                                            <SortableEntryHeader field="entryOrder" label="顺序" />
                                                                            <TableHeaderCell class="w-[120px]">操作</TableHeaderCell>
                                                                        </tr>
                                                                    </TableHead>
                                                                    <TableBody>
                                                                        <For each={entries().content}>
                                                                            {(entry) => (
                                                                                <TableRow>
                                                                                    <TableCell class="min-w-[220px]">
                                                                                        <div>
                                                                                            <p class="font-medium text-foreground">
                                                                                                {entry.word || "-"}
                                                                                            </p>
                                                                                            <Show
                                                                                                when={entry.definition}
                                                                                            >
                                                                                                <p class="mt-1 line-clamp-2 text-xs text-muted-foreground">
                                                                                                    {entry.definition}
                                                                                                </p>
                                                                                            </Show>
                                                                                        </div>
                                                                                    </TableCell>
                                                                                    <TableCell class="min-w-[220px] text-sm text-muted-foreground">
                                                                                        {entry.translation || "-"}
                                                                                    </TableCell>
                                                                                    <TableCell class="min-w-[140px] text-sm text-muted-foreground">
                                                                                        {entry.phonetic || "-"}
                                                                                    </TableCell>
                                                                                    <TableCell class="min-w-[220px] text-sm text-muted-foreground">
                                                                                        {entry.chapterDisplayPath || "默认章节"}
                                                                                    </TableCell>
                                                                                    <TableCell class="w-[100px] text-sm text-muted-foreground">
                                                                                        {entry.entryOrder || "-"}
                                                                                    </TableCell>
                                                                                    <TableCell class="w-[120px]">
                                                                                        <Show when={canManageSelectedDictionary()}>
                                                                                            <Button
                                                                                                disabled={entryAiLoading()[entry.entryId] || !entry.word}
                                                                                                size="sm"
                                                                                                variant="outline"
                                                                                                onClick={() => void handleEntryAiGenerate(entry)}
                                                                                            >
                                                                                                {entryAiLoading()[entry.entryId] ? "AI中..." : "单词AI"}
                                                                                            </Button>
                                                                                        </Show>
                                                                                    </TableCell>
                                                                                </TableRow>
                                                                            )}
                                                                        </For>
                                                                    </TableBody>
                                                                </TableRoot>
                                                            </div>
                                                        </Table>

                                                        <div class="mt-5 flex flex-col gap-3 border-t border-border/60 pt-4 md:flex-row md:items-center md:justify-between">
                                                            <p class="text-sm text-muted-foreground">
                                                                {entryPageSummary()} · 当前按{getEntrySortLabel(entrySortBy())}
                                                                {entrySortDir() === "asc" ? "升序" : "降序"}
                                                            </p>
                                                            <div class="flex items-center gap-2">
                                                                <Button
                                                                    disabled={entryPage() === 1}
                                                                    size="sm"
                                                                    variant="outline"
                                                                    onClick={() =>
                                                                        setEntryPage((page) =>
                                                                            Math.max(1, page - 1),
                                                                        )
                                                                    }
                                                                >
                                                                    上一页
                                                                </Button>
                                                                <span class="min-w-[88px] text-center text-sm text-muted-foreground">
                                                                    {entryPage()} / {entryTotalPages()}
                                                                </span>
                                                                <Button
                                                                    disabled={
                                                                        entryPage() === entryTotalPages()
                                                                    }
                                                                    size="sm"
                                                                    variant="outline"
                                                                    onClick={() =>
                                                                        setEntryPage((page) =>
                                                                            Math.min(
                                                                                entryTotalPages(),
                                                                                page + 1,
                                                                            ),
                                                                        )
                                                                    }
                                                                >
                                                                    下一页
                                                                </Button>
                                                            </div>
                                                        </div>
                                                    </Show>
                                                )}
                                            </Show>
                                        </CardContent>
                                    </Card>
                                </>
                            )}
                        </Show>
                    </div>
                </div>
            </Show>

            <Show when={selectedDictionary()}>
                {(dictionary) => (
                    <AddWordListModal
                        dictionary={dictionary()}
                        isOpen={isAddWordDialogOpen()}
                        onClose={() => setIsAddWordDialogOpen(false)}
                        onSuccess={async () => {
                            await Promise.all([refetch(), refetchEntries()]);
                        }}
                    />
                )}
            </Show>

            <Show when={isCreateDialogOpen()}>
                <div
                    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-sm"
                    onClick={closeCreateDialog}
                >
                    <div
                        aria-labelledby="create-dictionary-dialog-title"
                        aria-modal="true"
                        class="w-full max-w-xl rounded-[28px] border border-border/70 bg-background p-6 shadow-2xl"
                        role="dialog"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="font-display text-2xl font-semibold tracking-tight" id="create-dictionary-dialog-title">
                                    添加辞书
                                </h2>
                                <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                    适合老师创建自己的专题词书，也适合管理员补充系统资源。
                                </p>
                            </div>
                            <Button aria-label="关闭" size="sm" variant="ghost" onClick={closeCreateDialog}>
                                <X class="h-4 w-4" />
                            </Button>
                        </div>

                        <form class="mt-6 grid gap-4" onSubmit={handleCreate}>
                            <div class="space-y-2">
                                <Label>词书名称</Label>
                                <Input
                                    required
                                    value={form.name}
                                    onInput={(event) => setForm("name", event.currentTarget.value)}
                                />
                            </div>
                            <div class="space-y-2">
                                <Label>分类</Label>
                                <Input
                                    placeholder="例如：高考 / 考研 / 自定义"
                                    value={form.category}
                                    onInput={(event) => setForm("category", event.currentTarget.value)}
                                />
                            </div>
                            <div class="flex flex-wrap justify-end gap-3 pt-2">
                                <Button variant="outline" onClick={closeCreateDialog} type="button">
                                    取消
                                </Button>
                                <Button type="submit">创建词书</Button>
                            </div>
                        </form>
                    </div>
                </div>
            </Show>
        </section>
    );
}
