import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { PointsPage } from "@/pages/points-page";
import type { PaginatedResponse, UserRole } from "@/types/api";

const authState = vi.hoisted(() => ({ role: "ADMIN" as UserRole }));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({ id: 1, username: "operator", displayName: "Operator", role: authState.role, status: "ACTIVE" }),
    }),
}));

vi.mock("@/lib/api", () => ({
    api: {
        listAdminPointAccounts: vi.fn(),
        listAdminPointTransactions: vi.fn(),
        listAdminPointEvents: vi.fn(),
        listPointEventAttempts: vi.fn(),
        retryPointEvent: vi.fn(),
        cancelPointEvent: vi.fn(),
        reversePointTransaction: vi.fn(),
        adjustAdminStudentPoints: vi.fn(),
        listPointRules: vi.fn(),
        createPointRule: vi.fn(),
        updatePointRule: vi.fn(),
        listClassrooms: vi.fn(),
        listStudyPlans: vi.fn(),
        listTeacherPointStudents: vi.fn(),
        getTeacherStudentPointSummary: vi.fn(),
        listTeacherStudentPointTransactions: vi.fn(),
        adjustTeacherStudentPoints: vi.fn(),
    },
}));

const page = <T,>(content: T[]): PaginatedResponse<T> => ({
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 20,
    number: 0,
    numberOfElements: content.length,
    first: true,
    last: true,
    empty: content.length === 0,
});

