import { Eye, LoaderCircle, Pencil, Plus, RefreshCw, RotateCcw, XCircle } from "lucide-solid";
import { createMemo, createResource, createSignal, For, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRoot, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { formatDateTime, formatNumber } from "@/lib/format";
import type {
    ClassroomResponse,
    PointEventStatus,
    PointSourceType,
    StudentPointEventResponse,
    StudentPointRuleResponse,
    StudyPlanResponse,
} from "@/types/api";
import {
    PAGE_SIZE,
    PageState,
    Pagination,
    ReasonOperationDialog,
    StudentPointAdjustmentDialog,
    errorMessage,
    type AdjustmentTarget,
    type ReasonOperation,
} from "@/components/points/points-shared";

type AdminTab = "accounts" | "transactions" | "events" | "rules";
const tabs: { value: AdminTab; label: string }[] = [
    { value: "accounts", label: "账户" },
    { value: "transactions", label: "流水" },
    { value: "events", label: "事件" },
    { value: "rules", label: "规则" },
];
const eventStatuses: ("ALL" | PointEventStatus)[] = ["ALL", "PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED"];
const sourceTypes: PointSourceType[] = ["STUDY_TASK", "STUDY_RECORD", "VIDEO_WATCH", "EXAM", "MANUAL_ADJUSTMENT", "ADMIN_CORRECTION", "REDEMPTION"];
type RuleScopeType = "GLOBAL" | "CLASSROOM" | "STUDY_PLAN" | "EXAM";
const ruleScopeTypes: RuleScopeType[] = ["GLOBAL", "CLASSROOM", "STUDY_PLAN", "EXAM"];

const sourceTypeLabels: Record<PointSourceType, string> = {
    STUDY_TASK: "学习任务",
    STUDY_RECORD: "学习记录",
    VIDEO_WATCH: "视频观看",
    EXAM: "考试",
    MANUAL_ADJUSTMENT: "人工调整",
    ADMIN_CORRECTION: "管理员冲正",
    REDEMPTION: "积分兑换",
};

const ruleCodeLabels: Record<string, string> = {
    STUDY_RECORD_CORRECT: "单词答对",
    DAILY_TASK_COMPLETED: "完成每日任务",
    VIDEO_WATCH: "视频观看",
    MANUAL_ADJUSTMENT: "人工调整",
    ADMIN_CORRECTION: "管理员冲正",
    REDEMPTION: "积分兑换",
};

const scopeTypeLabels: Record<RuleScopeType, string> = {
    GLOBAL: "全局",
    CLASSROOM: "班级",
    STUDY_PLAN: "学习计划",
    EXAM: "考试",
};

const statusText: Record<string, string> = {
    ACTIVE: "正常",
    PENDING: "待处理",
    PROCESSING: "处理中",
    SUCCEEDED: "成功",
    FAILED: "失败",
    CANCELLED: "已取消",
    AUTO: "自动",
    MANUAL: "人工",
    EARN: "入账",
    DEDUCT: "扣减",
    FREEZE: "冻结",
    UNFREEZE: "解冻",
    SPEND: "消费",
    REVERSE: "冲正",
};

function statusVariant(status: string) {
    if (status === "SUCCEEDED" || status === "ACTIVE") return "success" as const;
    if (status === "FAILED") return "destructive" as const;
    if (status === "PENDING" || status === "PROCESSING") return "warning" as const;
    return "outline" as const;
}

function studentDisplayName(student: { studentId: number; studentUsername?: string | null; studentName?: string | null }) {
    return student.studentUsername?.trim() || student.studentName?.trim() || `#${student.studentId}`;
}

function studentSecondaryLabel(student: { studentName?: string | null }) {
    return student.studentName?.trim() || "";
}

function sourceTypeLabel(sourceType: PointSourceType) {
    return sourceTypeLabels[sourceType] ?? sourceType;
}

function transactionSourceLabel(ruleCode: string | null | undefined, sourceType: PointSourceType) {
    const normalizedRuleCode = ruleCode?.trim().toUpperCase();
    if (normalizedRuleCode && ruleCodeLabels[normalizedRuleCode]) {
        return ruleCodeLabels[normalizedRuleCode];
    }
    return sourceTypeLabel(sourceType);
}

function normalizeRuleScopeType(scopeType?: string | null): RuleScopeType {
    const normalized = scopeType?.trim().toUpperCase();
    return ruleScopeTypes.includes(normalized as RuleScopeType) ? normalized as RuleScopeType : "GLOBAL";
}

function scopeTypeLabel(scopeType?: string | null) {
    return scopeTypeLabels[normalizeRuleScopeType(scopeType)];
}

function ruleScopeLabel(
    rule: StudentPointRuleResponse,
    classrooms: ClassroomResponse[],
    studyPlans: StudyPlanResponse[],
) {
    const normalized = normalizeRuleScopeType(rule.scopeType);
    if (normalized === "GLOBAL") return scopeTypeLabels.GLOBAL;
    if (normalized === "CLASSROOM") {
        const classroom = classrooms.find((item) => item.id === rule.scopeId);
        return `${scopeTypeLabels.CLASSROOM} · ${classroom?.name ?? (rule.scopeId ? `#${rule.scopeId}` : "未选择")}`;
    }
    if (normalized === "STUDY_PLAN") {
        const studyPlan = studyPlans.find((item) => item.id === rule.scopeId);
        return `${scopeTypeLabels.STUDY_PLAN} · ${studyPlan?.name ?? (rule.scopeId ? `#${rule.scopeId}` : "未选择")}`;
    }
    return `${scopeTypeLabels.EXAM}${rule.scopeId ? ` · #${rule.scopeId}` : ""}`;
}

export function AdminPointsWorkspace() {
    const [activeTab, setActiveTab] = createSignal<AdminTab>("accounts");
    const [accountPage, setAccountPage] = createSignal(0);
    const [transactionPage, setTransactionPage] = createSignal(0);
    const [eventPage, setEventPage] = createSignal(0);
    const [eventStatus, setEventStatus] = createSignal<"ALL" | PointEventStatus>("ALL");
    const [operation, setOperation] = createSignal<ReasonOperation>();
    const [adjustmentTarget, setAdjustmentTarget] = createSignal<AdjustmentTarget>();
    const [selectedEventId, setSelectedEventId] = createSignal<number>();

    const [accounts, accountsActions] = createResource(
        () => activeTab() === "accounts" ? { page: accountPage(), size: PAGE_SIZE } : null,
        (params) => api.listAdminPointAccounts(params),
    );
    const [transactions, transactionActions] = createResource(
        () => activeTab() === "transactions" ? { page: transactionPage(), size: PAGE_SIZE } : null,
        (params) => api.listAdminPointTransactions(params),
    );
    const [events, eventActions] = createResource(
        () => {
            if (activeTab() !== "events") return null;
            const status = eventStatus();
            return { page: eventPage(), size: PAGE_SIZE, status: status === "ALL" ? undefined : status };
        },
        (params) => api.listAdminPointEvents(params),
    );
    const [rules, ruleActions] = createResource(
        () => activeTab() === "rules",
        (enabled) => enabled ? api.listPointRules() : Promise.resolve([]),
    );
    const [classrooms] = createResource(
        () => activeTab() === "rules",
        (enabled) => enabled ? api.listClassrooms() : Promise.resolve([]),
    );
    const [studyPlans] = createResource(
        () => activeTab() === "rules",
        (enabled) => enabled ? api.listStudyPlans() : Promise.resolve([]),
    );
    const [attempts] = createResource(selectedEventId, (eventId) => api.listPointEventAttempts(eventId));

    const refreshCurrent = () => {
        if (activeTab() === "accounts") void accountsActions.refetch();
        if (activeTab() === "transactions") void transactionActions.refetch();
        if (activeTab() === "events") void eventActions.refetch();
        if (activeTab() === "rules") void ruleActions.refetch();
    };

    return (
        <div class="space-y-5">
            <div class="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
                <div class="flex flex-wrap gap-1" aria-label="积分管理视图" role="tablist">
                    <For each={tabs}>{(tab) => (
                        <Button
                            aria-selected={activeTab() === tab.value}
                            class="min-w-20"
                            role="tab"
                            variant={activeTab() === tab.value ? "default" : "ghost"}
                            onClick={() => setActiveTab(tab.value)}
                        >{tab.label}</Button>
                    )}</For>
                </div>
                <Button aria-label="刷新当前列表" size="sm" variant="outline" onClick={refreshCurrent}>
                    <RefreshCw class="h-4 w-4" /> 刷新
                </Button>
            </div>

            <Show when={activeTab() === "accounts"}>
                <AdminAccountsView
                    resource={accounts}
                    page={accountPage()}
                    onAdjust={(studentId, studentName) => setAdjustmentTarget({ studentId, studentName, actorRole: "ADMIN" })}
                    onPageChange={setAccountPage}
                />
            </Show>
            <Show when={activeTab() === "transactions"}>
                <AdminTransactionsView
                    resource={transactions}
                    page={transactionPage()}
                    onPageChange={setTransactionPage}
                    onReverse={(id) => setOperation({
                        kind: "reverse",
                        id,
                        title: `冲正流水 #${id}`,
                        description: "冲正会生成一条相反方向的新流水，不会删除原记录。",
                        confirmLabel: "确认冲正",
                    })}
                />
            </Show>
            <Show when={activeTab() === "events"}>
                <AdminEventsView
                    resource={events}
                    page={eventPage()}
                    status={eventStatus()}
                    selectedEventId={selectedEventId()}
                    attempts={attempts}
                    onPageChange={setEventPage}
                    onStatusChange={(status) => { setEventPage(0); setEventStatus(status); }}
                    onShowAttempts={(id) => setSelectedEventId(selectedEventId() === id ? undefined : id)}
                    onRetry={(event) => setOperation({
                        kind: "retry",
                        id: event.id,
                        title: `手动重试事件 #${event.id}`,
                        description: "系统会保留本次人工尝试和操作原因。请先确认失败原因已经消除。",
                        confirmLabel: "确认重试",
                    })}
                    onCancel={(event) => setOperation({
                        kind: "cancel",
                        id: event.id,
                        title: `取消事件 #${event.id}`,
                        description: "取消后该事件不再自动处理，操作会写入审计记录。",
                        confirmLabel: "确认取消",
                    })}
                />
            </Show>
            <Show when={activeTab() === "rules"}>
                <AdminRulesView
                    classrooms={classrooms() ?? []}
                    resource={rules}
                    studyPlans={studyPlans() ?? []}
                    onChanged={() => void ruleActions.refetch()}
                />
            </Show>

            <ReasonOperationDialog
                operation={operation()}
                onClose={() => setOperation(undefined)}
                onSuccess={refreshCurrent}
            />
            <StudentPointAdjustmentDialog
                target={adjustmentTarget()}
                onClose={() => setAdjustmentTarget(undefined)}
                onSuccess={() => void accountsActions.refetch()}
            />
        </div>
    );
}

type PageResource<T> = {
    (): T | undefined;
    loading: boolean;
    error: unknown;
};

function AdminAccountsView(props: {
    resource: PageResource<Awaited<ReturnType<typeof api.listAdminPointAccounts>>>;
    page: number;
    onPageChange: (page: number) => void;
    onAdjust: (studentId: number, studentName: string) => void;
}) {
    const rows = () => props.resource()?.content ?? [];
    return (
        <section class="space-y-4" aria-labelledby="accounts-title">
            <div><h2 class="text-lg font-semibold" id="accounts-title">学生积分账户</h2><p class="text-sm text-muted-foreground">查看余额、累计收入与支出，并发起有审计记录的人工调整。</p></div>
            <PageState loading={props.resource.loading} error={props.resource.error} empty={!rows().length} emptyText="暂无积分账户" />
            <Show when={!props.resource.loading && !props.resource.error && rows().length}>
                <Table><TableRoot><TableHead><TableRow><TableHeaderCell>学生</TableHeaderCell><TableHeaderCell>可用</TableHeaderCell><TableHeaderCell>冻结</TableHeaderCell><TableHeaderCell>累计获得</TableHeaderCell><TableHeaderCell>累计支出</TableHeaderCell><TableHeaderCell>状态</TableHeaderCell><TableHeaderCell class="text-right">操作</TableHeaderCell></TableRow></TableHead>
                    <TableBody><For each={rows()}>{(account) => <TableRow><TableCell><div class="font-medium">{studentDisplayName(account)}</div><div class="text-xs text-muted-foreground">{studentSecondaryLabel(account) || "学生"} · 账户 #{account.accountId}</div></TableCell><TableCell class="font-semibold">{formatNumber(account.availablePoints)}</TableCell><TableCell>{formatNumber(account.frozenPoints)}</TableCell><TableCell>{formatNumber(account.lifetimeEarnedPoints)}</TableCell><TableCell>{formatNumber(account.lifetimeSpentPoints)}</TableCell><TableCell><Badge variant={statusVariant(account.status)}>{statusText[account.status] ?? account.status}</Badge></TableCell><TableCell class="text-right"><Button size="sm" variant="outline" onClick={() => props.onAdjust(account.studentId, studentDisplayName(account))}>人工调整</Button></TableCell></TableRow>}</For></TableBody>
                </TableRoot></Table>
                <Pagination page={props.page} totalPages={props.resource()?.totalPages ?? 1} totalElements={props.resource()?.totalElements ?? 0} onPageChange={props.onPageChange} />
            </Show>
        </section>
    );
}

function AdminTransactionsView(props: {
    resource: PageResource<Awaited<ReturnType<typeof api.listAdminPointTransactions>>>;
    page: number;
    onPageChange: (page: number) => void;
    onReverse: (id: number) => void;
}) {
    const rows = () => props.resource()?.content ?? [];
    return (
        <section class="space-y-4" aria-labelledby="transactions-title">
            <div><h2 class="text-lg font-semibold" id="transactions-title">积分流水</h2><p class="text-sm text-muted-foreground">流水不可删除；纠错通过冲正生成新的审计记录。</p></div>
            <PageState loading={props.resource.loading} error={props.resource.error} empty={!rows().length} emptyText="暂无积分流水" />
            <Show when={!props.resource.loading && !props.resource.error && rows().length}>
                <Table><TableRoot><TableHead><TableRow><TableHeaderCell>流水</TableHeaderCell><TableHeaderCell>学生</TableHeaderCell><TableHeaderCell>类型</TableHeaderCell><TableHeaderCell>变动</TableHeaderCell><TableHeaderCell>余额</TableHeaderCell><TableHeaderCell>来源 / 原因</TableHeaderCell><TableHeaderCell>时间</TableHeaderCell><TableHeaderCell class="text-right">操作</TableHeaderCell></TableRow></TableHead>
                    <TableBody><For each={rows()}>{(transaction) => <TableRow><TableCell class="font-medium">#{transaction.id}</TableCell><TableCell><div class="font-medium">{studentDisplayName(transaction)}</div><Show when={studentSecondaryLabel(transaction)}><div class="text-xs text-muted-foreground">{studentSecondaryLabel(transaction)}</div></Show></TableCell><TableCell><Badge variant="outline">{statusText[transaction.transactionType] ?? transaction.transactionType}</Badge></TableCell><TableCell class={transaction.amount >= 0 ? "font-semibold text-success" : "font-semibold text-destructive"}>{transaction.amount >= 0 ? "+" : ""}{transaction.amount}</TableCell><TableCell>{transaction.balanceBefore} → {transaction.balanceAfter}</TableCell><TableCell class="max-w-64"><div>{transactionSourceLabel(transaction.ruleCode, transaction.sourceType)}</div><div class="truncate text-xs text-muted-foreground">{transaction.reason ?? transaction.sourceKey}</div></TableCell><TableCell class="whitespace-nowrap">{formatDateTime(transaction.createdAt)}</TableCell><TableCell class="text-right"><Show when={transaction.transactionType !== "REVERSE" && !transaction.reversedTransactionId}><Button size="sm" variant="outline" onClick={() => props.onReverse(transaction.id)}><RotateCcw class="h-4 w-4" /> 冲正</Button></Show></TableCell></TableRow>}</For></TableBody>
                </TableRoot></Table>
                <Pagination page={props.page} totalPages={props.resource()?.totalPages ?? 1} totalElements={props.resource()?.totalElements ?? 0} onPageChange={props.onPageChange} />
            </Show>
        </section>
    );
}

function AdminEventsView(props: {
    resource: PageResource<Awaited<ReturnType<typeof api.listAdminPointEvents>>>;
    page: number;
    status: "ALL" | PointEventStatus;
    selectedEventId?: number;
    attempts: PageResource<Awaited<ReturnType<typeof api.listPointEventAttempts>>>;
    onPageChange: (page: number) => void;
    onStatusChange: (status: "ALL" | PointEventStatus) => void;
    onShowAttempts: (id: number) => void;
    onRetry: (event: StudentPointEventResponse) => void;
    onCancel: (event: StudentPointEventResponse) => void;
}) {
    const rows = () => props.resource()?.content ?? [];
    return (
        <section class="space-y-4" aria-labelledby="events-title">
            <div class="flex flex-col gap-3 md:flex-row md:items-end md:justify-between"><div><h2 class="text-lg font-semibold" id="events-title">积分事件</h2><p class="text-sm text-muted-foreground">筛选处理状态，查看每次自动/人工尝试并处理异常事件。</p></div><label class="flex items-center gap-2 text-sm"><span class="text-muted-foreground">状态</span><select aria-label="事件状态" class="h-10 rounded-lg border border-input bg-background px-3" value={props.status} onChange={(event) => props.onStatusChange(event.currentTarget.value as "ALL" | PointEventStatus)}><For each={eventStatuses}>{(status) => <option value={status}>{status === "ALL" ? "全部" : statusText[status]}</option>}</For></select></label></div>
            <PageState loading={props.resource.loading} error={props.resource.error} empty={!rows().length} emptyText="当前筛选条件下没有事件" />
            <Show when={!props.resource.loading && !props.resource.error && rows().length}>
                <Table><TableRoot><TableHead><TableRow><TableHeaderCell>事件</TableHeaderCell><TableHeaderCell>学生</TableHeaderCell><TableHeaderCell>规则</TableHeaderCell><TableHeaderCell>积分</TableHeaderCell><TableHeaderCell>状态</TableHeaderCell><TableHeaderCell>重试</TableHeaderCell><TableHeaderCell>错误</TableHeaderCell><TableHeaderCell class="text-right">操作</TableHeaderCell></TableRow></TableHead>
                    <TableBody><For each={rows()}>{(event) => <TableRow><TableCell><div class="font-medium">#{event.id}</div><div class="text-xs text-muted-foreground">{event.sourceKey}</div></TableCell><TableCell><div class="font-medium">{studentDisplayName(event)}</div><Show when={studentSecondaryLabel(event)}><div class="text-xs text-muted-foreground">{studentSecondaryLabel(event)}</div></Show></TableCell><TableCell><div>{event.ruleName}</div><div class="text-xs text-muted-foreground">{event.ruleCode}</div></TableCell><TableCell class="font-semibold">{event.points >= 0 ? "+" : ""}{event.points}</TableCell><TableCell><Badge variant={statusVariant(event.status)}>{statusText[event.status]}</Badge></TableCell><TableCell>{event.autoAttemptCount}/3</TableCell><TableCell class="max-w-52"><span class="block max-w-52 truncate text-xs text-destructive" title={event.lastError ?? undefined}>{event.lastError ?? "-"}</span></TableCell><TableCell><div class="flex justify-end gap-2"><Button aria-label={`查看事件 ${event.id} 尝试记录`} size="sm" variant="ghost" onClick={() => props.onShowAttempts(event.id)}><Eye class="h-4 w-4" /></Button><Show when={event.status === "FAILED"}><Button size="sm" variant="outline" onClick={() => props.onRetry(event)}><RefreshCw class="h-4 w-4" /> 手动重试</Button></Show><Show when={event.status === "PENDING" || event.status === "FAILED"}><Button size="sm" variant="outline" onClick={() => props.onCancel(event)}><XCircle class="h-4 w-4" /> 取消</Button></Show></div></TableCell></TableRow>}</For></TableBody>
                </TableRoot></Table>
                <Pagination page={props.page} totalPages={props.resource()?.totalPages ?? 1} totalElements={props.resource()?.totalElements ?? 0} onPageChange={props.onPageChange} />
            </Show>
            <Show when={props.selectedEventId}>
                <section class="space-y-3 border-t border-border pt-4" aria-labelledby="attempts-title"><h3 class="font-semibold" id="attempts-title">事件 #{props.selectedEventId} 尝试记录</h3><PageState loading={props.attempts.loading} error={props.attempts.error} empty={!props.attempts()?.length} emptyText="暂无尝试记录" /><Show when={!props.attempts.loading && props.attempts()?.length}><Table><TableRoot><TableHead><TableRow><TableHeaderCell>序号</TableHeaderCell><TableHeaderCell>触发</TableHeaderCell><TableHeaderCell>结果</TableHeaderCell><TableHeaderCell>操作人</TableHeaderCell><TableHeaderCell>原因 / 错误</TableHeaderCell><TableHeaderCell>开始</TableHeaderCell><TableHeaderCell>结束</TableHeaderCell></TableRow></TableHead><TableBody><For each={props.attempts() ?? []}>{(attempt) => <TableRow><TableCell>#{attempt.attemptNo}</TableCell><TableCell>{statusText[attempt.triggerType]}</TableCell><TableCell><Badge variant={statusVariant(attempt.status)}>{statusText[attempt.status]}</Badge></TableCell><TableCell>{attempt.operatorRole ?? "系统"}{attempt.operatorId ? ` #${attempt.operatorId}` : ""}</TableCell><TableCell class="max-w-72"><div>{attempt.reason ?? "-"}</div><div class="text-xs text-destructive">{attempt.errorMessage}</div></TableCell><TableCell>{formatDateTime(attempt.startedAt)}</TableCell><TableCell>{formatDateTime(attempt.finishedAt)}</TableCell></TableRow>}</For></TableBody></TableRoot></Table></Show></section>
            </Show>
        </section>
    );
}

function AdminRulesView(props: {
    classrooms: ClassroomResponse[];
    resource: PageResource<StudentPointRuleResponse[]>;
    studyPlans: StudyPlanResponse[];
    onChanged: () => void;
}) {
    const [dialogOpen, setDialogOpen] = createSignal(false);
    const [editingRule, setEditingRule] = createSignal<StudentPointRuleResponse>();
    const [name, setName] = createSignal("");
    const [description, setDescription] = createSignal("");
    const [sourceType, setSourceType] = createSignal<PointSourceType>("MANUAL_ADJUSTMENT");
    const [basePoints, setBasePoints] = createSignal("0");
    const [scopeType, setScopeType] = createSignal<RuleScopeType>("GLOBAL");
    const [scopeId, setScopeId] = createSignal("");
    const [enabled, setEnabled] = createSignal(true);
    const [reason, setReason] = createSignal("");
    const [pending, setPending] = createSignal(false);
    const [error, setError] = createSignal("");
    const rows = () => props.resource() ?? [];
    const requiresScopeId = () => scopeType() === "CLASSROOM" || scopeType() === "STUDY_PLAN";
    const scopeIdOptions = () => {
        if (scopeType() === "CLASSROOM") {
            return props.classrooms.map((classroom) => ({ value: classroom.id, label: classroom.name }));
        }
        if (scopeType() === "STUDY_PLAN") {
            return props.studyPlans.map((studyPlan) => ({ value: studyPlan.id, label: studyPlan.name }));
        }
        return [];
    };
    const valid = createMemo(() => name().trim().length > 0
        && Number.isInteger(Number(basePoints()))
        && Number(basePoints()) !== 0
        && (!requiresScopeId() || Boolean(scopeId()))
        && reason().trim().length <= 500);

    const openRule = (rule?: StudentPointRuleResponse) => {
        setEditingRule(rule);
        setName(rule?.name ?? "");
        setDescription(rule?.description ?? "");
        setSourceType(rule?.sourceType ?? "MANUAL_ADJUSTMENT");
        setBasePoints(String(rule?.basePoints ?? 0));
        setScopeType(normalizeRuleScopeType(rule?.scopeType));
        setScopeId(rule?.scopeId ? String(rule.scopeId) : "");
        setEnabled(rule?.enabled ?? true);
        setReason("");
        setError("");
        setDialogOpen(true);
    };
    const changeScopeType = (nextScopeType: RuleScopeType) => {
        setScopeType(nextScopeType);
        if (nextScopeType === "GLOBAL" || nextScopeType === "EXAM") {
            setScopeId("");
        }
    };
    const submit = async () => {
        if (!valid() || pending()) return;
        setPending(true); setError("");
        const common = {
            name: name().trim(),
            description: description().trim() || undefined,
            sourceType: sourceType(),
            basePoints: Number(basePoints()),
            scopeType: scopeType(),
            scopeId: scopeId() ? Number(scopeId()) : undefined,
            enabled: enabled(),
            reason: reason().trim(),
        };
        try {
            const rule = editingRule();
            if (rule) await api.updatePointRule(rule.id, common);
            else await api.createPointRule({ code: sourceType(), ...common });
            setDialogOpen(false);
            props.onChanged();
        } catch (requestError) {
            setError(errorMessage(requestError));
        } finally {
            setPending(false);
        }
    };

    return (
        <section class="space-y-4" aria-labelledby="rules-title">
            <div class="flex flex-col gap-3 md:flex-row md:items-end md:justify-between"><div><h2 class="text-lg font-semibold" id="rules-title">积分规则</h2><p class="text-sm text-muted-foreground">规则编码创建后不可修改；变更会校验是否存在未消费完成的事件。</p></div><Button onClick={() => openRule()}><Plus class="h-4 w-4" /> 新增规则</Button></div>
            <PageState loading={props.resource.loading} error={props.resource.error} empty={!rows().length} emptyText="暂无积分规则" />
            <Show when={!props.resource.loading && !props.resource.error && rows().length}><Table><TableRoot><TableHead><TableRow><TableHeaderCell>编码 / 名称</TableHeaderCell><TableHeaderCell>来源</TableHeaderCell><TableHeaderCell>分值</TableHeaderCell><TableHeaderCell>范围</TableHeaderCell><TableHeaderCell>状态</TableHeaderCell><TableHeaderCell>更新时间</TableHeaderCell><TableHeaderCell class="text-right">操作</TableHeaderCell></TableRow></TableHead><TableBody><For each={rows()}>{(rule) => <TableRow><TableCell><div class="font-medium">{rule.name}</div><div class="font-mono text-xs text-muted-foreground">{rule.code}</div></TableCell><TableCell>{sourceTypeLabel(rule.sourceType)}</TableCell><TableCell class="font-semibold">{rule.basePoints}</TableCell><TableCell>{ruleScopeLabel(rule, props.classrooms, props.studyPlans)}</TableCell><TableCell><Badge variant={rule.enabled ? "success" : "outline"}>{rule.enabled ? "启用" : "停用"}</Badge></TableCell><TableCell>{formatDateTime(rule.updatedAt)}</TableCell><TableCell class="text-right"><Button size="sm" variant="outline" onClick={() => openRule(rule)}><Pencil class="h-4 w-4" /> 编辑</Button></TableCell></TableRow>}</For></TableBody></TableRoot></Table></Show>
            <Show when={dialogOpen()}><div class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" role="presentation"><section aria-labelledby="rule-dialog-title" aria-modal="true" class="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-2xl" role="dialog"><div><h2 class="text-lg font-semibold" id="rule-dialog-title">{editingRule() ? "编辑积分规则" : "新增积分规则"}</h2><p class="mt-1 text-sm text-muted-foreground">保存前请确认规则名称、基础分值和适用范围。规则编码由来源类型自动生成。</p></div><div class="mt-5 grid gap-4 md:grid-cols-2"><div class="space-y-2"><Label for="rule-name">规则名称</Label><Input id="rule-name" maxlength={100} required value={name()} onInput={(event) => setName(event.currentTarget.value)} /></div><div class="space-y-2"><Label for="rule-source">来源类型</Label><select id="rule-source" class="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm" value={sourceType()} onChange={(event) => setSourceType(event.currentTarget.value as PointSourceType)}><For each={sourceTypes}>{(source) => <option value={source}>{sourceTypeLabel(source)}</option>}</For></select></div><div class="space-y-2"><Label for="rule-points">基础分值</Label><Input id="rule-points" required type="number" value={basePoints()} onInput={(event) => setBasePoints(event.currentTarget.value)} /></div><div class="space-y-2"><Label for="rule-scope-type">范围类型</Label><select id="rule-scope-type" class="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm" value={scopeType()} onChange={(event) => changeScopeType(event.currentTarget.value as RuleScopeType)}><For each={ruleScopeTypes}>{(scope) => <option value={scope}>{scopeTypeLabel(scope)}</option>}</For></select></div><div class="space-y-2 md:col-span-2"><Label for="rule-scope-id">范围ID</Label><select id="rule-scope-id" class="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm disabled:cursor-not-allowed disabled:opacity-60" disabled={!requiresScopeId()} value={scopeId()} onChange={(event) => setScopeId(event.currentTarget.value)}><Show when={requiresScopeId()} fallback={<option value="">全局或考试范围不需要选择范围ID</option>}><option value="">请选择{scopeTypeLabel(scopeType())}</option><For each={scopeIdOptions()}>{(option) => <option value={option.value}>{option.label}</option>}</For></Show></select><p class="text-xs text-muted-foreground">全局不需要范围ID；班级对应班级ID；学习计划对应学习计划ID；考试当前按考试来源整体生效。</p></div><div class="space-y-2 md:col-span-2"><Label for="rule-description">规则说明</Label><Textarea id="rule-description" maxlength={500} value={description()} onInput={(event) => setDescription(event.currentTarget.value)} /></div><div class="space-y-2 md:col-span-2"><Label for="rule-reason">变更原因</Label><Textarea id="rule-reason" maxlength={500} placeholder="选填，最多 500 字" value={reason()} onInput={(event) => setReason(event.currentTarget.value)} /><p class="text-right text-xs text-muted-foreground">{reason().length}/500</p></div><label class="flex items-center gap-3 text-sm md:col-span-2"><input checked={enabled()} type="checkbox" onChange={(event) => setEnabled(event.currentTarget.checked)} /> 启用规则</label></div><Show when={error()}><Alert class="mt-4 border-destructive/30 text-destructive">{error()}</Alert></Show><div class="mt-5 flex justify-end gap-3"><Button disabled={pending()} variant="outline" onClick={() => setDialogOpen(false)}>取消</Button><Button disabled={!valid() || pending()} onClick={() => void submit()}><Show when={pending()}><LoaderCircle class="h-4 w-4 animate-spin" /></Show>保存规则</Button></div></section></div></Show>
        </section>
    );
}
