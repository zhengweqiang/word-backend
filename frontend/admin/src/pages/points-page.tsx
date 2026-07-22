import { CircleDollarSign } from "lucide-solid";
import { Show } from "solid-js";
import { AdminPointsWorkspace } from "@/components/points/admin-points-workspace";
import { TeacherPointsWorkspace } from "@/components/points/teacher-points-workspace";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";

export function PointsPage() {
    const auth = useAuth();
    const isAdmin = () => auth.user()?.role === "ADMIN";

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow={isAdmin() ? "ADMIN OPERATIONS" : "TEACHER WORKSPACE"}
                title="积分管理"
                description={isAdmin()
                    ? "集中处理积分账户、流水、事件和规则；所有人工与风险操作保留原因和审计记录。"
                    : "查看受管学生的积分余额与流水，并按实际教学情况进行人工加减分。"}
                actions={<div class="flex h-10 w-10 items-center justify-center rounded-lg border border-border bg-background"><CircleDollarSign class="h-5 w-5 text-primary" /></div>}
            />
            <Show when={isAdmin()} fallback={<TeacherPointsWorkspace />}>
                <AdminPointsWorkspace />
            </Show>
        </section>
    );
}
