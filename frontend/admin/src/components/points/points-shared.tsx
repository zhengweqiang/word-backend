import { AlertTriangle, ChevronLeft, ChevronRight, LoaderCircle, X } from "lucide-solid";
import { createEffect, createSignal, Show } from "solid-js";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { api } from "@/lib/api";
import { createPointAdjustmentRequestKey } from "@/lib/point-request-key";
import type { UserRole } from "@/types/api";

export const PAGE_SIZE = 20;

export function errorMessage(error: unknown) {
    return error instanceof Error ? error.message : "请求失败，请稍后重试";
}

export function PageState(props: { loading: boolean; error?: unknown; empty: boolean; emptyText: string }) {
    return (
        <>
            <Show when={props.loading}>
                <div class="flex min-h-40 items-center justify-center gap-2 text-sm text-muted-foreground">
                    <LoaderCircle class="h-4 w-4 animate-spin" /> 正在加载
                </div>
            </Show>
            <Show when={!props.loading && props.error}>
                <Alert class="border-destructive/30 bg-destructive/5 text-destructive">
                    {errorMessage(props.error)}
                </Alert>
            </Show>
            <Show when={!props.loading && !props.error && props.empty}>
                <div class="flex min-h-40 items-center justify-center border-y border-dashed border-border text-sm text-muted-foreground">
                    {props.emptyText}
                </div>
            </Show>
        </>
    );
}

export function Pagination(props: {
    page: number;
    totalPages: number;
    totalElements: number;
    onPageChange: (page: number) => void;
}) {
    return (
        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4 text-sm text-muted-foreground">
            <span>共 {props.totalElements} 条，第 {props.page + 1} / {Math.max(1, props.totalPages)} 页</span>
            <div class="flex gap-2">
                <Button
                    aria-label="上一页"
                    disabled={props.page <= 0}
                    size="sm"
                    variant="outline"
                    onClick={() => props.onPageChange(props.page - 1)}
                >
                    <ChevronLeft class="h-4 w-4" />
                </Button>
                <Button
                    aria-label="下一页"
                    disabled={props.page + 1 >= props.totalPages}
                    size="sm"
                    variant="outline"
                    onClick={() => props.onPageChange(props.page + 1)}
                >
                    <ChevronRight class="h-4 w-4" />
                </Button>
            </div>
        </div>
    );
}

export interface ReasonOperation {
    kind: "retry" | "cancel" | "reverse";
    id: number;
    title: string;
    description: string;
    confirmLabel: string;
}

export function ReasonOperationDialog(props: {
    operation?: ReasonOperation;
    onClose: () => void;
    onSuccess: () => void;
}) {
    const [reason, setReason] = createSignal("");
    const [pending, setPending] = createSignal(false);
    const [error, setError] = createSignal("");

    createEffect(() => {
        if (props.operation) {
            setReason("");
            setError("");
        }
    });

    const submit = async () => {
        const operation = props.operation;
        const nextReason = reason().trim();
        if (!operation || !nextReason || pending()) return;
        setPending(true);
        setError("");
        try {
            if (operation.kind === "retry") await api.retryPointEvent(operation.id, { reason: nextReason });
            if (operation.kind === "cancel") await api.cancelPointEvent(operation.id, { reason: nextReason });
            if (operation.kind === "reverse") await api.reversePointTransaction(operation.id, { reason: nextReason });
            props.onSuccess();
            props.onClose();
        } catch (requestError) {
            setError(errorMessage(requestError));
        } finally {
            setPending(false);
        }
    };

    return (
        <Show when={props.operation}>
            {(operation) => (
                <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" role="presentation">
                    <section
                        aria-labelledby="point-operation-title"
                        aria-modal="true"
                        class="w-full max-w-lg rounded-lg border border-border bg-background p-6 shadow-2xl"
                        role="dialog"
                    >
                        <div class="flex items-start justify-between gap-4">
                            <div class="flex gap-3">
                                <AlertTriangle class="mt-0.5 h-5 w-5 text-warning" />
                                <div>
                                    <h2 class="text-lg font-semibold" id="point-operation-title">{operation().title}</h2>
                                    <p class="mt-1 text-sm text-muted-foreground">{operation().description}</p>
                                </div>
                            </div>
                            <Button aria-label="关闭" size="sm" variant="ghost" onClick={props.onClose}>
                                <X class="h-4 w-4" />
                            </Button>
                        </div>
                        <div class="mt-5 space-y-2">
                            <Label for="point-operation-reason">操作原因</Label>
                            <Textarea
                                id="point-operation-reason"
                                maxlength={500}
                                placeholder="请填写可追溯的业务原因"
                                value={reason()}
                                onInput={(event) => setReason(event.currentTarget.value)}
                            />
                            <p class="text-right text-xs text-muted-foreground">{reason().length}/500</p>
                        </div>
                        <Show when={error()}><Alert class="mt-4 border-destructive/30 text-destructive">{error()}</Alert></Show>
                        <div class="mt-5 flex justify-end gap-3">
                            <Button disabled={pending()} variant="outline" onClick={props.onClose}>取消</Button>
                            <Button disabled={!reason().trim() || pending()} onClick={() => void submit()}>
                                <Show when={pending()}><LoaderCircle class="h-4 w-4 animate-spin" /></Show>
                                {operation().confirmLabel}
                            </Button>
                        </div>
                    </section>
                </div>
            )}
        </Show>
    );
}