describe("PointsPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authState.role = "ADMIN";
        vi.mocked(api.listAdminPointAccounts).mockResolvedValue(page([]));
        vi.mocked(api.listAdminPointTransactions).mockResolvedValue(page([]));
        vi.mocked(api.listAdminPointEvents).mockResolvedValue(page([]));
        vi.mocked(api.listPointRules).mockResolvedValue([]);
        vi.mocked(api.listClassrooms).mockResolvedValue([]);
        vi.mocked(api.listStudyPlans).mockResolvedValue([]);
        vi.mocked(api.listTeacherPointStudents).mockResolvedValue(page([]));
        vi.mocked(api.listTeacherStudentPointTransactions).mockResolvedValue(page([]));
    });

    it("shows the administrator work views and account balances", async () => {
        vi.mocked(api.listAdminPointAccounts).mockResolvedValue(page([{
            accountId: 10,
            studentId: 42,
            studentName: "小明",
            availablePoints: 120,
            frozenPoints: 0,
            lifetimeEarnedPoints: 180,
            lifetimeSpentPoints: 60,
            status: "ACTIVE",
            updatedAt: "2026-07-22T04:00:00",
        }]));

        render(() => <PointsPage />);

        expect(await screen.findByText("小明")).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "账户" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "流水" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "事件" })).toBeInTheDocument();
        expect(screen.getByRole("tab", { name: "规则" })).toBeInTheDocument();
        expect(screen.getByText("120")).toBeInTheDocument();
        expect(api.listAdminPointAccounts).toHaveBeenCalledWith({ page: 0, size: 20 });
        expect(api.listTeacherPointStudents).not.toHaveBeenCalled();
    });

    it("shows student usernames instead of numeric IDs in admin point tables", async () => {
        vi.mocked(api.listAdminPointAccounts).mockResolvedValue(page([{
            accountId: 10,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            availablePoints: 120,
            frozenPoints: 0,
            lifetimeEarnedPoints: 180,
            lifetimeSpentPoints: 60,
            status: "ACTIVE",
        }]));
        vi.mocked(api.listAdminPointTransactions).mockResolvedValue(page([{
            id: 91,
            accountId: 10,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            transactionType: "EARN",
            amount: 5,
            balanceBefore: 115,
            balanceAfter: 120,
            sourceType: "STUDY_RECORD",
            sourceKey: "study:13",
            ruleCode: "STUDY_RECORD_COMPLETE",
        }]));
        vi.mocked(api.listAdminPointEvents).mockResolvedValue(page([{
            id: 7,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            sourceType: "STUDY_RECORD",
            sourceId: 13,
            sourceKey: "study:13",
            ruleCode: "STUDY_RECORD_COMPLETE",
            ruleName: "完成学习记录",
            points: 5,
            status: "FAILED",
            autoAttemptCount: 3,
        }]));

        render(() => <PointsPage />);

        expect(await screen.findByText("student42")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("tab", { name: "流水" }));
        expect(await screen.findByText("student42")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("tab", { name: "事件" }));
        expect(await screen.findByText("student42")).toBeInTheDocument();
        expect(screen.queryByText("#42")).not.toBeInTheDocument();
    });

    it("shows transaction sources in Chinese in the source and reason column", async () => {
        vi.mocked(api.listAdminPointTransactions).mockResolvedValue(page([{
            id: 6,
            accountId: 10,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            transactionType: "EARN",
            amount: 2,
            balanceBefore: 14,
            balanceAfter: 16,
            sourceType: "VIDEO_WATCH",
            sourceKey: "classroom-video:1:14:student:4:completed",
            ruleCode: "VIDEO_WATCH",
        }, {
            id: 5,
            accountId: 10,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            transactionType: "EARN",
            amount: 1,
            balanceBefore: 13,
            balanceAfter: 14,
            sourceType: "MANUAL_ADJUSTMENT",
            sourceKey: "manual:5",
            ruleCode: "MANUAL_ADJUSTMENT",
            reason: "管理员奖励1分首次完成者",
        }, {
            id: 3,
            accountId: 10,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            transactionType: "EARN",
            amount: 10,
            balanceBefore: 2,
            balanceAfter: 12,
            sourceType: "STUDY_TASK",
            sourceKey: "study-day-task:1:completed",
            ruleCode: "DAILY_TASK_COMPLETED",
        }, {
            id: 2,
            accountId: 10,
            studentId: 42,
            studentUsername: "student42",
            studentName: "小明",
            transactionType: "EARN",
            amount: 1,
            balanceBefore: 1,
            balanceAfter: 2,
            sourceType: "STUDY_RECORD",
            sourceKey: "study-record:3:correct",
            ruleCode: "STUDY_RECORD_CORRECT",
        }]));

        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "流水" }));

        expect(await screen.findByText("视频观看")).toBeInTheDocument();
        expect(screen.getByText("人工调整")).toBeInTheDocument();
        expect(screen.getByText("完成每日任务")).toBeInTheDocument();
        expect(screen.getByText("单词答对")).toBeInTheDocument();
        expect(screen.queryByText("VIDEO_WATCH")).not.toBeInTheDocument();
        expect(screen.queryByText("MANUAL_ADJUSTMENT")).not.toBeInTheDocument();
        expect(screen.queryByText("DAILY_TASK_COMPLETED")).not.toBeInTheDocument();
        expect(screen.queryByText("STUDY_RECORD_CORRECT")).not.toBeInTheDocument();
    });

    it("requires a reason before confirming a failed event retry", async () => {
        vi.mocked(api.listAdminPointEvents).mockResolvedValue(page([{
            id: 7,
            studentId: 42,
            sourceType: "STUDY_RECORD",
            sourceId: 13,
            sourceKey: "study:13",
            ruleCode: "STUDY_RECORD_COMPLETE",
            ruleName: "完成学习记录",
            points: 5,
            status: "FAILED",
            autoAttemptCount: 3,
            lastError: "database unavailable",
        }]));
        vi.mocked(api.retryPointEvent).mockResolvedValue({
            id: 7,
            studentId: 42,
            sourceType: "STUDY_RECORD",
            sourceKey: "study:13",
            ruleCode: "STUDY_RECORD_COMPLETE",
            ruleName: "完成学习记录",
            points: 5,
            status: "PENDING",
            autoAttemptCount: 3,
        });

        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "事件" }));
        expect(await screen.findByText("database unavailable")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "手动重试" }));

        const confirm = screen.getByRole("button", { name: "确认重试" });
        expect(confirm).toBeDisabled();
        await fireEvent.input(screen.getByLabelText("操作原因"), { target: { value: "数据库恢复，核对后重试" } });
        expect(confirm).not.toBeDisabled();
        await fireEvent.click(confirm);

        await waitFor(() => expect(api.retryPointEvent).toHaveBeenCalledWith(7, {
            reason: "数据库恢复，核对后重试",
        }));
    });

    it("does not offer cancellation while an event is processing", async () => {
        vi.mocked(api.listAdminPointEvents).mockResolvedValue(page([{
            id: 8,
            studentId: 42,
            sourceType: "STUDY_RECORD",
            sourceId: 14,
            sourceKey: "study:14",
            ruleCode: "STUDY_RECORD_CORRECT",
            ruleName: "答对单词",
            points: 1,
            status: "PROCESSING",
            autoAttemptCount: 1,
        }]));

        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "事件" }));
        expect(await screen.findByText("study:14")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "取消" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "手动重试" })).not.toBeInTheDocument();
    });

    it("uses Chinese source and scope pickers and derives the hidden rule code from source type", async () => {
        vi.mocked(api.listClassrooms).mockResolvedValue([{
            id: 3,
            name: "一班",
            teacherId: 2,
            teacherName: "王老师",
            studentCount: 12,
        }]);
        vi.mocked(api.listStudyPlans).mockResolvedValue([{
            id: 8,
            name: "四级计划",
            teacherId: 2,
            dictionaryId: 5,
            dictionaryName: "CET4",
            classroomIds: [3],
            startDate: "2026-07-23",
            timezone: "Asia/Shanghai",
            dailyNewCount: 10,
            dailyReviewLimit: 20,
            reviewMode: "FIXED_INTERVALS",
            reviewIntervals: [0, 1],
            completionThreshold: 80,
            dailyDeadlineTime: "22:00",
            attentionTrackingEnabled: false,
            minFocusSecondsPerWord: 1,
            maxFocusSecondsPerWord: 60,
            longStayWarningSeconds: 20,
            idleTimeoutSeconds: 60,
            status: "PUBLISHED",
            studentCount: 12,
        }]);
        vi.mocked(api.createPointRule).mockResolvedValue({
            id: 11,
            code: "STUDY_RECORD",
            name: "答对单词",
            sourceType: "MANUAL_ADJUSTMENT",
            basePoints: 3,
            enabled: true,
        });

        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "规则" }));
        await fireEvent.click(await screen.findByRole("button", { name: "新增规则" }));
        expect(screen.queryByLabelText("规则编码")).not.toBeInTheDocument();
        expect(await screen.findByRole("option", { name: "学习记录" })).toBeInTheDocument();
        await fireEvent.change(screen.getByLabelText("来源类型"), { target: { value: "STUDY_RECORD" } });
        await fireEvent.input(screen.getByLabelText("规则名称"), { target: { value: "答对单词" } });
        await fireEvent.input(screen.getByLabelText("基础分值"), { target: { value: "3" } });
        await fireEvent.change(screen.getByLabelText("范围类型"), { target: { value: "CLASSROOM" } });
        await fireEvent.change(await screen.findByLabelText("范围ID"), { target: { value: "3" } });

        const save = screen.getByRole("button", { name: "保存规则" });
        expect(save).not.toBeDisabled();
        await fireEvent.click(save);

        await waitFor(() => expect(api.createPointRule).toHaveBeenCalledWith(expect.objectContaining({
            code: "STUDY_RECORD",
            sourceType: "STUDY_RECORD",
            basePoints: 3,
            scopeType: "CLASSROOM",
            scopeId: 3,
            reason: "",
        })));
        expect(api.listClassrooms).toHaveBeenCalled();
        expect(api.listStudyPlans).toHaveBeenCalled();
    });

    it("marks rule name and base points as required while keeping reason optional", async () => {
        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "规则" }));
        await fireEvent.click(await screen.findByRole("button", { name: "新增规则" }));

        expect(screen.getByLabelText("规则名称")).toBeRequired();
        expect(screen.getByLabelText("基础分值")).toBeRequired();
        expect(screen.getByLabelText("变更原因")).not.toBeRequired();
    });

    it("shows point rule source and scope labels in Chinese when editing", async () => {
        vi.mocked(api.listPointRules).mockResolvedValue([{
            id: 11,
            code: "STUDY_PLAN",
            name: "计划奖励",
            sourceType: "STUDY_TASK",
            basePoints: 10,
            scopeType: "STUDY_PLAN",
            scopeId: 8,
            enabled: true,
        }]);
        vi.mocked(api.listClassrooms).mockResolvedValue([]);
        vi.mocked(api.listStudyPlans).mockResolvedValue([{
            id: 8,
            name: "四级计划",
            teacherId: 2,
            dictionaryId: 5,
            dictionaryName: "CET4",
            classroomIds: [],
            startDate: "2026-07-23",
            timezone: "Asia/Shanghai",
            dailyNewCount: 10,
            dailyReviewLimit: 20,
            reviewMode: "FIXED_INTERVALS",
            reviewIntervals: [0],
            completionThreshold: 80,
            dailyDeadlineTime: "22:00",
            attentionTrackingEnabled: false,
            minFocusSecondsPerWord: 1,
            maxFocusSecondsPerWord: 60,
            longStayWarningSeconds: 20,
            idleTimeoutSeconds: 60,
            status: "PUBLISHED",
            studentCount: 12,
        }]);
        vi.mocked(api.updatePointRule).mockResolvedValue({
            id: 11,
            code: "STUDY_PLAN",
            name: "计划奖励",
            sourceType: "STUDY_TASK",
            basePoints: 10,
            scopeType: "STUDY_PLAN",
            scopeId: 8,
            enabled: true,
        });

        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "规则" }));

        expect(await screen.findByText("学习任务")).toBeInTheDocument();
        expect(screen.getByText("学习计划 · 四级计划")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "编辑" }));

        expect(screen.queryByLabelText("规则编码")).not.toBeInTheDocument();
        expect(screen.getByLabelText("来源类型")).toHaveValue("STUDY_TASK");
        expect(screen.getByLabelText("范围类型")).toHaveValue("STUDY_PLAN");
        expect(screen.getByLabelText("范围ID")).toHaveValue("8");
        await fireEvent.click(screen.getByRole("button", { name: "保存规则" }));

        await waitFor(() => expect(api.updatePointRule).toHaveBeenCalledWith(11, expect.objectContaining({
            name: "计划奖励",
            basePoints: 10,
            reason: "",
        })));
    });

    it("shows only managed students to teachers and loads the selected student's transactions", async () => {
        authState.role = "TEACHER";
        vi.mocked(api.listTeacherPointStudents).mockResolvedValue(page([{
            studentId: 42,
            studentName: "小明",
            availablePoints: 120,
            lifetimeEarnedPoints: 180,
            lifetimeSpentPoints: 60,
            todayEarnedPoints: 5,
        }]));
        vi.mocked(api.listTeacherStudentPointTransactions).mockResolvedValue(page([{
            id: 91,
            accountId: 10,
            studentId: 42,
            transactionType: "EARN",
            amount: 5,
            balanceBefore: 115,
            balanceAfter: 120,
            sourceType: "STUDY_RECORD",
            sourceKey: "study:13",
            ruleCode: "STUDY_RECORD_COMPLETE",
            reason: "完成学习",
        }]));

        render(() => <PointsPage />);
        expect(await screen.findByText("小明")).toBeInTheDocument();
        expect(screen.queryByRole("tab", { name: "规则" })).not.toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "查看流水" }));

        expect(await screen.findByText("完成学习")).toBeInTheDocument();
        expect(api.listTeacherStudentPointTransactions).toHaveBeenCalledWith(42, { page: 0, size: 20 });
    });

    it("reuses the same adjustment requestKey after a failed teacher submission", async () => {
        authState.role = "TEACHER";
        vi.mocked(api.listTeacherPointStudents).mockResolvedValue(page([{
            studentId: 42,
            studentName: "小明",
            availablePoints: 120,
            lifetimeEarnedPoints: 180,
            lifetimeSpentPoints: 60,
            todayEarnedPoints: 5,
        }]));
        vi.mocked(api.adjustTeacherStudentPoints)
            .mockRejectedValueOnce(new Error("network"))
            .mockResolvedValueOnce({ requestId: 5, eventId: 8, status: "SUCCEEDED", transactionId: 9, availableBalance: 125 });

        render(() => <PointsPage />);
        expect(await screen.findByText("小明")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "人工调整" }));
        await fireEvent.input(screen.getByLabelText("调整分值"), { target: { value: "5" } });
        await fireEvent.input(screen.getByLabelText("调整原因"), { target: { value: "课堂表现优秀" } });
        await fireEvent.click(screen.getByRole("button", { name: "确认调整" }));
        expect(await screen.findByText("network")).toBeInTheDocument();
        await fireEvent.click(screen.getByRole("button", { name: "确认调整" }));

        await waitFor(() => expect(api.adjustTeacherStudentPoints).toHaveBeenCalledTimes(2));
        const firstPayload = vi.mocked(api.adjustTeacherStudentPoints).mock.calls[0]?.[1];
        const secondPayload = vi.mocked(api.adjustTeacherStudentPoints).mock.calls[1]?.[1];
        expect(firstPayload?.requestKey).toBeTruthy();
        expect(secondPayload?.requestKey).toBe(firstPayload?.requestKey);
    });
});
