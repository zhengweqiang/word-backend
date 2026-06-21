import { createEffect, createMemo, createSignal, createUniqueId, For, Show } from "solid-js";
import { Input } from "@/components/ui/input";
import type { Dictionary } from "@/types/api";

interface SearchableDictionarySelectProps {
    dictionaries: Dictionary[];
    value: string;
    onChange: (value: string) => void;
    id?: string;
}

export function SearchableDictionarySelect(props: SearchableDictionarySelectProps) {
    const [open, setOpen] = createSignal(false);
    const [keyword, setKeyword] = createSignal("");
    const [activeDictionaryId, setActiveDictionaryId] = createSignal<number | null>(null);
    const componentId = createUniqueId();
    const searchInputId = `${componentId}-search`;
    const listboxId = `${componentId}-listbox`;
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

    const optionId = (dictionaryId: number) => `${componentId}-option-${dictionaryId}`;

    createEffect(() => {
        if (!open()) {
            return;
        }
        const dictionaries = filteredDictionaries();
        const currentId = activeDictionaryId();
        if (currentId !== null && dictionaries.some((dictionary) => dictionary.id === currentId)) {
            return;
        }
        const selected = dictionaries.find((dictionary) => String(dictionary.id) === props.value);
        setActiveDictionaryId(selected?.id ?? dictionaries[0]?.id ?? null);
    });

    createEffect(() => {
        if (open()) {
            queueMicrotask(() => searchInputRef?.focus());
        }
    });

    const close = (restoreFocus = false) => {
        setOpen(false);
        setKeyword("");
        setActiveDictionaryId(null);
        if (restoreFocus) {
            triggerRef?.focus();
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

    const setActiveOption = (dictionary: Dictionary) => {
        setActiveDictionaryId(dictionary.id);
        queueMicrotask(() => {
            document.getElementById(optionId(dictionary.id))?.scrollIntoView?.({ block: "nearest" });
        });
    };

    const handleSearchKeyDown = (event: KeyboardEvent) => {
        const dictionaries = filteredDictionaries();
        const activeIndex = dictionaries.findIndex((dictionary) => dictionary.id === activeDictionaryId());
        let nextIndex: number | null = null;

        if (event.key === "ArrowDown" && dictionaries.length > 0) {
            nextIndex = activeIndex < 0 ? 0 : Math.min(activeIndex + 1, dictionaries.length - 1);
        } else if (event.key === "ArrowUp" && dictionaries.length > 0) {
            nextIndex = activeIndex < 0 ? dictionaries.length - 1 : Math.max(activeIndex - 1, 0);
        } else if (event.key === "Home" && dictionaries.length > 0) {
            nextIndex = 0;
        } else if (event.key === "End" && dictionaries.length > 0) {
            nextIndex = dictionaries.length - 1;
        } else if (event.key === "Enter") {
            const activeDictionary = dictionaries[activeIndex];
            if (activeDictionary) {
                event.preventDefault();
                selectDictionary(activeDictionary);
            }
            return;
        } else {
            return;
        }

        event.preventDefault();
        setActiveOption(dictionaries[nextIndex]);
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
                id={props.id}
                type="button"
                class={
                    "flex h-11 w-full items-center justify-between rounded-lg border border-input bg-background/70 " +
                    "px-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-60"
                }
                aria-expanded={open()}
                aria-haspopup="listbox"
                aria-label={
                    selectedDictionary()
                        ? `词书：${selectedDictionary()!.name}，${selectedDictionary()!.wordCount || 0} 词`
                        : props.dictionaries.length > 0
                            ? "选择词书"
                            : "暂无可选词书"
                }
                disabled={props.dictionaries.length === 0}
                onClick={toggle}
            >
                <Show
                    when={selectedDictionary()}
                    fallback={
                        <span class="text-muted-foreground">
                            {props.dictionaries.length > 0 ? "选择词书" : "暂无可选词书"}
                        </span>
                    }
                >
                    {(dictionary) => (
                        <span class="flex min-w-0 items-center gap-2">
                            <span class="truncate font-medium">{dictionary().name}</span>
                            <span class="shrink-0 text-xs text-muted-foreground">
                                {dictionary().wordCount || 0} 词
                            </span>
                        </span>
                    )}
                </Show>
                <span aria-hidden="true" class="text-muted-foreground">⌄</span>
            </button>

            <Show when={open()}>
                <div
                    class="absolute z-50 mt-2 w-full rounded-xl border border-border bg-background p-3 shadow-xl"
                    onKeyDown={(event) => {
                        if (event.key === "Escape") {
                            event.preventDefault();
                            close(true);
                        }
                    }}
                >
                    <div class="flex gap-2">
                        <label class="sr-only" for={searchInputId}>搜索词书</label>
                        <Input
                            ref={searchInputRef}
                            id={searchInputId}
                            role="combobox"
                            aria-autocomplete="list"
                            aria-controls={listboxId}
                            aria-expanded="true"
                            aria-activedescendant={
                                activeDictionaryId() === null
                                    ? undefined
                                    : optionId(activeDictionaryId()!)
                            }
                            placeholder="搜索词书名称"
                            value={keyword()}
                            onInput={(event) => setKeyword(event.currentTarget.value)}
                            onKeyDown={handleSearchKeyDown}
                        />
                        <Show when={keyword()}>
                            <button
                                type="button"
                                class={
                                    "rounded-lg border border-border px-3 text-xs text-muted-foreground " +
                                    "hover:text-foreground"
                                }
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

                    <div
                        id={listboxId}
                        class="mt-3 max-h-64 space-y-1 overflow-y-auto"
                        role="listbox"
                        aria-label="词书列表"
                    >
                        <Show
                            when={filteredDictionaries().length > 0}
                            fallback={
                                <p class="px-3 py-6 text-center text-sm text-muted-foreground">
                                    没有匹配的词书
                                </p>
                            }
                        >
                            <For each={filteredDictionaries()}>
                                {(dictionary) => (
                                    <button
                                        id={optionId(dictionary.id)}
                                        type="button"
                                        role="option"
                                        tabIndex={-1}
                                        aria-selected={String(dictionary.id) === props.value}
                                        aria-label={`${dictionary.name} ${dictionary.wordCount || 0} 词`}
                                        class={
                                            String(dictionary.id) === props.value
                                                ? "flex w-full items-center justify-between rounded-lg " +
                                                  "bg-primary/10 px-3 py-2.5 text-left text-sm text-primary"
                                                : dictionary.id === activeDictionaryId()
                                                    ? "flex w-full items-center justify-between rounded-lg " +
                                                      "bg-muted/60 px-3 py-2.5 text-left text-sm"
                                                : "flex w-full items-center justify-between rounded-lg px-3 py-2.5 " +
                                                  "text-left text-sm hover:bg-muted/60"
                                        }
                                        onClick={() => selectDictionary(dictionary)}
                                    >
                                        <span class="truncate font-medium">{dictionary.name}</span>
                                        <span class="ml-3 shrink-0 text-xs text-muted-foreground">
                                            {dictionary.wordCount || 0} 词
                                        </span>
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
