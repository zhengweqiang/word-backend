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

    it("requires and submits an audit reason when creating a rule", async () => {
        vi.mocked(api.createPointRule).mockResolvedValue({
            id: 11,
            code: "CLASSROOM_HELP",
            name: "课堂互助",
            sourceType: "MANUAL_ADJUSTMENT",
            basePoints: 3,
            enabled: true,
        });

        render(() => <PointsPage />);
        await fireEvent.click(screen.getByRole("tab", { name: "规则" }));
        await fireEvent.click(await screen.findByRole("button", { name: "新增规则" }));
        await fireEvent.input(screen.getByLabelText("规则编码"), { target: { value: "CLASSROOM_HELP" } });
        await fireEvent.input(screen.getByLabelText("规则名称"), { target: { value: "课堂互助" } });
        await fireEvent.input(screen.getByLabelText("基础分值"), { target: { value: "3" } });

        const save = screen.getByRole("button", { name: "保存规则" });
        expect(save).toBeDisabled();
        await fireEvent.input(screen.getByLabelText("变更原因"), { target: { value: "新增课堂激励规则" } });
        expect(save).not.toBeDisabled();
        await fireEvent.click(save);

        await waitFor(() => expect(api.createPointRule).toHaveBeenCalledWith(expect.objectContaining({
            code: "CLASSROOM_HELP",
            basePoints: 3,
            reason: "新增课堂激励规则",
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
