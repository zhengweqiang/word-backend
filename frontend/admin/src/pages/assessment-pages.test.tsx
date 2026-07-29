import { fireEvent, render, screen, waitFor, within } from "@solidjs/testing-library";
import type { JSX } from "solid-js";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { QuestionBankPage } from "@/pages/question-bank-page";
import { QuestionImportPage } from "@/pages/question-import-page";
import { PaperTemplatePage } from "@/pages/paper-template-page";
import { PaperEditorPage } from "@/pages/paper-editor-page";
import { PaperReleasePage } from "@/pages/paper-release-page";
import { PaperResultPage } from "@/pages/paper-result-page";

const routeParams = vi.hoisted(() => ({ paperId: "42", releaseId: "77" }));
const authUser = vi.hoisted(() => ({ id: 7, role: "TEACHER" as "TEACHER" | "ADMIN", displayName: "老师" }));

vi.mock("@solidjs/router", async () => {
    const actual = await vi.importActual<typeof import("@solidjs/router")>("@solidjs/router");
    return {
        ...actual,
        A: (props: { href: string; children: JSX.Element; [key: string]: unknown }) => {
            const { children, ...rest } = props;
            return <a {...rest}>{children}</a>;
        },
        useLocation: () => ({ pathname: "/questions" }),
        useParams: () => routeParams,
        useNavigate: () => vi.fn(),
    };
});

vi.mock("@/lib/api", () => ({
    api: {
        listQuestions: vi.fn(), createQuestion: vi.fn(), updateQuestion: vi.fn(), copyQuestion: vi.fn(), archiveQuestion: vi.fn(),
        previewQuestionImport: vi.fn(), getQuestionImport: vi.fn(), confirmQuestionImport: vi.fn(),
        listPapers: vi.fn(), createPaper: vi.fn(), updatePaper: vi.fn(), getPaperPreview: vi.fn(), copyPaper: vi.fn(), archivePaper: vi.fn(),
        addPaperQuestion: vi.fn(), reorderPaperQuestions: vi.fn(), updatePaperQuestionScore: vi.fn(), removePaperQuestion: vi.fn(),
        listPaperReleases: vi.fn(), getPaperRelease: vi.fn(), publishPaper: vi.fn(), withdrawPaperRelease: vi.fn(), invalidatePaperRelease: vi.fn(), supersedePaperRelease: vi.fn(),
        getPaperReleaseResults: vi.fn(), getPaperReleaseQuestionStats: vi.fn(), getPaperReleaseStudentResult: vi.fn(), releasePaperResults: vi.fn(),
        listClassrooms: vi.fn(), getClassroomStudents: vi.fn(), listMyStudents: vi.fn(), listStudents: vi.fn(),
    },
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({ user: () => authUser }),
}));

const emptyPage = { content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, numberOfElements: 0 };
const question = {
    id: 1, questionType: "SINGLE_CHOICE" as const, stem: "abandon 的词义是？",
    options: { A: "放弃", B: "获得" }, acceptedAnswers: ["A"], defaultScore: 2,
    difficulty: 2, tags: ["四级"], explanation: "核心词义", createdByUserId: 7,
    status: "ACTIVE" as const, createdAt: "2026-07-29T09:00:00",
};
const paper = {
    id: 42, title: "四级周测", instructions: "独立完成", ownerUserId: 7,
    status: "DRAFT" as const, shuffleQuestions: false, shuffleOptions: false,
    totalScore: 0, questionCount: 0, questions: [],
};
const release = {
    id: 77, paperTemplateId: 42, title: "四级周测", publishedByUserId: 7,
    status: "OPEN" as const, questionCount: 1, totalScore: 2, shuffleQuestions: false,
    shuffleOptions: false, blankAnswerPolicy: "ALLOW_BLANK" as const,
    resultVisibility: "HIDDEN_UNTIL_RELEASED" as const, createdAt: "2026-07-29T09:00:00",
    targets: [{ id: 701, studentId: 12, sourceClassroomIds: [31], attemptId: 501, attemptStatus: "SUBMITTED_LATE" as const }],
};

