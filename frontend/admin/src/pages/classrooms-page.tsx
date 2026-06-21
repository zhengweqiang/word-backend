import { Plus, X } from "lucide-solid";
import { createEffect, createMemo, createResource, createSignal, For, Show } from "solid-js";
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
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { ClassroomResponse, Dictionary, PaginatedResponse, UserResponse } from "@/types/api";

interface ClassroomOptionsData {
    dictionaries: Dictionary[];
    teachers: UserResponse[];
    students: UserResponse[];
}

const PAGE_SIZE = 20;

const createDefaultForm = () => ({
    name: "",
    description: "",
    teacherId: "",
});

export function ClassroomsPage() {
    const auth = useAuth();
    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");
    const [feedback, setFeedback] = createSignal("");
    const [createError, setCreateError] = createSignal("");
    const [isCreateDialogOpen, setIsCreateDialogOpen] = createSignal(false);
    const [keyword, setKeyword] = createSignal("");
    const [sortBy, setSortBy] = createSignal<"createdAt" | "updatedAt" | "name">("createdAt");
    const [sortDir, setSortDir] = createSignal<"asc" | "desc">("desc");
    const [currentPage, setCurrentPage] = createSignal(1);
    const [selectedClassroomId, setSelectedClassroomId] = createSignal<number | null>(null);
    const [selectedDictionaryId, setSelectedDictionaryId] = createSignal("");
    const [selectedStudentId, setSelectedStudentId] = createSignal("");
    const [form, setForm] = createStore(createDefaultForm());

    const [optionsData] = createResource(
        () => auth.user(),
        async (user): Promise<ClassroomOptionsData | null> => {
            if (!user) {
                return null;
            }

            const [students, dictionaries, allUsers] = await Promise.all([
                api.listStudents(),
                api.listDictionaries(),
                user.role === "ADMIN" ? api.listUsers() : Promise.resolve<UserResponse[]>([]),
            ]);

            return {
                dictionaries,
                teachers: user.role === "ADMIN" ? allUsers.filter((item) => item.role === "TEACHER") : [],
                students,
            };
        },
    );

    const classroomParams = createMemo(() => {
        if (!auth.user()) {
            return null;
        }

        return {
            page: currentPage(),
            size: PAGE_SIZE,
            keyword: keyword().trim() || undefined,
            sortBy: sortBy(),
            sortDir: sortDir(),
        };
    });

    const [classroomsPage, { refetch: refetchClassrooms }] = createResource(
        classroomParams,
        async (params): Promise<PaginatedResponse<ClassroomResponse> | null> => {
            if (!params) {
                return null;
            }
            return api.listClassroomsPage(params);
        },
    );

    const [classroomMembers, { refetch: refetchMembers }] = createResource(
        selectedClassroomId,
        async (classroomId): Promise<UserResponse[]> => {
            if (!classroomId) {
                return [];
            }
            return api.getClassroomStudents(classroomId);
        },
    );

    const [classroomDictionaries, { refetch: refetchDictionaries }] = createResource(
        selectedClassroomId,
        async (classroomId): Promise<Dictionary[]> => {
            if (!classroomId) {
                return [];
            }
            return api.getClassroomDictionaries(classroomId);
        },
    );

    const currentClassrooms = createMemo(() => classroomsPage()?.content ?? []);
    const teacherOptions = createMemo(() => optionsData()?.teachers ?? []);
    const canCreateClassroom = createMemo(() => {
        if (!form.name.trim()) {
            return false;
        }
        if (isAdmin()) {
            return teacherOptions().length > 0 && Boolean(form.teacherId);
        }
        return true;
    });
    const selectedClassroom = createMemo(() => {
        const classroomId = selectedClassroomId();
        if (!classroomId) {
            return null;
        }
        return currentClassrooms().find((item) => item.id === classroomId) ?? null;
    });
    const totalPages = createMemo(() => Math.max(1, classroomsPage()?.totalPages ?? 1));
    const pageSummary = createMemo(() => {
        const current = classroomsPage();
        if (!current || current.totalElements === 0) {
            return "暂无数据";
        }
        const start = current.number * current.size + 1;
        const end = start + current.numberOfElements - 1;
        return `第 ${start}-${end} 条，共 ${current.totalElements} 个班级`;
    });

    createEffect(() => {
        const classrooms = currentClassrooms();
        if (classrooms.length === 0) {
            setSelectedClassroomId(null);
            return;
        }

        const currentSelected = selectedClassroomId();
        if (!currentSelected || !classrooms.some((classroom) => classroom.id === currentSelected)) {
            setSelectedClassroomId(classrooms[0].id);
        }
    });

    createEffect(() => {
        selectedClassroomId();
        setSelectedStudentId("");
        setSelectedDictionaryId("");
    });

    const mutateAndRefresh = async (
        runner: () => Promise<unknown>,
        successMessage: string,
        options?: { refreshMembers?: boolean; refreshDictionaries?: boolean },
    ) => {
        setFeedback("");
        await runner();
        setFeedback(successMessage);
        await refetchClassrooms();
        if (options?.refreshMembers) {
            await refetchMembers();
        }
        if (options?.refreshDictionaries) {
            await refetchDictionaries();
        }
    };

    const closeCreateDialog = () => {
        setIsCreateDialogOpen(false);
        setCreateError("");
        setForm(createDefaultForm());
    };

    const handleCreate = async (event: SubmitEvent) => {
        event.preventDefault();
        setFeedback("");
        setCreateError("");
        try {
            await api.createClassroom({
                name: form.name.trim(),
                description: form.description.trim() || undefined,
                teacherId: form.teacherId ? Number(form.teacherId) : undefined,
            });
            setFeedback("班级已创建。");
            setCurrentPage(1);
            await refetchClassrooms();
            closeCreateDialog();
        } catch (error) {
            setCreateError(error instanceof Error ? error.message : "创建班级失败，请稍后重试。");
        }
    };

    const handleKeywordInput = (value: string) => {
        setKeyword(value);
        setCurrentPage(1);
    };

    const handleSortByChange = (value: "createdAt" | "updatedAt" | "name") => {
        setSortBy(value);
        setCurrentPage(1);
    };

    const handleSortDirChange = (value: "asc" | "desc") => {
        setSortDir(value);
        setCurrentPage(1);
    };

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Classrooms"
                title="班级管理"
                description="班级列表支持分页、搜索和排序，学生与词书维护统一收敛到详情面板里。"
                actions={
                    <div class="flex flex-wrap items-center gap-3">
                        <Button onClick={() => setIsCreateDialogOpen(true)}>
                            <Plus class="h-4 w-4" />
                            创建班级
                        </Button>
                        <Button variant="outline" onClick={() => void refetchClassrooms()}>
                            刷新
                        </Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show
                when={classroomsPage()}
                fallback={
                    <Card>
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载班级数据...</CardContent>
                    </Card>
                }
            >
                <>
                    <Card>
                        <CardHeader class="gap-4">
                            <div class="flex flex-wrap items-start justify-between gap-4">
                                <div>
                                    <CardTitle>班级列表</CardTitle>
                                    <CardDescription>支持按班级名称或描述搜索，并按名称、创建时间、更新时间排序。</CardDescription>
                                </div>
                                <div class="grid w-full gap-3 lg:w-auto lg:min-w-[640px] lg:grid-cols-[minmax(0,1.4fr)_180px_160px]">
                                    <div class="space-y-2">
                                        <Label>搜索</Label>
                                        <Input
                                            placeholder="按班级名称或描述搜索"
                                            value={keyword()}
                                            onInput={(event) => handleKeywordInput(event.currentTarget.value)}
                                        />
                                    </div>
                                    <div class="space-y-2">
                                        <Label>排序字段</Label>
                                        <select
                                            class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                            value={sortBy()}
                                            onChange={(event) =>
                                                handleSortByChange(
                                                    event.currentTarget.value as "createdAt" | "updatedAt" | "name",
                                                )
                                            }
                                        >
                                            <option value="createdAt">创建时间</option>
                                            <option value="updatedAt">更新时间</option>
                                            <option value="name">班级名称</option>
                                        </select>
                                    </div>
                                    <div class="space-y-2">
                                        <Label>排序方向</Label>
                                        <select
                                            class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                            value={sortDir()}
                                            onChange={(event) =>
                                                handleSortDirChange(event.currentTarget.value as "asc" | "desc")
                                            }
                                        >
                                            <option value="desc">降序</option>
                                            <option value="asc">升序</option>
                                        </select>
                                    </div>
                                </div>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <Show
                                when={currentClassrooms().length > 0}
                                fallback={<EmptyState title="暂无班级" description="先创建班级，列表才会出现内容。" />}
                            >
                                <Table>
                                    <TableRoot>
                                        <TableHead>
                                            <tr>
                                                <TableHeaderCell>班级</TableHeaderCell>
                                                <TableHeaderCell>负责老师</TableHeaderCell>
                                                <TableHeaderCell>学生数</TableHeaderCell>
                                                <TableHeaderCell>更新时间</TableHeaderCell>
                                                <TableHeaderCell>操作</TableHeaderCell>
                                            </tr>
                                        </TableHead>
                                        <TableBody>
                                            <For each={currentClassrooms()}>
                                                {(classroom) => (
                                                    <TableRow>
                                                        <TableCell>
                                                            <button
                                                                class="space-y-1 text-left"
                                                                onClick={() => setSelectedClassroomId(classroom.id)}
                                                            >
                                                                <p class="font-medium text-foreground">{classroom.name}</p>
                                                                <p class="text-xs text-muted-foreground">
                                                                    {classroom.description || "暂无班级说明"}
                                                                </p>
                                                            </button>
                                                        </TableCell>
                                                        <TableCell>{classroom.teacherName}</TableCell>
                                                        <TableCell>
                                                            <Badge variant="outline">{classroom.studentCount} 人</Badge>
                                                        </TableCell>
                                                        <TableCell>{formatDateTime(classroom.updatedAt || classroom.createdAt)}</TableCell>
                                                        <TableCell>
                                                            <div class="flex flex-wrap gap-2">
                                                                <Button
                                                                    size="sm"
                                                                    variant={
                                                                        selectedClassroomId() === classroom.id ? "secondary" : "outline"
                                                                    }
                                                                    onClick={() => setSelectedClassroomId(classroom.id)}
                                                                >
                                                                    管理学生
                                                                </Button>
                                                                <Button
                                                                    size="sm"
                                                                    variant="destructive"
                                                                    onClick={() =>
                                                                        void mutateAndRefresh(
                                                                            () => api.deleteClassroom(classroom.id),
                                                                            `已删除班级 ${classroom.name}。`,
                                                                            { refreshMembers: selectedClassroomId() === classroom.id },
                                                                        )
                                                                    }
                                                                >
                                                                    删除
                                                                </Button>
                                                            </div>
                                                        </TableCell>
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

                    <Show
                        when={selectedClassroom()}
                        fallback={<EmptyState title="未选中班级" description="从上方列表选择一个班级后，在这里维护学生成员。" />}
                    >
                        {(classroom) => (
                                <Card>
                                    <CardHeader>
                                        <div class="flex flex-wrap items-start justify-between gap-4">
                                            <div>
                                                <CardTitle>{classroom().name}</CardTitle>
                                            <CardDescription>{classroom().description || "暂无班级说明"}</CardDescription>
                                        </div>
                                        <div class="rounded-2xl border border-border/70 bg-background/60 px-4 py-3 text-sm text-muted-foreground">
                                            负责老师：<span class="font-medium text-foreground">{classroom().teacherName}</span>
                                        </div>
                                        </div>
                                    </CardHeader>
                                    <CardContent class="space-y-5">
                                        <div class="space-y-3">
                                            <div class="flex items-center justify-between gap-3">
                                                <p class="text-sm font-medium text-foreground">班级词书</p>
                                                <Badge variant="outline">{classroomDictionaries()?.length || 0} 本</Badge>
                                            </div>
                                            <div class="grid gap-3 md:grid-cols-[1fr_auto]">
                                                <select
                                                    class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                    value={selectedDictionaryId()}
                                                    onChange={(event) => setSelectedDictionaryId(event.currentTarget.value)}
                                                >
                                                    <option value="">选择词书加入班级</option>
                                                    <For each={optionsData()?.dictionaries || []}>
                                                        {(dictionaryOption) => (
                                                            <option value={dictionaryOption.id}>{dictionaryOption.name}</option>
                                                        )}
                                                    </For>
                                                </select>
                                                <Button
                                                    onClick={() => {
                                                        const dictionaryId = selectedDictionaryId();
                                                        if (!dictionaryId) {
                                                            return;
                                                        }
                                                        void mutateAndRefresh(
                                                            () =>
                                                                api.assignDictionariesToClassroom(
                                                                    classroom().id,
                                                                    [Number(dictionaryId)],
                                                                ),
                                                            `已将词书加入 ${classroom().name}。`,
                                                            { refreshDictionaries: true },
                                                        ).then(() => setSelectedDictionaryId(""));
                                                    }}
                                                >
                                                    添加词书
                                                </Button>
                                            </div>
                                            <Show
                                                when={(classroomDictionaries() || []).length > 0}
                                                fallback={<p class="text-sm text-muted-foreground">该班级还没有关联词书。</p>}
                                            >
                                                <div class="flex flex-wrap gap-2">
                                                    <For each={classroomDictionaries() || []}>
                                                        {(dictionaryItem) => (
                                                            <div class="flex items-center gap-2 rounded-full border border-border/70 bg-background px-3 py-1.5 text-sm">
                                                                <span>{dictionaryItem.name}</span>
                                                                <button
                                                                    class="text-muted-foreground transition hover:text-destructive"
                                                                    onClick={() =>
                                                                        void mutateAndRefresh(
                                                                            () =>
                                                                                api.removeDictionaryFromClassroom(
                                                                                    classroom().id,
                                                                                    dictionaryItem.id,
                                                                                ),
                                                                            `已将 ${dictionaryItem.name} 从 ${classroom().name} 移除。`,
                                                                            { refreshDictionaries: true },
                                                                        )
                                                                    }
                                                                >
                                                                    ×
                                                                </button>
                                                            </div>
                                                        )}
                                                    </For>
                                                </div>
                                            </Show>
                                        </div>

                                        <div class="h-px bg-border/60" />

                                        <div class="grid gap-3 md:grid-cols-[1fr_auto]">
                                            <select
                                                class="h-11 rounded-lg border border-input bg-background/70 px-3 text-sm"
                                            value={selectedStudentId()}
                                            onChange={(event) => setSelectedStudentId(event.currentTarget.value)}
                                        >
                                            <option value="">选择学生加入班级</option>
                                            <For each={optionsData()?.students || []}>
                                                {(student) => <option value={student.id}>{student.displayName}</option>}
                                            </For>
                                        </select>
                                        <Button
                                            onClick={() => {
                                                const studentId = selectedStudentId();
                                                if (!studentId) {
                                                    return;
                                                }
                                                void mutateAndRefresh(
                                                    () => api.addStudentToClassroom(classroom().id, Number(studentId)),
                                                    `已将学生加入 ${classroom().name}。`,
                                                    { refreshMembers: true },
                                                ).then(() => setSelectedStudentId(""));
                                            }}
                                        >
                                            添加学生
                                        </Button>
                                    </div>

                                    <div class="space-y-3">
                                        <div class="flex items-center justify-between gap-3">
                                            <p class="text-sm font-medium text-foreground">班级学生</p>
                                            <Badge variant="outline">{classroomMembers()?.length || 0} 人</Badge>
                                        </div>
                                        <Show
                                            when={(classroomMembers() || []).length > 0}
                                            fallback={<p class="text-sm text-muted-foreground">该班级还没有学生。</p>}
                                        >
                                            <div class="flex flex-wrap gap-2">
                                                <For each={classroomMembers() || []}>
                                                    {(student) => (
                                                        <div class="flex items-center gap-2 rounded-full border border-border/70 bg-background px-3 py-1.5 text-sm">
                                                            <span>{student.displayName}</span>
                                                            <button
                                                                class="text-muted-foreground transition hover:text-destructive"
                                                                onClick={() =>
                                                                    void mutateAndRefresh(
                                                                        () => api.removeStudentFromClassroom(classroom().id, student.id),
                                                                        `已将 ${student.displayName} 移出 ${classroom().name}。`,
                                                                        { refreshMembers: true },
                                                                    )
                                                                }
                                                            >
                                                                ×
                                                            </button>
                                                        </div>
                                                    )}
                                                </For>
                                            </div>
                                        </Show>
                                    </div>
                                </CardContent>
                            </Card>
                        )}
                    </Show>
                </>
            </Show>

            <Show when={isCreateDialogOpen()}>
                <div
                    class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4 backdrop-blur-sm"
                    onClick={closeCreateDialog}
                >
                    <div
                        aria-labelledby="create-classroom-dialog-title"
                        aria-modal="true"
                        class="w-full max-w-2xl rounded-[28px] border border-border/70 bg-background p-6 shadow-2xl"
                        role="dialog"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="font-display text-2xl font-semibold tracking-tight" id="create-classroom-dialog-title">
                                    创建班级
                                </h2>
                                <p class="mt-2 text-sm leading-6 text-muted-foreground">
                                    管理员可指定老师，老师创建的班级默认归自己管理。
                                </p>
                            </div>
                            <Button aria-label="关闭" size="sm" variant="ghost" onClick={closeCreateDialog}>
                                <X class="h-4 w-4" />
                            </Button>
                        </div>

                        <form class="mt-6 grid gap-4" onSubmit={handleCreate}>
                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label>班级名称</Label>
                                    <Input
                                        required
                                        value={form.name}
                                        onInput={(event) => setForm("name", event.currentTarget.value)}
                                    />
                                </div>
                                <Show when={isAdmin()}>
                                    <div class="space-y-2">
                                        <Label>负责老师</Label>
                                        <Show
                                            when={optionsData()}
                                            fallback={<p class="text-sm text-muted-foreground">正在加载老师数据...</p>}
                                        >
                                            <Show
                                                when={teacherOptions().length > 0}
                                                fallback={
                                                    <EmptyState
                                                        title="暂无可用老师"
                                                        description="管理员创建班级前需要先创建老师账号。"
                                                        actions={
                                                            <a
                                                                class="inline-flex h-10 items-center justify-center rounded-lg border border-border bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all duration-200 hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                                                                href="/users"
                                                            >
                                                                去用户管理
                                                            </a>
                                                        }
                                                    />
                                                }
                                            >
                                                <select
                                                    class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                                    value={form.teacherId}
                                                    onChange={(event) => setForm("teacherId", event.currentTarget.value)}
                                                >
                                                    <option value="">选择老师</option>
                                                    <For each={teacherOptions()}>
                                                        {(teacher) => <option value={teacher.id}>{teacher.displayName}</option>}
                                                    </For>
                                                </select>
                                            </Show>
                                        </Show>
                                    </div>
                                </Show>
                            </div>
                            <div class="space-y-2">
                                <Label>描述</Label>
                                <Textarea
                                    value={form.description}
                                    onInput={(event) => setForm("description", event.currentTarget.value)}
                                />
                            </div>
                            <Show when={createError()}>
                                <Alert class="border-destructive/30 bg-destructive/10 text-destructive">{createError()}</Alert>
                            </Show>
                            <div class="flex flex-wrap justify-end gap-3 pt-2">
                                <Button variant="outline" onClick={closeCreateDialog} type="button">
                                    取消
                                </Button>
                                <Button disabled={!canCreateClassroom()} type="submit">创建班级</Button>
                            </div>
                        </form>
                    </div>
                </div>
            </Show>
        </section>
    );
}
