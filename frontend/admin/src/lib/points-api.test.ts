import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";

const jsonResponse = (body: unknown = {}) => new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
});

describe("student points API", () => {
    beforeEach(() => {
        vi.stubGlobal("fetch", vi.fn().mockImplementation(() => Promise.resolve(jsonResponse())));
    });

    it("keeps admin list pagination 0-based and forwards event status", async () => {
        await api.listAdminPointAccounts({ page: 0, size: 20 });
        await api.listAdminPointEvents({ page: 2, size: 20, status: "FAILED" });

        expect(fetch).toHaveBeenNthCalledWith(
            1,
            "/api/admin/points/accounts?page=0&size=20",
            expect.objectContaining({ credentials: "include" }),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/admin/points/events?page=2&size=20&status=FAILED",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    it("uses the audited admin operation endpoints and payloads", async () => {
        await api.retryPointEvent(7, { reason: "核对后重试" });
        await api.cancelPointEvent(8, { reason: "业务已撤销" });
        await api.reversePointTransaction(9, { reason: "重复发放" });

        expect(fetch).toHaveBeenNthCalledWith(
            1,
            "/api/admin/points/events/7/retry",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ reason: "核对后重试" }) }),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/admin/points/events/8/cancel",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ reason: "业务已撤销" }) }),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            3,
            "/api/admin/points/transactions/9/reverse",
            expect.objectContaining({ method: "POST", body: JSON.stringify({ reason: "重复发放" }) }),
        );
    });

    it("uses role-specific student adjustment paths with a stable request key", async () => {
        const payload = { requestKey: "point-adjustment:teacher:42:fixed", amount: 5, reason: "课堂表现" };

        await api.listTeacherPointStudents({ page: 0, size: 20, name: "小明" });
        await api.adjustTeacherStudentPoints(42, payload);
        await api.adjustAdminStudentPoints(42, payload);

        expect(fetch).toHaveBeenNthCalledWith(
            1,
            "/api/teachers/me/points/students?page=0&size=20&name=%E5%B0%8F%E6%98%8E",
            expect.any(Object),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/teachers/me/points/students/42/adjustments",
            expect.objectContaining({ method: "POST", body: JSON.stringify(payload) }),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            3,
            "/api/admin/points/students/42/adjustments",
            expect.objectContaining({ method: "POST", body: JSON.stringify(payload) }),
        );
    });

    it("sends audit reasons when creating and updating rules", async () => {
        const createPayload = {
            code: "CLASSROOM_HELP",
            name: "课堂互助",
            description: "帮助同学完成课堂任务",
            sourceType: "MANUAL_ADJUSTMENT" as const,
            basePoints: 3,
            enabled: true,
            reason: "新增课堂激励规则",
        };
        const updatePayload = {
            name: "课堂互助",
            description: "帮助同学完成课堂任务",
            sourceType: "MANUAL_ADJUSTMENT" as const,
            basePoints: 5,
            enabled: true,
            reason: "根据教研会议调整分值",
        };

        await api.createPointRule(createPayload);
        await api.updatePointRule(11, updatePayload);

        expect(fetch).toHaveBeenNthCalledWith(
            1,
            "/api/admin/points/rules",
            expect.objectContaining({ method: "POST", body: JSON.stringify(createPayload) }),
        );
        expect(fetch).toHaveBeenNthCalledWith(
            2,
            "/api/admin/points/rules/11",
            expect.objectContaining({ method: "PUT", body: JSON.stringify(updatePayload) }),
        );
    });
});
