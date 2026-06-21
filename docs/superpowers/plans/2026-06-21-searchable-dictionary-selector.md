# Searchable Dictionary Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the study-plan form's native dictionary select with a searchable single-select that shows dictionary names and word counts.

**Architecture:** Add a focused, API-free SolidJS selector component that owns open/search/focus state and reports the chosen dictionary ID to its parent. Keep `StudyPlansPage` responsible for loading dictionaries, storing `form.dictionaryId`, and submitting the existing API payload.

**Tech Stack:** SolidJS, TypeScript, Tailwind CSS, Vitest, `@solidjs/testing-library`, Docker Compose

## Global Constraints

- Search uses the already loaded dictionary list and makes no additional backend request.
- Matching is case-insensitive partial matching against dictionary names.
- Candidate and selected states display dictionary name plus word count; missing counts render as `0 词`.
- The create-study-plan API contract and numeric `dictionaryId` payload remain unchanged.
- After modifying `admin-frontend/`, rebuild and restart the `admin-frontend` container before completion.

---

### Task 1: Searchable Dictionary Selector Component

**Files:**
- Create: `admin-frontend/src/components/study-plans/searchable-dictionary-select.tsx`
- Create: `admin-frontend/src/components/study-plans/searchable-dictionary-select.test.tsx`

**Interfaces:**
- Consumes: `Dictionary[]` from `@/types/api`, a string dictionary ID, and an `onChange(value: string)` callback.
- Produces: `SearchableDictionarySelect(props: SearchableDictionarySelectProps)`.

- [ ] **Step 1: Write the failing component tests**

```tsx
import { fireEvent, render, screen } from "@solidjs/testing-library";
import { createSignal } from "solid-js";
import { describe, expect, it, vi } from "vitest";
import { SearchableDictionarySelect } from "@/components/study-plans/searchable-dictionary-select";

const dictionaries = [
    { id: 7, name: "CET-4 核心词汇", wordCount: 1200 },
    { id: 8, name: "考研英语高频词", wordCount: 860 },
];

function SelectorHarness() {
    const [value, setValue] = createSignal("");
    return (
        <SearchableDictionarySelect
            dictionaries={dictionaries}
            value={value()}
            onChange={setValue}
        />
    );
}

describe("SearchableDictionarySelect", () => {
    it("filters dictionary names case-insensitively and selects a result", async () => {
        render(() => <SelectorHarness />);

        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), { target: { value: "cet-4" } });

        expect(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" })).toBeInTheDocument();
        expect(screen.queryByRole("option", { name: "考研英语高频词 860 词" })).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));

        expect(screen.getByRole("button", { name: "CET-4 核心词汇，1200 词" })).toBeInTheDocument();
        expect(screen.queryByPlaceholderText("搜索词书名称")).not.toBeInTheDocument();
    });

    it("shows an empty result and restores options when search is cleared", () => {
        render(() => <SelectorHarness />);

        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), { target: { value: "托福" } });

        expect(screen.getByText("没有匹配的词书")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "清空词书搜索" }));
        expect(screen.getAllByRole("option")).toHaveLength(2);
    });

    it("closes on Escape and returns focus to the trigger", () => {
        render(() => <SelectorHarness />);
        const trigger = screen.getByRole("button", { name: "选择词书" });

        fireEvent.click(trigger);
        fireEvent.keyDown(screen.getByPlaceholderText("搜索词书名称"), { key: "Escape" });

        expect(screen.queryByPlaceholderText("搜索词书名称")).not.toBeInTheDocument();
        expect(trigger).toHaveFocus();
    });

    it("disables selection when no dictionaries are available", () => {
        const onChange = vi.fn();
        render(() => <SearchableDictionarySelect dictionaries={[]} value="" onChange={onChange} />);

        expect(screen.getByRole("button", { name: "暂无可选词书" })).toBeDisabled();
    });
});
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `cd admin-frontend && npm test -- src/components/study-plans/searchable-dictionary-select.test.tsx`

Expected: FAIL because `@/components/study-plans/searchable-dictionary-select` does not exist.

- [ ] **Step 3: Implement the minimal selector component**

```tsx
import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/types/api";

