import { Plus, X } from "lucide-solid";
import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeaderCell,
    TableRoot,
    TableRow,
} from "@/components/ui/table";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { enumLabel, formatDateTime } from "@/lib/format";
import type { PaginatedResponse, UserResponse, UserRole, UserStatus } from "@/types/api";

interface AdminUserPageData {
    usersPage: PaginatedResponse<UserResponse>;
}

interface TeacherUserPageData {
    studentsPage: PaginatedResponse<UserResponse>;
}

const userRoles: UserRole[] = ["ADMIN", "TEACHER", "STUDENT"];
const userStatuses: UserStatus[] = ["ACTIVE", "DISABLED", "LOCKED"];
const PAGE_SIZE = 20;

const createDefaultForm = () => ({
    username: "",
    password: "",
    displayName: "",
    email: "",
    phone: "",
    role: "TEACHER" as UserRole,
});

export function UsersPage() {
    const auth = useAuth();
    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");
    const [feedback, setFeedback] = createSignal("");
    const [isCreateDialogOpen, setIsCreateDialogOpen] = createSignal(false);
    const [nameFilter, setNameFilter] = createSignal("");
    const [roleFilter, setRoleFilter] = createSignal<"ALL" | UserRole>("ALL");
    const [currentPage, setCurrentPage] = createSignal(1);
    const [createForm, setCreateForm] = createStore(createDefaultForm());

    const requestParams = createMemo(() => {
        const user = auth.user();
        if (!user) {
            return null;
        }

        return {
            userRole: user.role,
            page: currentPage(),
            size: PAGE_SIZE,
            role: roleFilter() === "ALL" ? undefined : roleFilter(),
            name: nameFilter().trim() || undefined,
        };
    });

    const [pageData, { refetch }] = createResource(
        requestParams,
        async (params): Promise<AdminUserPageData | TeacherUserPageData | null> => {
            if (!params) {
                return null;
            }

            if (params.userRole === "ADMIN") {
                return {
                    usersPage: await api.listUsersPage({
                        page: params.page,
                        size: params.size,
                        role: params.role,
                        name: params.name,
                    }),
                };
            }

            return {
                studentsPage: await api.listMyStudentsPage({
                    page: params.page,
                    size: params.size,
                    name: params.name,
                }),
            };
        },
    );

    const pagedUsers = createMemo(() => {
        const current = pageData();
        if (!current) {
            return null;
        }
        return isAdmin() ? (current as AdminUserPageData).usersPage : (current as TeacherUserPageData).studentsPage;
    });

    const currentUsers = createMemo(() => pagedUsers()?.content ?? []);
    const totalPages = createMemo(() => Math.max(1, pagedUsers()?.totalPages ?? 1));
    const pageSummary = createMemo(() => {
        const current = pagedUsers();
        if (!current || current.totalElements === 0) {
            return "暂无数据";
        }
        const start = current.number * current.size + 1;
        const end = start + current.numberOfElements - 1;
        return `第 ${start}-${end} 条，共 ${current.totalElements} 人`;
    });

    const mutateWithRefetch = async (runner: () => Promise<unknown>, successMessage: string) => {
        setFeedback("");
        await runner();
        setFeedback(successMessage);
        await refetch();
    };

    const resetCreateForm = () => {
        setCreateForm(createDefaultForm());
    };

    const closeCreateDialog = () => {
        setIsCreateDialogOpen(false);
        resetCreateForm();
    };

    const handleCreateUser = async (event: SubmitEvent) => {
        event.preventDefault();
        setFeedback("");
        await api.createUser({
            username: createForm.username.trim(),
            password: createForm.password,
            displayName: createForm.displayName.trim(),
            email: createForm.email.trim() || undefined,
            phone: createForm.phone.trim() || undefined,
            role: createForm.role,
        });
        setFeedback("用户已创建。");
        setCurrentPage(1);
        await refetch();
        closeCreateDialog();
    };

    const handleRoleFilterChange = (value: "ALL" | UserRole) => {
        setRoleFilter(value);
        setCurrentPage(1);
    };

    const handleNameFilterInput = (value: string) => {
        setNameFilter(value);
        setCurrentPage(1);
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Users"
                title={isAdmin() ? "用户管理" : "我的学生"}
                description={
                    isAdmin()
                        ? "管理员可以创建账号、调整角色和状态，并维护完整的用户清单。"
                        : "老师在这里快速查看自己负责的学生列表。"
                }
                actions={
                    <div class="flex flex-wrap items-center gap-3">
                        <Show when={isAdmin()}>
                            <Button onClick={() => setIsCreateDialogOpen(true)}>
                                <Plus class="h-4 w-4" />
                                创建用户
                            </Button>
                        </Show>
                        <Button variant="outline" onClick={() => void refetch()}>
                            刷新
                        </Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show
                when={pageData()}
                fallback={
                    <Card>
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载用户数据...</CardContent>
                    </Card>
                }
            >
                <Card>
                    <CardHeader class="gap-4">
                        <div class="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <CardTitle>{isAdmin() ? "用户清单" : "我的学生清单"}</CardTitle>
                                <CardDescription>
                                    {isAdmin()
                                        ? "支持按角色和姓名筛选，角色与状态变更会即时提交。"
                                        : "支持按学生姓名筛选当前老师账号下的学生。"}
                                </CardDescription>
                            </div>
                            <div
                                class={
                                    isAdmin()
                                        ? "grid w-full gap-3 md:w-auto md:min-w-[420px] md:grid-cols-[180px_minmax(0,1fr)]"
                                        : "w-full md:w-[280px]"
                                }
                            >
                                <Show when={isAdmin()}>
                                    <div class="space-y-2">
                                        <Label>角色</Label>
                                        <select
                                            class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                            value={roleFilter()}
                                            onChange={(event) =>
                                                handleRoleFilterChange(event.currentTarget.value as "ALL" | UserRole)
                                            }
                                        >
                                            <option value="ALL">全部角色</option>
                                            <For each={userRoles}>{(role) => <option value={role}>{role}</option>}</For>
                                        </select>
                                    </div>
                                </Show>
                                <div class="space-y-2">
                                    <Label>{isAdmin() ? "用户姓名" : "学生姓名"}</Label>
                                    <Input
                                        placeholder={isAdmin() ? "按用户姓名筛选" : "按学生姓名筛选"}
                                        value={nameFilter()}
                                        onInput={(event) => handleNameFilterInput(event.currentTarget.value)}
                                    />
                                </div>
                            </div>
                        </div>
                    </CardHeader>
                    <CardContent>
                        <Show
                            when={currentUsers().length > 0}
                            fallback={
                                <EmptyState
                                    title="暂无数据"
                                    description={
                                        isAdmin()
                                            ? "当前没有符合筛选条件的用户，或还未创建账号。"
                                            : "当前没有符合筛选条件的学生。"
                                    }
                                />
                            }
                        >
                            <Table>
                                <TableRoot>
                                    <TableHead>
                                        <tr>
                                            <TableHeaderCell>用户</TableHeaderCell>
                                            <TableHeaderCell>角色</TableHeaderCell>
                                            <TableHeaderCell>状态</TableHeaderCell>
                                            <TableHeaderCell>最近登录</TableHeaderCell>
                                            <Show when={isAdmin()}>
                                                <TableHeaderCell>标记</TableHeaderCell>
                                            </Show>
                                        </tr>
                                    </TableHead>
                                    <TableBody>
                                        <For each={currentUsers()}>
                                            {(user) => (
                                                <TableRow>
                                                    <TableCell>
                                                        <div>
                                                            <p class="font-medium text-foreground">{user.displayName}</p>
                                                            <p class="text-xs text-muted-foreground">{user.username}</p>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Show
                                                            when={isAdmin()}
                                                            fallback={<Badge variant="outline">{enumLabel(user.role)}</Badge>}
                                                        >
                                                            <select
                                                                class="h-10 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                                value={user.role}
                                                                onChange={(event) =>
                                                                    void mutateWithRefetch(
                                                                        () => api.updateUserRole(user.id, event.currentTarget.value),
                                                                        `已更新 ${user.displayName} 的角色。`,
                                                                    )
                                                                }
                                                            >
                                                                <For each={userRoles}>
                                                                    {(role) => <option value={role}>{role}</option>}
                                                                </For>
                                                            </select>
                                                        </Show>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Show
                                                            when={isAdmin()}
                                                            fallback={<Badge variant="outline">{enumLabel(user.status)}</Badge>}
                                                        >
                                                            <select
                                                                class="h-10 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                                value={user.status}
                                                                onChange={(event) =>
                                                                    void mutateWithRefetch(
                                                                        () => api.updateUserStatus(user.id, event.currentTarget.value),
                                                                        `已更新 ${user.displayName} 的状态。`,
                                                                    )
                                                                }
                                                            >
                                                                <For each={userStatuses}>
                                                                    {(status) => <option value={status}>{status}</option>}
                                                                </For>
                                                            </select>
                                                        </Show>
                                                    </TableCell>
                                                    <TableCell>{formatDateTime(user.lastLoginAt)}</TableCell>
                                                    <Show when={isAdmin()}>
                                                        <TableCell>
                                                            <Badge variant={user.role === "STUDENT" ? "secondary" : "default"}>
                                                                {enumLabel(user.role)}
                                                            </Badge>
                                                        </TableCell>
                                                    </Show>
                                                </TableRow>
                                            )}
                                        </For>
                                    </TableBody>
                                </TableRoot>
                            </Table>
                            <div class="mt-5 flex flex-col gap-3 border-t border-border/60 pt-4 md:flex-row md:items-center md:justify-between">
                                <p class="text-sm text-muted-foreground">{pageSummary()}</p>
                                <div class="flex items-center gap-2">
                                    <Button
                                        disabled={currentPage() === 1}
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
                                    >
                                        上一页
                                    </Button>
                                    <span class="min-w-[88px] text-center text-sm text-muted-foreground">
                                        {currentPage()} / {totalPages()}
                                    </span>
                                    <Button
                                        disabled={currentPage() === totalPages()}
                                        size="sm"
                                        variant="outline"
                                        onClick={() => setCurrentPage((page) => Math.min(totalPages(), page + 1))}
                                    >
                                        下一页
                                    </Button>
                                </div>
                            </div>
                        </Show>
                    </CardContent>
                </Card>
            </Show>

            <Show when={isCreateDialogOpen()}>
                <div
                    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-sm"
                    onClick={closeCreateDialog}
                >
                    <div
                        aria-labelledby="create-user-dialog-title"
                        aria-modal="true"
                        class="w-full max-w-2xl rounded-[28px] border border-border/70 bg-background p-6 shadow-2xl"
                        role="dialog"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="font-display text-2xl font-semibold tracking-tight" id="create-user-dialog-title">
                                    创建用户
                                </h2>
                                <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                    为管理员、老师或学生创建新账号。
                                </p>
                            </div>
                            <Button aria-label="关闭" size="sm" variant="ghost" onClick={closeCreateDialog}>
                                <X class="h-4 w-4" />
                            </Button>
                        </div>

                        <form class="mt-6 grid gap-4" onSubmit={handleCreateUser}>
                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label>用户名</Label>
                                    <Input
                                        required
                                        value={createForm.username}
                                        onInput={(event) => setCreateForm("username", event.currentTarget.value)}
                                    />
                                </div>
                                <div class="space-y-2">
                                    <Label>姓名</Label>
                                    <Input
                                        required
                                        value={createForm.displayName}
                                        onInput={(event) => setCreateForm("displayName", event.currentTarget.value)}
                                    />
                                </div>
                            </div>
                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label>密码</Label>
                                    <Input
                                        required
                                        type="password"
                                        value={createForm.password}
                                        onInput={(event) => setCreateForm("password", event.currentTarget.value)}
                                    />
                                </div>
                                <div class="space-y-2">
                                    <Label>角色</Label>
                                    <select
                                        class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                        value={createForm.role}
                                        onChange={(event) => setCreateForm("role", event.currentTarget.value as UserRole)}
                                    >
                                        <For each={userRoles}>{(role) => <option value={role}>{role}</option>}</For>
                                    </select>
                                </div>
                            </div>
                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label>邮箱</Label>
                                    <Input
                                        value={createForm.email}
                                        onInput={(event) => setCreateForm("email", event.currentTarget.value)}
                                    />
                                </div>
                                <div class="space-y-2">
                                    <Label>手机号</Label>
                                    <Input
                                        value={createForm.phone}
                                        onInput={(event) => setCreateForm("phone", event.currentTarget.value)}
                                    />
                                </div>
                            </div>
                            <div class="flex flex-wrap justify-end gap-3 pt-2">
                                <Button variant="outline" onClick={closeCreateDialog} type="button">
                                    取消
                                </Button>
                                <Button type="submit">创建用户</Button>
                            </div>
                        </form>
                    </div>
                </div>
            </Show>
        </section>
    );
}
