import { createMemo, createResource, For, Match, Show, Switch } from "solid-js";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatCard } from "@/components/dashboard/stat-card";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { api } from "@/lib/api";
import { formatDateTime, formatNumber } from "@/lib/format";
import { useAuth } from "@/features/auth/auth-context";

interface AdminOverviewData {
    users: number;
    teachers: number;
    students: number;
    classrooms: number;
    studyPlans: number;
    dictionaries: number;
    latestBatchStatus?: string | null;
    latestBatchId?: string | null;
    recentClassrooms: Awaited<ReturnType<typeof api.listClassrooms>>;
    recentPlans: Awaited<ReturnType<typeof api.listStudyPlans>>;
    recentDictionaries: Awaited<ReturnType<typeof api.listDictionaries>>;
}

interface TeacherOverviewData {
    students: number;
    classrooms: number;
    studyPlans: number;
    dictionaries: number;
    recentStudents: Awaited<ReturnType<typeof api.listMyStudents>>;
    recentClassrooms: Awaited<ReturnType<typeof api.listClassrooms>>;
    recentPlans: Awaited<ReturnType<typeof api.listStudyPlans>>;
}

type OverviewData = AdminOverviewData | TeacherOverviewData;

export function OverviewPage() {
    const auth = useAuth();

    const [overview, { refetch }] = createResource(
        () => auth.user(),
        async (user): Promise<OverviewData | null> => {
            if (!user) {
                return null;
            }

            if (user.role === "ADMIN") {
                const [users, classrooms, studyPlans, dictionaries, latestBatch] = await Promise.all([
                    api.listUsers(),
                    api.listClassrooms(),
                    api.listStudyPlans(),
                    api.listDictionaries(),
                    api.getLatestImportBatch().catch(() => null),
                ]);

                return {
                    users: users.length,
                    teachers: users.filter((item) => item.role === "TEACHER").length,
                    students: users.filter((item) => item.role === "STUDENT").length,
                    classrooms: classrooms.length,
                    studyPlans: studyPlans.length,
                    dictionaries: dictionaries.length,
                    latestBatchStatus: latestBatch?.status ?? null,
                    latestBatchId: latestBatch?.jobId ?? null,
                    recentClassrooms: classrooms.slice(0, 4),
                    recentPlans: studyPlans.slice(0, 4),
                    recentDictionaries: dictionaries.slice(0, 4),
                };
            }

            const [students, classrooms, studyPlans, dictionaries] = await Promise.all([
                api.listMyStudents(),
                api.listClassrooms(),
                api.listStudyPlans(),
                api.listDictionaries(),
            ]);

            return {
                students: students.length,
                classrooms: classrooms.length,
                studyPlans: studyPlans.length,
                dictionaries: dictionaries.length,
                recentStudents: students.slice(0, 5),
                recentClassrooms: classrooms.slice(0, 4),
                recentPlans: studyPlans.slice(0, 4),
            };
        },
    );

    const isAdmin = createMemo(() => auth.user()?.role === "ADMIN");

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Overview"
                title={isAdmin() ? "管理员总览" : "老师工作台"}
                description={
                    isAdmin()
                        ? "用一个面板追踪用户规模、教学结构、词书资源和最近一次导入批次。"
                        : "聚焦你负责的学生、班级和学习计划，减少在多个入口之间切换。"
                }
                actions={
                    <Button variant="outline" onClick={() => void refetch()}>
                        刷新数据
                    </Button>
                }
            />

            <Show
                when={overview()}
                fallback={
                    <Card>
                        <CardContent class="p-6 text-sm text-muted-foreground">正在加载总览...</CardContent>
                    </Card>
                }
            >
                {(data) => (
                    <>
                        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                            <Switch>
                                <Match when={isAdmin()}>
                                    <StatCard label="平台用户" value={formatNumber((data() as AdminOverviewData).users)} hint="管理员、老师、学生总数" />
                                    <StatCard label="老师" value={formatNumber((data() as AdminOverviewData).teachers)} hint="可分配班级与计划" />
                                    <StatCard label="学生" value={formatNumber((data() as AdminOverviewData).students)} hint="可接受词书和计划" />
                                    <StatCard label="导入批次" value={(data() as AdminOverviewData).latestBatchStatus || "暂无"} hint={(data() as AdminOverviewData).latestBatchId || "尚未触发导入"} />
                                </Match>
                                <Match when={!isAdmin()}>
                                    <StatCard label="我的学生" value={formatNumber((data() as TeacherOverviewData).students)} hint="当前已关联学生人数" />
                                    <StatCard label="我的班级" value={formatNumber((data() as TeacherOverviewData).classrooms)} hint="可直接管理的班级" />
                                    <StatCard label="学习计划" value={formatNumber((data() as TeacherOverviewData).studyPlans)} hint="可查看和发布的计划" />
                                    <StatCard label="可见词书" value={formatNumber((data() as TeacherOverviewData).dictionaries)} hint="系统词书与个人词书" />
                                </Match>
                            </Switch>
                        </div>

                        <div class="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
                            <Card>
                                <CardHeader>
                                    <CardTitle>最近在运转的结构</CardTitle>
                                    <CardDescription>优先展示最近可操作的班级和学习计划。</CardDescription>
                                </CardHeader>
                                <CardContent class="grid gap-6 lg:grid-cols-2">
                                    <div class="space-y-3">
                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Classrooms</p>
                                        <Show when={data().recentClassrooms.length > 0} fallback={<EmptyState title="暂无班级" description="先创建班级，再安排学生和学习计划。" />}>
                                            <For each={data().recentClassrooms}>
                                                {(classroom) => (
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <div class="flex items-center justify-between gap-4">
                                                            <div>
                                                                <p class="font-medium text-foreground">{classroom.name}</p>
                                                                <p class="mt-1 text-sm text-muted-foreground">{classroom.teacherName}</p>
                                                            </div>
                                                            <Badge variant="secondary">{classroom.studentCount} 人</Badge>
                                                        </div>
                                                    </div>
                                                )}
                                            </For>
                                        </Show>
                                    </div>

                                    <div class="space-y-3">
                                        <p class="text-xs uppercase tracking-[0.18em] text-muted-foreground">Study Plans</p>
                                        <Show when={data().recentPlans.length > 0} fallback={<EmptyState title="暂无学习计划" description="计划创建后会在这里优先显示。" />}>
                                            <For each={data().recentPlans}>
                                                {(plan) => (
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <div class="flex items-center justify-between gap-4">
                                                            <div>
                                                                <p class="font-medium text-foreground">{plan.name}</p>
                                                                <p class="mt-1 text-sm text-muted-foreground">{plan.dictionaryName}</p>
                                                            </div>
                                                            <Badge variant={plan.status === "PUBLISHED" ? "success" : "outline"}>
                                                                {plan.status}
                                                            </Badge>
                                                        </div>
                                                    </div>
                                                )}
                                            </For>
                                        </Show>
                                    </div>
                                </CardContent>
                            </Card>

                            <Card>
                                <CardHeader>
                                    <CardTitle>{isAdmin() ? "词书与导入信号" : "我的最近学生"}</CardTitle>
                                    <CardDescription>
                                        {isAdmin()
                                            ? "管理员优先关注新词书和导入状态。"
                                            : "老师优先关注当前已经关联的学生。"}
                                    </CardDescription>
                                </CardHeader>
                                <CardContent class="space-y-4">
                                    <Switch>
                                        <Match when={isAdmin()}>
                                            <For each={(data() as AdminOverviewData).recentDictionaries}>
                                                {(dictionary) => (
                                                    <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                        <div class="flex items-center justify-between gap-4">
                                                            <div>
                                                                <p class="font-medium text-foreground">{dictionary.name}</p>
                                                                <p class="mt-1 text-sm text-muted-foreground">{dictionary.category || "未分类"}</p>
                                                            </div>
                                                            <Badge variant="outline">{dictionary.wordCount || 0} 词</Badge>
                                                        </div>
                                                    </div>
                                                )}
                                            </For>
                                        </Match>
                                        <Match when={!isAdmin()}>
                                            <Show
                                                when={(data() as TeacherOverviewData).recentStudents.length > 0}
                                                fallback={<EmptyState title="还没有关联学生" description="管理员可以先为老师分配学生，班级维度也可以继续补齐。" />}
                                            >
                                                <For each={(data() as TeacherOverviewData).recentStudents}>
                                                    {(student) => (
                                                        <div class="rounded-2xl border border-border/70 bg-background/60 p-4">
                                                            <div class="flex items-center justify-between gap-4">
                                                                <div>
                                                                    <p class="font-medium text-foreground">{student.displayName}</p>
                                                                    <p class="mt-1 text-sm text-muted-foreground">{student.username}</p>
                                                                </div>
                                                                <Badge variant="outline">{formatDateTime(student.lastLoginAt)}</Badge>
                                                            </div>
                                                        </div>
                                                    )}
                                                </For>
                                            </Show>
                                        </Match>
                                    </Switch>
                                </CardContent>
                            </Card>
                        </div>
                    </>
                )}
            </Show>
        </section>
    );
}