interface SearchableDictionarySelectProps {
    dictionaries: Dictionary[];
    value: string;
    onChange: (value: string) => void;
}

export function SearchableDictionarySelect(props: SearchableDictionarySelectProps) {
    const [open, setOpen] = createSignal(false);
    const [keyword, setKeyword] = createSignal("");
    let triggerRef!: HTMLButtonElement;
    let searchInputRef!: HTMLInputElement;

    const selectedDictionary = createMemo(() =>
        props.dictionaries.find((dictionary) => String(dictionary.id) === props.value),
    );
    const filteredDictionaries = createMemo(() => {
        const normalizedKeyword = keyword().trim().toLocaleLowerCase("zh-CN");
        if (!normalizedKeyword) {
            return props.dictionaries;
        }
        return props.dictionaries.filter((dictionary) =>
            dictionary.name.toLocaleLowerCase("zh-CN").includes(normalizedKeyword),
        );
    });

    createEffect(() => {
        if (open()) {
            queueMicrotask(() => searchInputRef?.focus());
        }
    });

    const close = (restoreFocus = false) => {
        setOpen(false);
        setKeyword("");
        if (restoreFocus) {
            queueMicrotask(() => triggerRef?.focus());
        }
    };

    const toggle = () => {
        if (open()) {
            close(true);
            return;
        }
        setKeyword("");
        setOpen(true);
    };

    const selectDictionary = (dictionary: Dictionary) => {
        props.onChange(String(dictionary.id));
        close(true);
    };

    return (
        <div
            class="relative"
            onFocusOut={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                    close();
                }
            }}
        >
            <button
                ref={triggerRef}
                type="button"
                class="flex h-11 w-full items-center justify-between rounded-lg border border-input bg-background/70 px-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-60"
                aria-expanded={open()}
                aria-haspopup="listbox"
                aria-label={
                    selectedDictionary()
                        ? `${selectedDictionary()!.name}，${selectedDictionary()!.wordCount || 0} 词`
                        : props.dictionaries.length > 0
                            ? "选择词书"
                            : "暂无可选词书"
                }
                disabled={props.dictionaries.length === 0}
                onClick={toggle}
            >
                <Show when={selectedDictionary()} fallback={<span class="text-muted-foreground">{props.dictionaries.length > 0 ? "选择词书" : "暂无可选词书"}</span>}>
                    {(dictionary) => (
                        <span class="flex min-w-0 items-center gap-2">
                            <span class="truncate font-medium">{dictionary().name}</span>
                            <span class="shrink-0 text-xs text-muted-foreground">{dictionary().wordCount || 0} 词</span>
                        </span>
                    )}
                </Show>
                <span aria-hidden="true" class="text-muted-foreground">⌄</span>
            </button>

            <Show when={open()}>
                <div class="absolute z-50 mt-2 w-full rounded-xl border border-border bg-background p-3 shadow-xl">
                    <div class="flex gap-2">
                        <Input
                            ref={searchInputRef}
                            placeholder="搜索词书名称"
                            value={keyword()}
                            onInput={(event) => setKeyword(event.currentTarget.value)}
                            onKeyDown={(event) => {
                                if (event.key === "Escape") {
                                    event.preventDefault();
                                    close(true);
                                }
                            }}
                        />
                        <Show when={keyword()}>
                            <button
                                type="button"
                                class="rounded-lg border border-border px-3 text-xs text-muted-foreground hover:text-foreground"
                                aria-label="清空词书搜索"
                                onClick={() => {
                                    setKeyword("");
                                    searchInputRef?.focus();
                                }}
                            >
                                清空
                            </button>
                        </Show>
                    </div>

                    <div class="mt-3 max-h-64 space-y-1 overflow-y-auto" role="listbox" aria-label="词书列表">
                        <Show
                            when={filteredDictionaries().length > 0}
                            fallback={<p class="px-3 py-6 text-center text-sm text-muted-foreground">没有匹配的词书</p>}
                        >
                            <For each={filteredDictionaries()}>
                                {(dictionary) => (
                                    <button
                                        type="button"
                                        role="option"
                                        aria-selected={String(dictionary.id) === props.value}
                                        aria-label={`${dictionary.name} ${dictionary.wordCount || 0} 词`}
                                        class={
                                            String(dictionary.id) === props.value
                                                ? "flex w-full items-center justify-between rounded-lg bg-primary/10 px-3 py-2.5 text-left text-sm text-primary"
                                                : "flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-left text-sm hover:bg-muted/60"
                                        }
                                        onClick={() => selectDictionary(dictionary)}
                                    >
                                        <span class="truncate font-medium">{dictionary.name}</span>
                                        <span class="ml-3 shrink-0 text-xs text-muted-foreground">{dictionary.wordCount || 0} 词</span>
                                    </button>
                                )}
                            </For>
                        </Show>
                    </div>
                </div>
            </Show>
        </div>
    );
}
```

- [ ] **Step 4: Run the component test to verify it passes**

Run: `cd admin-frontend && npm test -- src/components/study-plans/searchable-dictionary-select.test.tsx`

Expected: 4 tests PASS.

- [ ] **Step 5: Commit the component slice**

```bash
git add admin-frontend/src/components/study-plans/searchable-dictionary-select.tsx admin-frontend/src/components/study-plans/searchable-dictionary-select.test.tsx
git commit -m "feat(admin): add searchable dictionary selector"
```

### Task 2: Study Plan Form Integration

**Files:**
- Modify: `admin-frontend/src/pages/study-plans-page.tsx:1-195`
- Create: `admin-frontend/src/pages/study-plans-page.test.tsx`

**Interfaces:**
- Consumes: `SearchableDictionarySelect`, `data().dictionaries`, `form.dictionaryId`, and `setForm`.
- Produces: the existing create-plan request with the selected dictionary's numeric ID.

- [ ] **Step 1: Write a failing integration test for the submitted dictionary ID**

```tsx
import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { StudyPlansPage } from "@/pages/study-plans-page";

