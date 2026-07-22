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
        user: () => ({
            id: 1,
            username: "admin",
            displayName: "Admin",
            role: "ADMIN",
            status: "ACTIVE",
        }),
    }),
}));

const createdPlan = {
    id: 99,
    name: "四级计划",
    teacherId: 1,
    dictionaryId: 7,
    dictionaryName: "CET-4 核心词汇",
    classroomIds: [12],
    startDate: "2026-06-21",
    endDate: null,
    timezone: "Asia/Shanghai",
    dailyNewCount: 20,
    dailyReviewLimit: 60,
    reviewMode: "FIXED_INTERVAL",
    reviewIntervals: [0, 1, 3, 7, 14],
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
        vi.resetAllMocks();
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
        vi.mocked(api.listClassrooms).mockResolvedValue([
            {
                id: 12,
                name: "高一 1 班",
                description: null,
                teacherId: 1,
                teacherName: "Admin",
                studentCount: 3,
            },
        ]);
        render(() => <StudyPlansPage />);

        fireEvent.input(await screen.findByLabelText("计划名称"), {
            target: { value: "四级计划" },
        });
        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.input(screen.getByPlaceholderText("搜索词书名称"), {
            target: { value: "CET-4" },
        });
        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));
        fireEvent.click(screen.getByLabelText("高一 1 班"));
        const createButton = screen.getByRole("button", { name: "创建计划" });
        await waitFor(() => expect(createButton).toBeEnabled());
        fireEvent.click(createButton);

        await waitFor(() => {
            expect(api.createStudyPlan).toHaveBeenCalledWith(
                expect.objectContaining({
                    name: "四级计划",
                    dictionaryId: 7,
                    classroomIds: [12],
                    reviewIntervals: [0, 1, 3, 7, 14],
                }),
            );
        });
    });

    it("blocks review intervals that do not start with zero and explains the correction", async () => {
        vi.mocked(api.listClassrooms).mockResolvedValue([
            {
                id: 12,
                name: "高一 1 班",
                description: null,
                teacherId: 1,
                teacherName: "Admin",
                studentCount: 3,
            },
        ]);
        render(() => <StudyPlansPage />);

        fireEvent.input(await screen.findByLabelText("计划名称"), {
            target: { value: "四级计划" },
        });
        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));
        fireEvent.click(screen.getByLabelText("高一 1 班"));
        fireEvent.input(screen.getByLabelText("复习间隔"), {
            target: { value: "1,3,7,14" },
        });
        const createButton = screen.getByRole("button", { name: "创建计划" });
        await waitFor(() => expect(createButton).toBeEnabled());
        fireEvent.click(createButton);

        expect(await screen.findByText("复习间隔必须从 0 开始，例如：0,1,3,7,14。")).toBeInTheDocument();
        expect(api.createStudyPlan).not.toHaveBeenCalled();
    });

    it("reloads the common dictionaries and clears an invalid selection when classrooms change", async () => {
        vi.mocked(api.listClassrooms).mockResolvedValue([
            {
                id: 12,
                name: "高一 1 班",
                description: null,
                teacherId: 1,
                teacherName: "Admin",
                studentCount: 3,
            },
        ]);
        vi.mocked(api.listDictionaries)
            .mockResolvedValueOnce([
                { id: 7, name: "班级共同词书", wordCount: 1200 },
                { id: 8, name: "未分配词书", wordCount: 860 },
            ])
            .mockResolvedValueOnce([
                { id: 7, name: "班级共同词书", wordCount: 1200 },
            ]);

        render(() => <StudyPlansPage />);

        fireEvent.click(await screen.findByRole("button", { name: "选择词书" }));
        fireEvent.click(screen.getByRole("option", { name: "未分配词书 860 词" }));
        fireEvent.click(screen.getByLabelText("高一 1 班"));

        await waitFor(() => expect(api.listDictionaries).toHaveBeenLastCalledWith([12]));
        expect(await screen.findByRole("button", { name: "选择词书" })).toBeInTheDocument();
        expect(screen.getByText("所选词书不适用于当前班级，请重新选择共同可用的词书。")).toBeInTheDocument();
    });

    it("shows the backend reason when creating a study plan fails", async () => {
        vi.mocked(api.listClassrooms).mockResolvedValue([
            {
                id: 12,
                name: "高一 1 班",
                description: null,
                teacherId: 1,
                teacherName: "Admin",
                studentCount: 3,
            },
        ]);
        vi.mocked(api.createStudyPlan).mockRejectedValue(
            new Error("dictionaryId is not associated with all selected classrooms"),
        );

        render(() => <StudyPlansPage />);

        fireEvent.input(await screen.findByLabelText("计划名称"), {
            target: { value: "四级计划" },
        });
        fireEvent.click(screen.getByRole("button", { name: "选择词书" }));
        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));
        fireEvent.click(screen.getByLabelText("高一 1 班"));
        const createButton = screen.getByRole("button", { name: "创建计划" });
        await waitFor(() => expect(createButton).toBeEnabled());
        fireEvent.click(createButton);

        expect(await screen.findByText("所选词书未分配给全部班级，请重新选择。")).toBeInTheDocument();
    });

    it("shows an empty classroom state and disables creation when no classrooms are available", async () => {
        render(() => <StudyPlansPage />);

        expect(await screen.findByText("暂无可用班级")).toBeInTheDocument();
        expect(screen.getByText("请先在“班级管理”中创建班级，再回来创建学习计划。")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "去班级管理" })).toHaveAttribute("href", "/classrooms");
        expect(screen.getByRole("button", { name: "创建计划" })).toBeDisabled();
    });

    it("keeps the visible wordbook field purpose associated with the selected trigger", async () => {
        render(() => <StudyPlansPage />);

        fireEvent.click(await screen.findByRole("button", { name: "选择词书" }));
        fireEvent.click(screen.getByRole("option", { name: "CET-4 核心词汇 1200 词" }));

        const trigger = screen.getByRole("button", { name: "词书：CET-4 核心词汇，1200 词" });
        expect(trigger).toHaveAttribute("id", "study-plan-dictionary");
        expect(screen.getByText("词书", { selector: "label" })).toHaveAttribute(
            "for",
            "study-plan-dictionary",
        );
    });

    it("shows review mode choices in Chinese while preserving backend values", async () => {
        render(() => <StudyPlansPage />);

        expect(await screen.findByRole("option", { name: "固定间隔" })).toHaveValue("FIXED_INTERVAL");
        expect(screen.getByRole("option", { name: "艾宾浩斯" })).toHaveValue("EBBINGHAUS");
        expect(screen.getByRole("option", { name: "自定义" })).toHaveValue("CUSTOM");
    });

    it("shows the review mode picker with the same full-width layout as other form controls", async () => {
        render(() => <StudyPlansPage />);

        const reviewModeSelect = (await screen.findByRole("option", { name: "固定间隔" }))
            .closest("select");

        expect(reviewModeSelect).toHaveClass("w-full");
    });
});
