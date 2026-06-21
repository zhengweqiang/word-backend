import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { createSignal } from "solid-js";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddWordListModal } from "@/components/dictionaries/add-word-list-modal";
import { api } from "@/lib/api";

vi.mock("@/lib/api", () => ({
    api: {
        addDictionaryWordList: vi.fn(),
        generateDictionaryWordWithAi: vi.fn(),
        listDictionaryMetaWordSuggestions: vi.fn(),
    },
}));

const dictionary = {
    id: 7,
    name: "测试词书",
    category: "测试",
    scopeType: "CUSTOM",
    ownerUserId: 1,
};

describe("AddWordListModal", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listDictionaryMetaWordSuggestions).mockResolvedValue([]);
        vi.mocked(api.generateDictionaryWordWithAi).mockResolvedValue({
            dictionaryId: 7,
            metaWordId: 99,
            configId: 1,
            providerName: "OpenAI",
            modelName: "gpt-4o-mini",
            word: "apple",
            translation: "苹果",
            partOfSpeech: "noun",
            phonetic: "/ˈæp.əl/",
            definition: "a round fruit",
            exampleSentence: "She ate an apple after lunch.",
            total: 1,
            existed: 0,
            created: 1,
            added: 1,
            failed: 0,
        });
        vi.mocked(api.addDictionaryWordList).mockResolvedValue({
            total: 1,
            existed: 0,
            created: 1,
            added: 1,
            failed: 0,
        });
    });

    it("shows the dialog when isOpen changes from false to true", async () => {
        let setOpen!: (value: boolean) => void;

        const Wrapper = () => {
            const [open, updateOpen] = createSignal(false);
            setOpen = updateOpen;
            return (
                <AddWordListModal
                    dictionary={dictionary}
                    isOpen={open()}
                    onClose={vi.fn()}
                />
            );
        };

        render(() => <Wrapper />);

        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

        setOpen(true);

        expect(await screen.findByRole("dialog")).toBeInTheDocument();
        expect(screen.getByText("手动录入单词")).toBeInTheDocument();
    });

    it("starts with a single quick-entry row and keeps focus while typing", async () => {
        render(() => (
            <AddWordListModal
                dictionary={dictionary}
                isOpen={true}
                onClose={vi.fn()}
            />
        ));

        const wordInputs = screen.getAllByPlaceholderText("单词 *（输入时自动联想词元表）");
        expect(wordInputs).toHaveLength(1);

        const wordInput = wordInputs[0] as HTMLInputElement;
        wordInput.focus();

        fireEvent.input(wordInput, {
            currentTarget: { value: "a" },
            target: { value: "a" },
        });

        expect(wordInput).toHaveValue("a");
        expect(document.activeElement).toBe(wordInput);
    });

    it("fills the current row with AI generated details", async () => {
        render(() => (
            <AddWordListModal
                dictionary={dictionary}
                isOpen={true}
                onClose={vi.fn()}
            />
        ));

        fireEvent.input(screen.getByLabelText("第 1 行单词"), {
            currentTarget: { value: "apple" },
            target: { value: "apple" },
        });
        fireEvent.click(screen.getByRole("button", { name: "AI" }));

        await waitFor(() => {
            expect(api.generateDictionaryWordWithAi).toHaveBeenCalledWith(7, { word: "apple" });
        });

        await waitFor(() => {
            expect(screen.getByLabelText("第 1 行中文释义")).toHaveValue("苹果");
            expect(screen.getByLabelText("第 1 行词性")).toHaveValue("noun");
            expect(screen.getByLabelText("第 1 行音标")).toHaveValue("/ˈæp.əl/");
            expect(screen.getByLabelText("第 1 行英文释义")).toHaveValue("a round fruit");
            expect(screen.getByLabelText("第 1 行例句")).toHaveValue("She ate an apple after lunch.");
        });
    });

    it("submits bulk entries through the existing batch API", async () => {
        const onSuccess = vi.fn();

        render(() => (
            <AddWordListModal
                dictionary={dictionary}
                isOpen={true}
                onClose={vi.fn()}
                onSuccess={onSuccess}
            />
        ));

        fireEvent.click(screen.getByRole("button", { name: "批量粘贴" }));
        fireEvent.input(screen.getByRole("textbox"), {
            currentTarget: { value: "apple | 苹果 | noun" },
            target: { value: "apple | 苹果 | noun" },
        });
        fireEvent.click(screen.getByRole("button", { name: "解析并添加" }));

        await waitFor(() => {
            expect(api.addDictionaryWordList).toHaveBeenCalledWith(7, [
                {
                    word: "apple",
                    translation: "苹果",
                    partOfSpeech: "noun",
                    phonetic: undefined,
                    definition: undefined,
                    exampleSentence: undefined,
                    difficulty: 2,
                },
            ]);
        });

        await waitFor(() => {
            expect(onSuccess).toHaveBeenCalledTimes(1);
        });

        expect(screen.getByText("处理结果")).toBeInTheDocument();
    });
});
