import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";

const jsonResponse = (body: unknown = {}) => new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
});

describe("assessment API", () => {
    beforeEach(() => {
        Object.defineProperty(window, "localStorage", {
            configurable: true,
            value: { getItem: vi.fn(() => null), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn() },
        });
        vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(jsonResponse())));
    });

    it("forwards question filters and create payloads", async () => {
        await api.listQuestions({ page: 0, size: 20, keyword: "词义", questionType: "SINGLE_CHOICE", status: "ACTIVE", tag: "四级" });
        await api.createQuestion({
            questionType: "SINGLE_CHOICE",
            stem: "abandon 的词义是？",
            options: { A: "放弃", B: "获得" },
            acceptedAnswers: ["A"],
            defaultScore: 2,
            tags: ["四级"],
            status: "ACTIVE",
        });

        expect(fetch).toHaveBeenNthCalledWith(
            1,
            "/api/teacher/questions?page=0&size=20&keyword=%E8%AF%8D%E4%B9%89&questionType=SINGLE_CHOICE&status=ACTIVE&tag=%E5%9B%9B%E7%BA%A7",
            expect.any(Object),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/teacher/questions",
            expect.objectContaining({ method: "POST", body: expect.stringContaining("abandon") }),
        );
    });

    it("sends the exact fill-in-the-blank enum contract", async () => {
        await api.listQuestions({ page: 2, size: 20, questionType: "FILL_IN_BLANK", status: "ACTIVE" });
        await api.createQuestion({
            questionType: "FILL_IN_BLANK",
            stem: "abandon 的同义词",
            options: {},
            acceptedAnswers: ["abandon", "forsake"],
            defaultScore: 2,
            status: "ACTIVE",
        });

        expect(fetch).toHaveBeenNthCalledWith(
            1,
            "/api/teacher/questions?page=2&size=20&questionType=FILL_IN_BLANK&status=ACTIVE",
            expect.any(Object),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/teacher/questions",
            expect.objectContaining({ body: expect.stringContaining('"questionType":"FILL_IN_BLANK"') }),
        );
    });

    it("uses multipart preview and selected-row confirmation", async () => {
        const file = new File(["question_type,stem"], "questions.csv", { type: "text/csv" });
        await api.previewQuestionImport(file);
        await api.confirmQuestionImport(12, { selectedRowIds: [101, 102] });

        const previewCall = vi.mocked(fetch).mock.calls[0];
        expect(previewCall[0]).toBe("/api/teacher/question-imports/preview");
        expect(previewCall[1]).toEqual(expect.objectContaining({ method: "POST", body: expect.any(FormData) }));
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/teacher/question-imports/12/confirm",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ selectedRowIds: [101, 102] }) }),
        );
    });

    it("uses refresh-safe paper and release read paths plus correction commands", async () => {
        await api.getPaperPreview(42);
        await api.listPaperReleases();
        await api.getPaperRelease(77);
        await api.invalidatePaperRelease(77, { reason: "答案有误" });
        await api.getPaperReleaseStudentResult(77, 501);

        expect(vi.mocked(fetch).mock.calls.map((call) => call[0])).toEqual([
            "/api/teacher/papers/42/preview",
            "/api/teacher/paper-releases",
            "/api/teacher/paper-releases/77",
            "/api/teacher/paper-releases/77/invalidate",
            "/api/teacher/paper-releases/77/results/students/501",
        ]);
    });
});