vi.mock("@/lib/api", () => ({
    api: {
        listStudyPlans: vi.fn(),
        listClassrooms: vi.fn(),
        listDictionaries: vi.fn(),
        createStudyPlan: vi.fn(),
        getStudyPlanOverview: vi.fn(),
        getStudyPlanStudents: vi.fn(),
        publishStudyPlan: vi.fn(),
    },
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({ id: 1, username: "admin", displayName: "Admin", role: "ADMIN", status: "ACTIVE" }),
    }),
}));

const createdPlan = {
    id: 99,
    name: "四级计划",
    teacherId: 1,
    dictionaryId: 7,
    dictionaryName: "CET-4 核心词汇",
    classroomIds: [],
    startDate: "2026-06-21",
    endDate: null,
    timezone: "Asia/Shanghai",
    dailyNewCount: 20,
    dailyReviewLimit: 60,
    reviewMode: "FIXED_INTERVAL",
    reviewIntervals: [1, 3, 7, 14],
    completionThreshold: 85,
    dailyDeadlineTime: "21:00",
    attentionTrackingEnabled: true,
    minFocusSecondsPerWord: 2,
    maxFocusSecondsPerWord: 18,
    longStayWarningSeconds: 25,
    idleTimeoutSeconds: 12,
    status: "DRAFT",
    studentCount: 0,
};