export interface AdjustmentTarget {
    studentId: number;
    studentName: string;
    actorRole: Extract<UserRole, "ADMIN" | "TEACHER">;
}

export function StudentPointAdjustmentDialog(props: {
    target?: AdjustmentTarget;
    onClose: () => void;
    onSuccess: () => void;
}) {
    const [amount, setAmount] = createSignal("");
    const [reason, setReason] = createSignal("");
    const [requestKey, setRequestKey] = createSignal("");
    const [pending, setPending] = createSignal(false);
    const [error, setError] = createSignal("");

    createEffect(() => {
        const target = props.target;
        if (target) {
            setAmount("");
            setReason("");
            setError("");
            setRequestKey(createPointAdjustmentRequestKey(target.actorRole, target.studentId));
        }
    });

    const parsedAmount = () => Number(amount());
    const valid = () => Number.isInteger(parsedAmount()) && parsedAmount() !== 0 && reason().trim().length > 0;

    const submit = async () => {
        const target = props.target;
        if (!target || !valid() || pending()) return;
        setPending(true);
        setError("");
        const payload = { requestKey: requestKey(), amount: parsedAmount(), reason: reason().trim() };
        try {
            if (target.actorRole === "ADMIN") await api.adjustAdminStudentPoints(target.studentId, payload);
            else await api.adjustTeacherStudentPoints(target.studentId, payload);
            props.onSuccess();
            props.onClose();
        } catch (requestError) {
            setError(errorMessage(requestError));
        } finally {
            setPending(false);
        }
    };

    return (
        <Show when={props.target}>
            {(target) => (
                <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4" role="presentation">
                    <section aria-labelledby="adjustment-title" aria-modal="true" class="w-full max-w-lg rounded-lg border border-border bg-background p-6 shadow-2xl" role="dialog">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <h2 class="text-lg font-semibold" id="adjustment-title">人工调整积分</h2>
                                <p class="mt-1 text-sm text-muted-foreground">学生：{target().studentName}（ID {target().studentId}）</p>
                            </div>
                            <Button aria-label="关闭" size="sm" variant="ghost" onClick={props.onClose}><X class="h-4 w-4" /></Button>
                        </div>
                        <div class="mt-5 space-y-4">
                            <div class="space-y-2">
                                <Label for="adjustment-amount">调整分值</Label>
                                <Input id="adjustment-amount" inputmode="numeric" placeholder="正数加分，负数减分" type="number" value={amount()} onInput={(event) => setAmount(event.currentTarget.value)} />
                            </div>
                            <div class="space-y-2">
                                <Label for="adjustment-reason">调整原因</Label>
                                <Textarea id="adjustment-reason" maxlength={500} placeholder="必填，说明人工调整依据" value={reason()} onInput={(event) => setReason(event.currentTarget.value)} />
                                <p class="text-right text-xs text-muted-foreground">{reason().length}/500</p>
                            </div>
                            <div class="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">请求标识：{requestKey()}</div>
                        </div>
                        <Show when={error()}><Alert class="mt-4 border-destructive/30 text-destructive">{error()}</Alert></Show>
                        <div class="mt-5 flex justify-end gap-3">
                            <Button disabled={pending()} variant="outline" onClick={props.onClose}>取消</Button>
                            <Button disabled={!valid() || pending()} onClick={() => void submit()}>
                                <Show when={pending()}><LoaderCircle class="h-4 w-4 animate-spin" /></Show>
                                确认调整
                            </Button>
                        </div>
                    </section>
                </div>
            )}
        </Show>
    );
}