describe("teacher assessment pages", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        Object.assign(authUser, { id: 7, role: "TEACHER", displayName: "老师" });
        vi.stubGlobal("confirm", vi.fn(() => true));
        vi.mocked(api.listQuestions).mockResolvedValue({ ...emptyPage, content: [question], totalElements: 1, totalPages: 1, numberOfElements: 1 });
        vi.mocked(api.createQuestion).mockResolvedValue(question);
        vi.mocked(api.updateQuestion).mockResolvedValue(question);
        vi.mocked(api.copyQuestion).mockResolvedValue({ ...question, id: 2 });
        vi.mocked(api.archiveQuestion).mockResolvedValue(undefined);
        vi.mocked(api.previewQuestionImport).mockResolvedValue({
            batchId: 9, fileName: "questions.csv", totalRows: 2, validRows: 1, invalidRows: 1, duplicateRows: 0,
            status: "PREVIEWED", rows: [
                { id: 91, rowNumber: 2, status: "VALID", questionType: "FILL_IN_BLANK", stem: "拼写 abandon", acceptedAnswers: ["abandon"], score: 2 },
                { id: 92, rowNumber: 3, status: "INVALID", stem: "", message: "题干不能为空" },
            ],
        });
        vi.mocked(api.confirmQuestionImport).mockResolvedValue({ batchId: 9, importedCount: 1, importedQuestionIds: [3], status: "CONFIRMED" });
        vi.mocked(api.listPapers).mockResolvedValue({ ...emptyPage, content: [{ ...paper, status: "READY" }], totalElements: 1, totalPages: 1, numberOfElements: 1 });
        vi.mocked(api.createPaper).mockResolvedValue(paper);
        vi.mocked(api.updatePaper).mockResolvedValue(paper);
        vi.mocked(api.getPaperPreview).mockResolvedValue(paper);
        vi.mocked(api.copyPaper).mockResolvedValue({ ...paper, id: 43, title: "四级周测（副本）" });
        vi.mocked(api.archivePaper).mockResolvedValue(undefined);
        vi.mocked(api.addPaperQuestion).mockResolvedValue(paper);
        vi.mocked(api.reorderPaperQuestions).mockResolvedValue(paper);
        vi.mocked(api.updatePaperQuestionScore).mockResolvedValue(paper);
        vi.mocked(api.removePaperQuestion).mockResolvedValue({ ...paper, questions: [], questionCount: 0, totalScore: 0 });
        vi.mocked(api.listPaperReleases).mockResolvedValue([release]);
        vi.mocked(api.getPaperRelease).mockResolvedValue(release);
        vi.mocked(api.publishPaper).mockResolvedValue(release);
        vi.mocked(api.withdrawPaperRelease).mockResolvedValue({ ...release, status: "WITHDRAWN" });
        vi.mocked(api.invalidatePaperRelease).mockResolvedValue({ ...release, status: "INVALIDATED" });
        vi.mocked(api.supersedePaperRelease).mockResolvedValue({ ...release, id: 78, supersedesReleaseId: 77 });
        vi.mocked(api.listClassrooms).mockResolvedValue([{ id: 31, name: "高一 1 班", teacherId: 7, teacherName: "老师", studentCount: 20 }]);
        vi.mocked(api.getClassroomStudents).mockResolvedValue([
            { id: 12, username: "student", displayName: "小明", role: "STUDENT", status: "ACTIVE" },
            { id: 13, username: "student-13", displayName: "小红", role: "STUDENT", status: "ACTIVE" },
        ]);
        vi.mocked(api.listMyStudents).mockResolvedValue([{ id: 12, username: "student", displayName: "小明", role: "STUDENT", status: "ACTIVE" }]);
        vi.mocked(api.listStudents).mockResolvedValue([]);
        vi.mocked(api.getPaperReleaseResults).mockResolvedValue({
            releaseId: 77, title: "四级周测", releaseStatus: "OPEN", assignedCount: 1,
            notStartedCount: 0, inProgressCount: 0, overdueCount: 0, submittedCount: 0,
            submittedLateCount: 1, completedCount: 1, resultVisibility: "HIDDEN_UNTIL_RELEASED",
            resultsReleased: false, students: [{ releaseId: 77, attemptId: 501, studentId: 12, studentUsername: "student_api_20260729", status: "SUBMITTED_LATE", late: true, answeredCount: 1, correctCount: 1, earnedScore: 2, totalScore: 2, scorePercentage: 100, questions: [] } as any],
        });
        vi.mocked(api.getPaperReleaseQuestionStats).mockResolvedValue([{ releaseQuestionId: 801, questionOrder: 1, questionType: "SINGLE_CHOICE", stem: question.stem, submissionCount: 1, answeredCount: 1, correctCount: 1, correctnessRate: 100 }]);
        vi.mocked(api.getPaperReleaseStudentResult).mockResolvedValue({ releaseId: 77, attemptId: 501, studentId: 12, status: "SUBMITTED_LATE", late: true, answeredCount: 1, correctCount: 1, earnedScore: 2, totalScore: 2, scorePercentage: 100, submittedAt: "2026-07-29T11:00:00", questions: [{ releaseQuestionId: 801, questionOrder: 1, questionType: "SINGLE_CHOICE", stem: question.stem, options: question.options, selectedAnswers: ["A"], blankAnswers: [], correct: true, earnedScore: 2, questionScore: 2, acceptedAnswers: ["A"], explanation: "核心词义" }] });
    });

    it("creates a question from structured option, answer, and tag fields", async () => {
        render(() => <QuestionBankPage />);
        fireEvent.click(await screen.findByRole("button", { name: "新建试题" }));
        const form = screen.getByRole("form", { name: "试题表单" });
        fireEvent.input(within(form).getByLabelText("题干"), { target: { value: "新的单选题" } });
        fireEvent.input(within(form).getByLabelText("选项 A"), { target: { value: "答案一" } });
        fireEvent.input(within(form).getByLabelText("选项 B"), { target: { value: "答案二" } });
        fireEvent.input(within(form).getByLabelText("正确答案"), { target: { value: "A" } });
        fireEvent.input(within(form).getByLabelText("标签"), { target: { value: "四级,核心" } });
        fireEvent.submit(form);

        await waitFor(() => expect(api.createQuestion).toHaveBeenCalledWith(expect.objectContaining({
            stem: "新的单选题", options: { A: "答案一", B: "答案二" }, acceptedAnswers: ["A"], tags: ["四级", "核心"],
        })));
    });

    it("creates and renders FILL_IN_BLANK with multiple pipe-separated accepted answers", async () => {
        vi.mocked(api.listQuestions).mockResolvedValue({
            ...emptyPage,
            content: [{ ...question, questionType: "FILL_IN_BLANK", options: {}, acceptedAnswers: ["abandon", "forsake"] }],
            totalElements: 1,
            totalPages: 1,
            numberOfElements: 1,
        });
        render(() => <QuestionBankPage />);
        expect(await screen.findByText("填空题")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "新建试题" }));
        const form = screen.getByRole("form", { name: "试题表单" });
        fireEvent.change(within(form).getByLabelText("题型"), { target: { value: "FILL_IN_BLANK" } });
        fireEvent.input(within(form).getByLabelText("题干"), { target: { value: "abandon 的同义词" } });
        expect(within(form).getByText("多个可接受答案使用 | 分隔")).toBeInTheDocument();
        fireEvent.input(within(form).getByLabelText("正确答案"), { target: { value: "abandon | forsake" } });
        fireEvent.submit(form);
        await waitFor(() => expect(api.createQuestion).toHaveBeenCalledWith(expect.objectContaining({
            questionType: "FILL_IN_BLANK",
            options: {},
            acceptedAnswers: ["abandon", "forsake"],
        })));
    });

    it("blocks invalid question payloads before calling the API", async () => {
        render(() => <QuestionBankPage />);
        fireEvent.click(await screen.findByRole("button", { name: "新建试题" }));
        const form = screen.getByRole("form", { name: "试题表单" });
        fireEvent.input(within(form).getByLabelText("题干"), { target: { value: "无效单选" } });
        fireEvent.input(within(form).getByLabelText("选项 A"), { target: { value: "唯一选项" } });
        fireEvent.input(within(form).getByLabelText("正确答案"), { target: { value: "B" } });
        fireEvent.input(within(form).getByLabelText("分值"), { target: { value: "1.234" } });
        fireEvent.submit(form);
        expect(await screen.findByText(/选择题必须提供 2 至 4 个非空选项/)).toBeInTheDocument();
        expect(api.createQuestion).not.toHaveBeenCalled();
    });

    it("allows an owner to edit, copy, and explicitly archive a question", async () => {
        render(() => <QuestionBankPage />);
        fireEvent.click(await screen.findByRole("button", { name: `编辑 ${question.stem}` }));
        const form = screen.getByRole("form", { name: "试题表单" });
        fireEvent.input(within(form).getByLabelText("题干"), { target: { value: "更新后的题干" } });
        fireEvent.submit(form);
        await waitFor(() => expect(api.updateQuestion).toHaveBeenCalledWith(1, expect.objectContaining({ stem: "更新后的题干" })));

        fireEvent.click(await screen.findByRole("button", { name: `复制 ${question.stem}` }));
        await waitFor(() => expect(api.copyQuestion).toHaveBeenCalledWith(1, expect.any(Object)));
        fireEvent.click(await screen.findByRole("button", { name: `归档 ${question.stem}` }));
        await waitFor(() => expect(api.archiveQuestion).toHaveBeenCalledWith(1));
    });

    it("only confirms selectable valid import rows and keeps invalid feedback visible", async () => {
        render(() => <QuestionImportPage />);
        const input = screen.getByLabelText("CSV 文件");
        fireEvent.change(input, { target: { files: [new File(["csv"], "questions.csv", { type: "text/csv" })] } });
        fireEvent.click(screen.getByRole("button", { name: "预览导入" }));
        expect(await screen.findByText("题干不能为空")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "确认导入 1 题" }));
        await waitFor(() => expect(api.confirmQuestionImport).toHaveBeenCalledWith(9, { selectedRowIds: [91] }));
        expect(await screen.findByText("导入已确认")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "导入已确认" })).toBeDisabled();
        fireEvent.click(screen.getByRole("button", { name: "导入已确认" }));
        expect(api.confirmQuestionImport).toHaveBeenCalledTimes(1);
    });

    it("renders import preview columns with stable widths and localized question types", async () => {
        render(() => <QuestionImportPage />);
        fireEvent.change(screen.getByLabelText("CSV 文件"), {
            target: { files: [new File(["csv"], "questions.csv", { type: "text/csv" })] },
        });
        fireEvent.click(screen.getByRole("button", { name: "预览导入" }));

        expect(await screen.findByText("填空题")).toBeInTheDocument();
        expect(screen.queryByText("FILL_IN_BLANK")).not.toBeInTheDocument();
        expect(screen.getByRole("columnheader", { name: "选择" })).toHaveClass("w-24", "whitespace-nowrap");
        expect(screen.getByRole("columnheader", { name: "状态" })).toHaveClass("w-28", "whitespace-nowrap");
        expect(screen.getByRole("columnheader", { name: "题型" })).toHaveClass("w-32", "whitespace-nowrap");
        expect(screen.getByRole("columnheader", { name: "题干" })).toHaveClass("w-[28rem]");
        expect(screen.getByRole("columnheader", { name: "校验信息" })).toHaveClass("w-28", "whitespace-nowrap");
    });

    it("creates a paper and links its edit route", async () => {
        render(() => <PaperTemplatePage />);
        fireEvent.input(await screen.findByLabelText("试卷标题"), { target: { value: "四级周测" } });
        fireEvent.click(screen.getByRole("button", { name: "创建试卷" }));
        await waitFor(() => expect(api.createPaper).toHaveBeenCalledWith(expect.objectContaining({ title: "四级周测" })));
        expect(await screen.findByRole("link", { name: "编辑 四级周测" })).toHaveAttribute("href", "/papers/42/edit");
    });

    it("offers nonowners preview and copy without an edit or archive command", async () => {
        vi.mocked(api.listPapers).mockResolvedValue({
            ...emptyPage,
            content: [{ ...paper, ownerUserId: 8, title: "共享试卷" }],
            totalElements: 1,
            totalPages: 1,
            numberOfElements: 1,
        });
        render(() => <PaperTemplatePage />);
        expect(await screen.findByRole("button", { name: "预览 共享试卷" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "复制 共享试卷" })).toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "编辑 共享试卷" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "归档 共享试卷" })).not.toBeInTheDocument();
    });

    it("lets an admin archive a nonowned paper without exposing its edit route", async () => {
        Object.assign(authUser, { id: 1, role: "ADMIN" });
        vi.mocked(api.listPapers).mockResolvedValue({
            ...emptyPage,
            content: [{ ...paper, ownerUserId: 8, title: "全局试卷" }],
            totalElements: 1,
            totalPages: 1,
            numberOfElements: 1,
        });
        render(() => <PaperTemplatePage />);
        expect(await screen.findByRole("button", { name: "归档 全局试卷" })).toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "编辑 全局试卷" })).not.toBeInTheDocument();
    });

    it("keeps paper archive explicit and outside normal status updates", async () => {
        render(() => <PaperEditorPage />);
        await screen.findByText("四级周测");
        expect(within(screen.getByLabelText("状态")).queryByRole("option", { name: "已归档" })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "归档试卷" }));
        await waitFor(() => expect(api.archivePaper).toHaveBeenCalledWith(42));
    });

    it("loads the paper id from the route and adds an active question", async () => {
        render(() => <PaperEditorPage />);
        expect(await screen.findByText("四级周测")).toBeInTheDocument();
        expect(api.getPaperPreview).toHaveBeenCalledWith(42);
        fireEvent.click(screen.getByRole("button", { name: "添加试题 abandon 的词义是？" }));
        await waitFor(() => expect(api.addPaperQuestion).toHaveBeenCalledWith(42, { questionId: 1, score: 2 }));
    });

    it("keeps a directly opened nonowner editor read-only and offers copy", async () => {
        Object.assign(authUser, { id: 9, role: "TEACHER" });
        render(() => <PaperEditorPage />);
        expect(await screen.findByText("只读预览")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "保存设置" })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "复制为我的试卷" }));
        await waitFor(() => expect(api.copyPaper).toHaveBeenCalledWith(42, expect.any(Object)));
    });

    it("keeps a directly opened nonowner editor read-only for admins too", async () => {
        Object.assign(authUser, { id: 1, role: "ADMIN" });
        render(() => <PaperEditorPage />);
        expect(await screen.findByText("只读预览")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "保存设置" })).not.toBeInTheDocument();
    });

    it("searches and paginates through the entire ACTIVE question bank", async () => {
        const secondQuestion = { ...question, id: 2, questionType: "MULTIPLE_CHOICE" as const, stem: "第二页试题", acceptedAnswers: ["A", "B"] };
        vi.mocked(api.listQuestions).mockImplementation(async (params) => ({
            ...emptyPage,
            content: params.page === 1 ? [secondQuestion] : [question],
            totalElements: 2,
            totalPages: 2,
            number: params.page ?? 0,
            numberOfElements: 1,
        }));
        render(() => <PaperEditorPage />);
        await screen.findByRole("button", { name: `添加试题 ${question.stem}` });
        fireEvent.input(screen.getByLabelText("编辑器题库搜索"), { target: { value: "第二页" } });
        fireEvent.change(screen.getByLabelText("编辑器题型"), { target: { value: "MULTIPLE_CHOICE" } });
        fireEvent.click(screen.getByRole("button", { name: "搜索题库" }));
        await waitFor(() => expect(api.listQuestions).toHaveBeenCalledWith(expect.objectContaining({
            page: 0,
            size: 20,
            keyword: "第二页",
            questionType: "MULTIPLE_CHOICE",
            status: "ACTIVE",
        })));
        fireEvent.click(screen.getByRole("button", { name: "加载更多试题" }));
        expect(await screen.findByRole("button", { name: "添加试题 第二页试题" })).toBeInTheDocument();
        expect(api.listQuestions).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }));
    });

    it("previews resolved targets before publishing", async () => {
        render(() => <PaperReleasePage />);
        await screen.findByText("高一 1 班");
        fireEvent.change(screen.getByLabelText("选择试卷"), { target: { value: "42" } });
        fireEvent.click(screen.getByLabelText("高一 1 班"));
        fireEvent.click(screen.getByLabelText("小明"));
        await waitFor(() => expect(api.getClassroomStudents).toHaveBeenCalledWith(31));
        expect(await screen.findByText("最终目标 2 人")).toBeInTheDocument();
        expect(screen.getByText("小红")).toBeInTheDocument();
        expect(screen.getAllByText("高一 1 班").length).toBeGreaterThan(1);
        expect(screen.getByText("补充选择", { selector: "span" })).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "发布试卷" }));
        await waitFor(() => expect(api.publishPaper).toHaveBeenCalledWith(expect.objectContaining({ paperId: 42, classroomIds: [31], studentIds: [12] })));
    });

    it("lets teachers copy but not select another teacher's READY paper for publishing", async () => {
        vi.mocked(api.listPapers).mockResolvedValue({
            ...emptyPage,
            content: [paper, { ...paper, id: 43, title: "他人可复用卷", ownerUserId: 8, status: "READY" }],
            totalElements: 2,
            totalPages: 1,
            numberOfElements: 2,
        });
        render(() => <PaperReleasePage />);
        const selector = await screen.findByLabelText("选择试卷");
        expect(within(selector).queryByRole("option", { name: /他人可复用卷/ })).not.toBeInTheDocument();
        fireEvent.click(await screen.findByRole("button", { name: "复制后编辑 他人可复用卷" }));
        await waitFor(() => expect(api.copyPaper).toHaveBeenCalledWith(43, expect.any(Object)));
    });

    it("hides terminal correction commands and displays audit state", async () => {
        vi.mocked(api.listPaperReleases).mockResolvedValue([{
            ...release,
            status: "INVALIDATED",
            invalidatedAt: "2026-07-29T12:00:00",
            invalidatedByUserId: 7,
            invalidateReason: "答案配置错误",
        }]);
        render(() => <PaperReleasePage />);
        expect(await screen.findByText(/答案配置错误/)).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "撤回" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "作废" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "替换" })).not.toBeInTheDocument();
    });

    it("requires a reason and confirmation for a correctable release action", async () => {
        render(() => <PaperReleasePage />);
        const reason = await screen.findByLabelText("纠正原因");
        expect(screen.getByRole("button", { name: "作废" })).toBeDisabled();
        fireEvent.input(reason, { target: { value: "答案配置错误" } });
        fireEvent.click(screen.getByRole("button", { name: "作废" }));
        await waitFor(() => expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("写入审计记录")));
        expect(api.invalidatePaperRelease).toHaveBeenCalledWith(77, { reason: "答案配置错误" });
    });

    it("renders responsive tables and keyboard-operable release selection", async () => {
        render(() => <PaperReleasePage />);
        const selectRelease = await screen.findByRole("button", { name: "选择发布 四级周测" });
        selectRelease.focus();
        expect(selectRelease).toHaveFocus();
        fireEvent.keyDown(selectRelease, { key: "Enter" });
        fireEvent.click(selectRelease);
        expect(screen.getByTestId("release-table-scroll")).toHaveClass("overflow-x-auto");
    });

    it("supports error retry and prevents duplicate question submission", async () => {
        vi.mocked(api.listQuestions)
            .mockRejectedValueOnce(new Error("网络暂时不可用"))
            .mockResolvedValue({ ...emptyPage, content: [question], totalElements: 1, totalPages: 1, numberOfElements: 1 });
        render(() => <QuestionBankPage />);
        expect(await screen.findByText("网络暂时不可用")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "重试加载题库" }));
        expect(await screen.findByText(question.stem)).toBeInTheDocument();

        let resolveCreate!: (value: typeof question) => void;
        vi.mocked(api.createQuestion).mockImplementation(() => new Promise((resolve) => { resolveCreate = resolve; }));
        fireEvent.click(screen.getByRole("button", { name: "新建试题" }));
        const form = screen.getByRole("form", { name: "试题表单" });
        fireEvent.input(within(form).getByLabelText("题干"), { target: { value: "防重复提交" } });
        fireEvent.input(within(form).getByLabelText("选项 A"), { target: { value: "一" } });
        fireEvent.input(within(form).getByLabelText("选项 B"), { target: { value: "二" } });
        fireEvent.input(within(form).getByLabelText("正确答案"), { target: { value: "A" } });
        const save = within(form).getByRole("button", { name: "保存试题" });
        fireEvent.click(save);
        fireEvent.click(save);
        expect(api.createQuestion).toHaveBeenCalledTimes(1);
        resolveCreate(question);
    });

    it("loads release result details from the route and shows frozen answers", async () => {
        render(() => <PaperResultPage />);
        expect((await screen.findAllByText("超时提交")).length).toBeGreaterThan(0);
        expect(screen.getByText("student_api_20260729")).toBeInTheDocument();
        expect(api.getPaperReleaseResults).toHaveBeenCalledWith(77);
        fireEvent.click(screen.getByRole("button", { name: "查看学生 12" }));
        expect(await screen.findByText("A：放弃")).toBeInTheDocument();
        expect(screen.getByText("正确答案：A")).toBeInTheDocument();
    });
});