describe("StudyPlansPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listStudyPlans).mockResolvedValue([]);
        vi.mocked(api.listClassrooms).mockResolvedValue([]);
        vi.mocked(api.listDictionaries).mockResolvedValue([
            { id: 7, name: "CET-4 核心词汇", wordCount: 1200 },
            { id: 8, name: "考研英语高频词", wordCount: 860 },
        ]);
        vi.mocked(api.createStudyPlan).mockResolvedValue(createdPlan);
        vi.mocked(api.getStudyPlanOverview).mockResolvedValue({
            studyPlanId: 99,
            studyPlanName: "四级计划",
            status: "DRAFT",
            taskDate: "2026-06-21",
            totalStudents: 0,
            completedStudents: 0,
            notStartedStudents: 0,
            inProgressStudents: 0,
            missedStudents: 0,
            averageCompletionRate: 0,
            averageAttentionScore: 0,
        });
        vi.mocked(api.getStudyPlanStudents).mockResolvedValue([]);
    });

    it("submits the ID selected from the searchable dictionary picker", async () => {
        render(() => <StudyPlansPage />);

        fireEvent.input(await screen.findByLabelText("计划名称"), { target: { value: "四级计划" } });
        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), { target: { value: "CET-4" } });
        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));
        fireEvent.click(screen.getByRole("button", { name: "创建计划" }));

        await waitFor(() => {
            expect(api.createStudyPlan).toHaveBeenCalledWith(
                expect.objectContaining({ name: "四级计划", dictionaryId: 7 }),
            );
        });
    });
});
```

- [ ] **Step 2: Run the integration test to verify it fails**

Run: `cd admin-frontend && npm test -- src/pages/study-plans-page.test.tsx`

Expected: FAIL because the page still renders a native select and has no “选择词书” button.

- [ ] **Step 3: Replace the native select with the selector component**

Add this import:

```tsx
import { SearchableDictionarySelect } from "@/components/study-plans/searchable-dictionary-select";
```

Replace the existing `<select>` block under the “词书” label with:

```tsx
<SearchableDictionarySelect
    dictionaries={data().dictionaries}
    value={form.dictionaryId}
    onChange={(value) => setForm("dictionaryId", value)}
/>
```

Give the existing plan-name input an ID and connect its label so the integration test and assistive technology can identify it:

```tsx
<Label for="study-plan-name">计划名称</Label>
<Input
    id="study-plan-name"
    value={form.name}
    onInput={(event) => setForm("name", event.currentTarget.value)}
/>
```

- [ ] **Step 4: Run focused tests to verify the integration passes**

Run: `cd admin-frontend && npm test -- src/components/study-plans/searchable-dictionary-select.test.tsx src/pages/study-plans-page.test.tsx`

Expected: 5 tests PASS.

- [ ] **Step 5: Run the full admin test suite and production build**

Run: `cd admin-frontend && npm test`

Expected: all Vitest tests PASS.

Run: `cd admin-frontend && npm run build`

Expected: TypeScript and Vite build complete with exit code 0.

- [ ] **Step 6: Commit the page integration**

```bash
git add admin-frontend/src/pages/study-plans-page.tsx admin-frontend/src/pages/study-plans-page.test.tsx
git commit -m "feat(admin): make study plan dictionary searchable"
```

### Task 3: Container and Browser Verification

**Files:**
- No source files changed.

**Interfaces:**
- Consumes: the built `admin-frontend` Docker image and existing running backend.
- Produces: a healthy container and visually verified study-plan selector.

- [ ] **Step 1: Rebuild and restart the required frontend container**

Run: `docker compose up -d --build admin-frontend`

Expected: `words-admin-frontend` is recreated and starts successfully.

- [ ] **Step 2: Verify container health**

Run: `docker inspect --format '{{.State.Health.Status}}' words-admin-frontend`

Expected: `healthy`.

- [ ] **Step 3: Verify the behavior in the in-app browser**

Open `http://127.0.0.1:8083/study-plans`, reload after the container rebuild, expand the dictionary picker, search by a partial dictionary name, select the result, and confirm the trigger shows both its name and word count.

Expected: filtering is immediate, unmatched dictionaries disappear, selection closes the popup, and the browser console has no errors.
