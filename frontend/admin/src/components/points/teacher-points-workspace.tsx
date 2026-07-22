import { ArrowLeft, Search } from "lucide-solid";
import { createResource, createSignal, For, Show } from "solid-js";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRoot, TableRow } from "@/components/ui/table";
import { api } from "@/lib/api";
import { formatDateTime, formatNumber } from "@/lib/format";
import type { TeacherStudentPointResponse } from "@/types/api";
import {
    PAGE_SIZE,
    PageState,
    Pagination,
    StudentPointAdjustmentDialog,
    type AdjustmentTarget,
} from "@/components/points/points-shared";

const transactionLabels: Record<string, string> = {
    EARN: "入账",
    DEDUCT: "扣减",
    FREEZE: "冻结",
    UNFREEZE: "解冻",
    SPEND: "消费",
    REVERSE: "冲正",
};

export function TeacherPointsWorkspace() {
    const [studentPage, setStudentPage] = createSignal(0);
    const [studentName, setStudentName] = createSignal("");
    const [selectedStudent, setSelectedStudent] = createSignal<TeacherStudentPointResponse>();
    const [transactionPage, setTransactionPage] = createSignal(0);
    const [adjustmentTarget, setAdjustmentTarget] = createSignal<AdjustmentTarget>();

    const [students, studentActions] = createResource(
        () => ({ page: studentPage(), size: PAGE_SIZE, name: studentName().trim() || undefined }),
        (params) => api.listTeacherPointStudents(params),
    );
    const [transactions, transactionActions] = createResource(
        () => selectedStudent() ? { studentId: selectedStudent()!.studentId, page: transactionPage(), size: PAGE_SIZE } : null,
        (params) => api.listTeacherStudentPointTransactions(params.studentId, { page: params.page, size: params.size }),
    );

    const openTransactions = (student: TeacherStudentPointResponse) => {
        setTransactionPage(0);
        setSelectedStudent(student);
    };
    const openAdjustment = (student: TeacherStudentPointResponse) => {
        setAdjustmentTarget({ studentId: student.studentId, studentName: student.studentName, actorRole: "TEACHER" });
    };
    const refresh = () => {
        void studentActions.refetch();
        if (selectedStudent()) void transactionActions.refetch();
    };

    return (
        <div class="space-y-5">
            <Show when={!selectedStudent()} fallback={
                <TeacherTransactionsView
                    student={selectedStudent()!}
                    resource={transactions}
                    page={transactionPage()}
                    onBack={() => setSelectedStudent(undefined)}
                    onAdjust={() => openAdjustment(selectedStudent()!)}
                    onPageChange={setTransactionPage}
                />
            }>
                <section class="space-y-4" aria-labelledby="managed-students-title">
                    <div class="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
                        <div><h2 class="text-lg font-semibold" id="managed-students-title">受管学生积分</h2><p class="text-sm text-muted-foreground">仅显示当前教师负责的学生，可查看流水并进行有原因记录的人工调整。</p></div>
                        <label class="relative block w-full md:w-72"><span class="sr-only">学生姓名</span><Search class="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input class="pl-9" placeholder="按学生姓名筛选" value={studentName()} onInput={(event) => { setStudentPage(0); setStudentName(event.currentTarget.value); }} /></label>
                    </div>
                    <PageState loading={students.loading} error={students.error} empty={!students()?.content.length} emptyText="暂无受管学生" />
                    <Show when={!students.loading && !students.error && students()?.content.length}>
                        <Table><TableRoot><TableHead><TableRow><TableHeaderCell>学生</TableHeaderCell><TableHeaderCell>可用积分</TableHeaderCell><TableHeaderCell>今日获得</TableHeaderCell><TableHeaderCell>累计获得</TableHeaderCell><TableHeaderCell>累计支出</TableHeaderCell><TableHeaderCell class="text-right">操作</TableHeaderCell></TableRow></TableHead><TableBody><For each={students()?.content ?? []}>{(student) => <TableRow><TableCell><div class="font-medium">{student.studentName}</div><div class="text-xs text-muted-foreground">学生 #{student.studentId}</div></TableCell><TableCell class="font-semibold">{formatNumber(student.availablePoints)}</TableCell><TableCell class="text-success">+{formatNumber(student.todayEarnedPoints)}</TableCell><TableCell>{formatNumber(student.lifetimeEarnedPoints)}</TableCell><TableCell>{formatNumber(student.lifetimeSpentPoints)}</TableCell><TableCell><div class="flex justify-end gap-2"><Button size="sm" variant="ghost" onClick={() => openTransactions(student)}>查看流水</Button><Button size="sm" variant="outline" onClick={() => openAdjustment(student)}>人工调整</Button></div></TableCell></TableRow>}</For></TableBody></TableRoot></Table>
                        <Pagination page={studentPage()} totalPages={students()?.totalPages ?? 1} totalElements={students()?.totalElements ?? 0} onPageChange={setStudentPage} />
                    </Show>
                </section>
            </Show>
            <StudentPointAdjustmentDialog target={adjustmentTarget()} onClose={() => setAdjustmentTarget(undefined)} onSuccess={refresh} />
        </div>
    );
}

type TransactionsResource = {
    (): Awaited<ReturnType<typeof api.listTeacherStudentPointTransactions>> | undefined;
    loading: boolean;
    error: unknown;
};

function TeacherTransactionsView(props: {
    student: TeacherStudentPointResponse;
    resource: TransactionsResource;
    page: number;
    onBack: () => void;
    onAdjust: () => void;
    onPageChange: (page: number) => void;
}) {
    const rows = () => props.resource()?.content ?? [];
    return (
        <section class="space-y-4" aria-labelledby="teacher-transactions-title">
            <div class="flex flex-col gap-3 border-b border-border pb-4 md:flex-row md:items-center md:justify-between">
                <div class="flex items-center gap-3"><Button aria-label="返回学生列表" size="sm" variant="ghost" onClick={props.onBack}><ArrowLeft class="h-4 w-4" /></Button><div><h2 class="text-lg font-semibold" id="teacher-transactions-title">{props.student.studentName}的积分流水</h2><p class="text-sm text-muted-foreground">当前可用 {formatNumber(props.student.availablePoints)} 分</p></div></div>
                <Button variant="outline" onClick={props.onAdjust}>人工调整</Button>
            </div>
            <PageState loading={props.resource.loading} error={props.resource.error} empty={!rows().length} emptyText="该学生暂无积分流水" />
            <Show when={!props.resource.loading && !props.resource.error && rows().length}>
                <Table><TableRoot><TableHead><TableRow><TableHeaderCell>时间</TableHeaderCell><TableHeaderCell>类型</TableHeaderCell><TableHeaderCell>变动</TableHeaderCell><TableHeaderCell>余额</TableHeaderCell><TableHeaderCell>规则 / 来源</TableHeaderCell><TableHeaderCell>原因</TableHeaderCell></TableRow></TableHead><TableBody><For each={rows()}>{(transaction) => <TableRow><TableCell class="whitespace-nowrap">{formatDateTime(transaction.createdAt)}</TableCell><TableCell><Badge variant="outline">{transactionLabels[transaction.transactionType] ?? transaction.transactionType}</Badge></TableCell><TableCell class={transaction.amount >= 0 ? "font-semibold text-success" : "font-semibold text-destructive"}>{transaction.amount >= 0 ? "+" : ""}{transaction.amount}</TableCell><TableCell>{transaction.balanceBefore} → {transaction.balanceAfter}</TableCell><TableCell>{transaction.ruleCode ?? transaction.sourceType}</TableCell><TableCell>{transaction.reason ?? "-"}</TableCell></TableRow>}</For></TableBody></TableRoot></Table>
                <Pagination page={props.page} totalPages={props.resource()?.totalPages ?? 1} totalElements={props.resource()?.totalElements ?? 0} onPageChange={props.onPageChange} />
            </Show>
        </section>
    );
}
