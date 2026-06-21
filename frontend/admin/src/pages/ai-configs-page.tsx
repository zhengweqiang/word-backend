import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type {
    AiConfigResponse,
    AiConfigStatus,
    CreateAiConfigPayload,
    SyllableBackfillResponse,
    UpdateAiConfigPayload,
} from "@/types/api";

type FormState = {
    providerName: string;
    apiUrl: string;
    apiKey: string;
    modelName: string;
    status: AiConfigStatus;
    isDefault: boolean;
    remark: string;
};

type ChatExchange = {
    userMessage: string;
    reply: string;
};

const createEmptyForm = (): FormState => ({
    providerName: "",
    apiUrl: "",
    apiKey: "",
    modelName: "",
    status: "DISABLED",
    isDefault: false,
    remark: "",
});

const createFormFromConfig = (config?: AiConfigResponse | null): FormState => ({
    providerName: config?.providerName ?? "",
    apiUrl: config?.apiUrl ?? "",
    apiKey: "",
    modelName: config?.modelName ?? "",
    status: config?.status ?? "DISABLED",
    isDefault: config?.isDefault ?? false,
    remark: config?.remark ?? "",
});

export function AiConfigsPage() {
    const auth = useAuth();
    const [configs, setConfigs] = createSignal<AiConfigResponse[]>([]);
    const [selectedConfigId, setSelectedConfigId] = createSignal<number | null>(null);
    const [loading, setLoading] = createSignal(false);
    const [saving, setSaving] = createSignal(false);
    const [actionKey, setActionKey] = createSignal<string | null>(null);
    const [feedback, setFeedback] = createSignal("");
    const [error, setError] = createSignal("");
    const [chatInput, setChatInput] = createSignal("你好，请回复 test-ok。");
    const [chatSending, setChatSending] = createSignal(false);
    const [chatHistory, setChatHistory] = createSignal<ChatExchange[]>([]);
    const [backfillRunning, setBackfillRunning] = createSignal(false);
    const [backfillResult, setBackfillResult] = createSignal<SyllableBackfillResponse | null>(null);
    const [form, setForm] = createStore<FormState>(createEmptyForm());

    const selectedConfig = createMemo(
        () => configs().find((config) => config.id === selectedConfigId()) ?? null,
    );
    const isEditing = createMemo(() => selectedConfig() !== null);

    const syncForm = (config?: AiConfigResponse | null) => {
        setForm(createFormFromConfig(config));
        setChatInput("你好，请回复 test-ok。");
        setChatHistory([]);
    };

    const loadConfigs = async (preferredConfigId?: number | null) => {
        setLoading(true);
        setError("");

        try {
            const nextConfigs = await api.listAiConfigs();
            setConfigs(nextConfigs);
            const nextSelected = preferredConfigId !== undefined
                ? nextConfigs.find((item) => item.id === preferredConfigId) ?? null
                : nextConfigs.find((item) => item.id === selectedConfigId()) ?? nextConfigs[0] ?? null;
            setSelectedConfigId(nextSelected?.id ?? null);
            syncForm(nextSelected);
        } catch (loadError) {
            setError(loadError instanceof Error ? loadError.message : "加载 AI 配置失败");
        } finally {
            setLoading(false);
        }
    };

    createEffect(() => {
        if (selectedConfig()) {
            syncForm(selectedConfig());
            return;
        }
        if (selectedConfigId() === null) {
            syncForm(null);
        }
    });

    createEffect(() => {
        if (auth.user()) {
            void loadConfigs();
        }
    });

    const buildPayload = (): CreateAiConfigPayload | UpdateAiConfigPayload => ({
        providerName: form.providerName.trim(),
        apiUrl: form.apiUrl.trim(),
        apiKey: form.apiKey,
        modelName: form.modelName.trim(),
        status: form.status,
        isDefault: form.isDefault,
        remark: form.remark.trim() || undefined,
    });

    const handleCreateMode = () => {
        setSelectedConfigId(null);
        setFeedback("");
        setError("");
        syncForm(null);
    };

    const handleSubmit = async (event: SubmitEvent) => {
        event.preventDefault();
        setSaving(true);
        setFeedback("");
        setError("");

        try {
            if (!form.providerName.trim()) {
                throw new Error("请输入 AI 厂商名称");
            }
            if (!form.apiUrl.trim()) {
                throw new Error("请输入接口地址");
            }
            if (!form.modelName.trim()) {
                throw new Error("请输入模型名称");
            }
            if (!isEditing() && !form.apiKey.trim()) {
                throw new Error("新建配置时必须填写 API Key");
            }
            if (form.isDefault && form.status !== "ENABLED") {
                throw new Error("默认配置必须处于启用状态");
            }

            const payload = buildPayload();
            const saved = isEditing() && selectedConfig()
                ? await api.updateAiConfig(selectedConfig()!.id, payload)
                : await api.createAiConfig(payload as CreateAiConfigPayload);

            setFeedback(isEditing() ? "AI 配置已更新。" : "AI 配置已创建。");
            await loadConfigs(saved.id);
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "保存 AI 配置失败");
        } finally {
            setSaving(false);
        }
    };

    const handleToggleStatus = async (config: AiConfigResponse) => {
        setActionKey(`status-${config.id}`);
        setFeedback("");
        setError("");

        try {
            const nextStatus: AiConfigStatus = config.status === "ENABLED" ? "DISABLED" : "ENABLED";
            await api.updateAiConfigStatus(config.id, nextStatus);
            setFeedback(`${config.providerName} 已切换为 ${nextStatus === "ENABLED" ? "启用" : "禁用"}。`);
            await loadConfigs(config.id);
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "更新状态失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleSetDefault = async (config: AiConfigResponse) => {
        setActionKey(`default-${config.id}`);
        setFeedback("");
        setError("");

        try {
            await api.setDefaultAiConfig(config.id);
            setFeedback(`${config.providerName} 已设为默认配置。`);
            await loadConfigs(config.id);
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "设置默认配置失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleDelete = async (config: AiConfigResponse) => {
        if (!window.confirm(`确定删除配置「${config.providerName} / ${config.modelName}」吗？`)) {
            return;
        }

        setActionKey(`delete-${config.id}`);
        setFeedback("");
        setError("");

        try {
            await api.deleteAiConfig(config.id);
            setFeedback("AI 配置已删除。");
            await loadConfigs(config.id === selectedConfigId() ? null : selectedConfigId());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "删除 AI 配置失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleQuickTest = async (config: AiConfigResponse) => {
        setActionKey(`test-${config.id}`);
        setFeedback("");
        setError("");

        try {
            const result = await api.testAiConfig(config.id);
            setFeedback(`测试成功，模型回复：${result.reply}`);
            if (config.id === selectedConfigId()) {
                setChatHistory((current) => [
                    ...current,
                    { userMessage: "Reply with test-ok only.", reply: result.reply },
                ]);
            }
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "测试失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleSendChat = async () => {
        const config = selectedConfig();
        if (!config) {
            setError("请先选择一条配置");
            return;
        }
        if (config.status !== "ENABLED") {
            setError("请先启用当前配置，再进行对话测试");
            return;
        }
        if (!chatInput().trim()) {
            setError("请输入测试消息");
            return;
        }

        setChatSending(true);
        setFeedback("");
        setError("");

        try {
            const response = await api.chatWithAi({
                configId: config.id,
                messages: [{ role: "user", content: chatInput().trim() }],
            });
            setChatHistory((current) => [
                ...current,
                {
                    userMessage: chatInput().trim(),
                    reply: response.reply,
                },
            ]);
            setChatInput("");
            setFeedback("对话测试成功，当前配置可用。");
        } catch (chatError) {
            setError(chatError instanceof Error ? chatError.message : "发送测试消息失败");
        } finally {
            setChatSending(false);
        }
    };

    const handleSyllableBackfill = async () => {
        setBackfillRunning(true);
        setBackfillResult(null);
        setFeedback("");
        setError("");

        try {
            setBackfillResult(await api.backfillSyllables(200));
        } catch (backfillError) {
            setError(backfillError instanceof Error ? backfillError.message : "音节回填失败");
        } finally {
            setBackfillRunning(false);
        }
    };

    if (auth.user()?.role !== "ADMIN") {
        return (
            <section class="space-y-6">
                <PageHeader
                    eyebrow="AI"
                    title="AI 配置"
                    description="该页面仅面向管理员开放。"
                />
                <Card>
                    <CardContent class="p-6">
                        <EmptyState title="无访问权限" description="当前账号没有访问 AI 配置页面的权限。" />
                    </CardContent>
                </Card>
            </section>
        );
    }

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="AI"
                title="AI 配置"
                description="管理员在后台仅管理自己的 AI 配置，不查看其他用户的密钥或模型设置。"
                actions={
                    <div class="flex flex-wrap items-center gap-3">
                        <Button variant="outline" onClick={() => void loadConfigs()}>
                            刷新
                        </Button>
                        <Button onClick={handleCreateMode}>新建配置</Button>
                    </div>
                }
            />

            <Show when={feedback()}>
                <Alert class="border-success/20 bg-success/10 text-success">{feedback()}</Alert>
            </Show>

            <Show when={error()}>
                <Alert class="border-destructive/20 bg-destructive/10 text-destructive">{error()}</Alert>
            </Show>

            <Card>
                <CardHeader>
                    <CardTitle>音节数据回填</CardTitle>
                    <CardDescription>
                        为已发布学习计划中缺少音节的单词生成结构化英美音拆读数据。单次最多处理 200 个，校验失败的词不会写入。
                    </CardDescription>
                </CardHeader>
                <CardContent class="space-y-4">
                    <div class="flex flex-wrap items-center justify-between gap-4">
                        <p class="text-sm text-muted-foreground">需要已启用的默认 AI 配置。任务会逐词处理，单个失败不会中断整批。</p>
                        <Button onClick={() => void handleSyllableBackfill()} disabled={backfillRunning()}>
                            {backfillRunning() ? "正在回填..." : "回填音节"}
                        </Button>
                    </div>
                    <Show when={backfillResult()}>
                        {(result) => (
                            <div class="space-y-3 border-t border-border/80 pt-4">
                                <p class="font-medium text-foreground">已更新 {result().updated} / {result().attempted} 个单词</p>
                                <p class="text-sm text-muted-foreground">
                                    跳过 {result().skipped} 个，失败 {result().failures.length} 个。
                                </p>
                                <Show when={result().failures.length > 0}>
                                    <div class="space-y-2" role="status">
                                        <For each={result().failures}>
                                            {(failure) => (
                                                <p class="rounded-lg border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                                                    {failure.word}：{failure.reason}
                                                </p>
                                            )}
                                        </For>
                                    </div>
                                </Show>
                            </div>
                        )}
                    </Show>
                </CardContent>
            </Card>

            <div class="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
                <Card>
                    <CardHeader>
                        <CardTitle>我的配置</CardTitle>
                        <CardDescription>支持启用、禁用、设为默认、测试和删除。</CardDescription>
                    </CardHeader>
                    <CardContent class="space-y-4">
                        <Show
                            when={!loading()}
                            fallback={<p class="text-sm text-muted-foreground">正在加载配置...</p>}
                        >
                            <Show
                                when={configs().length > 0}
                                fallback={<EmptyState title="暂无配置" description="先创建一条 AI 配置后再进行测试。" />}
                            >
                                <div class="space-y-3">
                                    <For each={configs()}>
                                        {(config) => (
                                            <div
                                                class={`w-full rounded-2xl border p-4 text-left transition ${
                                                    config.id === selectedConfigId()
                                                        ? "border-primary/40 bg-primary/5 shadow-sm"
                                                        : "border-border/80 bg-background/70 hover:border-primary/20"
                                                }`}
                                                onClick={() => setSelectedConfigId(config.id)}
                                            >
                                                <div class="flex flex-wrap items-start justify-between gap-3">
                                                    <div class="space-y-1">
                                                        <p class="font-medium text-foreground">{config.providerName}</p>
                                                        <p class="text-xs text-muted-foreground">{config.modelName}</p>
                                                        <p class="text-xs text-muted-foreground">{config.apiKeyMasked}</p>
                                                    </div>
                                                    <div class="flex flex-wrap gap-2">
                                                        <Badge variant={config.status === "ENABLED" ? "success" : "outline"}>
                                                            {config.status}
                                                        </Badge>
                                                        <Show when={config.isDefault}>
                                                            <Badge variant="warning">DEFAULT</Badge>
                                                        </Show>
                                                    </div>
                                                </div>

                                                <p class="mt-3 text-xs text-muted-foreground">
                                                    更新于 {formatDateTime(config.updatedAt)}
                                                </p>

                                                <div class="mt-4 flex flex-wrap gap-2">
                                                    <Button variant="outline" size="sm">
                                                        编辑
                                                    </Button>
                                                    <Button
                                                        variant="outline"
                                                        size="sm"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            void handleToggleStatus(config);
                                                        }}
                                                    >
                                                        {actionKey() === `status-${config.id}`
                                                            ? "处理中..."
                                                            : config.status === "ENABLED"
                                                                ? "禁用"
                                                                : "启用"}
                                                    </Button>
                                                    <Button
                                                        variant="outline"
                                                        size="sm"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            void handleSetDefault(config);
                                                        }}
                                                    >
                                                        {actionKey() === `default-${config.id}` ? "处理中..." : "设为默认"}
                                                    </Button>
                                                    <Button
                                                        variant="outline"
                                                        size="sm"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            void handleQuickTest(config);
                                                        }}
                                                    >
                                                        {actionKey() === `test-${config.id}` ? "测试中..." : "测试"}
                                                    </Button>
                                                    <Button
                                                        variant="destructive"
                                                        size="sm"
                                                        onClick={(event) => {
                                                            event.stopPropagation();
                                                            void handleDelete(config);
                                                        }}
                                                    >
                                                        {actionKey() === `delete-${config.id}` ? "删除中..." : "删除"}
                                                    </Button>
                                                </div>
                                            </div>
                                        )}
                                    </For>
                                </div>
                            </Show>
                        </Show>
                    </CardContent>
                </Card>

                <div class="space-y-6">
                    <Card>
                        <CardHeader>
                            <CardTitle>{isEditing() ? "编辑配置" : "创建配置"}</CardTitle>
                            <CardDescription>
                                编辑时若不填写新密钥，后端会保留原有 API Key。
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            <form class="space-y-4" onSubmit={(event) => void handleSubmit(event)}>
                                <div class="grid gap-4 md:grid-cols-2">
                                    <div class="space-y-2">
                                        <Label for="providerName">厂商名称</Label>
                                        <Input
                                            id="providerName"
                                            value={form.providerName}
                                            onInput={(event) => setForm("providerName", event.currentTarget.value)}
                                            placeholder="例如 OpenAI"
                                        />
                                    </div>
                                    <div class="space-y-2">
                                        <Label for="modelName">模型名称</Label>
                                        <Input
                                            id="modelName"
                                            value={form.modelName}
                                            onInput={(event) => setForm("modelName", event.currentTarget.value)}
                                            placeholder="例如 gpt-4o-mini"
                                        />
                                    </div>
                                </div>

                                <div class="space-y-2">
                                    <Label for="apiUrl">接口地址</Label>
                                    <Input
                                        id="apiUrl"
                                        value={form.apiUrl}
                                        onInput={(event) => setForm("apiUrl", event.currentTarget.value)}
                                        placeholder="https://api.openai.com/v1/chat/completions"
                                    />
                                </div>

                                <div class="space-y-2">
                                    <Label for="apiKey">API Key</Label>
                                    <Input
                                        id="apiKey"
                                        type="password"
                                        value={form.apiKey}
                                        onInput={(event) => setForm("apiKey", event.currentTarget.value)}
                                        placeholder={selectedConfig()?.apiKeyMasked ? "留空表示沿用当前密钥" : "请输入 API Key"}
                                    />
                                    <Show when={selectedConfig()?.apiKeyMasked}>
                                        <p class="text-xs text-muted-foreground">当前密钥：{selectedConfig()?.apiKeyMasked}</p>
                                    </Show>
                                </div>

                                <div class="grid gap-4 md:grid-cols-2">
                                    <div class="space-y-2">
                                        <Label for="status">状态</Label>
                                        <select
                                            id="status"
                                            class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                            value={form.status}
                                            onChange={(event) => setForm("status", event.currentTarget.value as AiConfigStatus)}
                                        >
                                            <option value="DISABLED">DISABLED</option>
                                            <option value="ENABLED">ENABLED</option>
                                        </select>
                                    </div>
                                    <div class="space-y-2">
                                        <Label for="remark">备注</Label>
                                        <Input
                                            id="remark"
                                            value={form.remark}
                                            onInput={(event) => setForm("remark", event.currentTarget.value)}
                                            placeholder="例如 阅读短文生成"
                                        />
                                    </div>
                                </div>

                                <label class="flex items-center gap-3 text-sm text-muted-foreground">
                                    <input
                                        type="checkbox"
                                        checked={form.isDefault}
                                        onChange={(event) => setForm("isDefault", event.currentTarget.checked)}
                                    />
                                    <span>设为默认配置</span>
                                </label>

                                <div class="flex flex-wrap justify-end gap-3">
                                    <Button variant="outline" onClick={handleCreateMode}>
                                        清空
                                    </Button>
                                    <Button type="submit" disabled={saving()}>
                                        {saving() ? "保存中..." : isEditing() ? "保存修改" : "创建配置"}
                                    </Button>
                                </div>
                            </form>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader>
                            <CardTitle>对话测试</CardTitle>
                            <CardDescription>使用当前选中的启用配置发送一条测试消息，验证调用链路是否可用。</CardDescription>
                        </CardHeader>
                        <CardContent class="space-y-4">
                            <Textarea
                                value={chatInput()}
                                onInput={(event) => setChatInput(event.currentTarget.value)}
                                placeholder="输入一条测试消息"
                            />
                            <div class="flex justify-end">
                                <Button onClick={() => void handleSendChat()} disabled={chatSending() || !selectedConfig()}>
                                    {chatSending() ? "发送中..." : "发送测试消息"}
                                </Button>
                            </div>

                            <Show
                                when={chatHistory().length > 0}
                                fallback={
                                    <EmptyState
                                        title="还没有测试记录"
                                        description="保存并选中一条启用配置后，可以立即在这里验证接口连通性。"
                                    />
                                }
                            >
                                <div class="space-y-3">
                                    <For each={chatHistory()}>
                                        {(item) => (
                                            <div class="rounded-2xl border border-border/80 bg-background/70 p-4">
                                                <div class="space-y-3">
                                                    <div>
                                                        <p class="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">User</p>
                                                        <p class="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">
                                                            {item.userMessage}
                                                        </p>
                                                    </div>
                                                    <div>
                                                        <p class="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Assistant</p>
                                                        <p class="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">
                                                            {item.reply}
                                                        </p>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </For>
                                </div>
                            </Show>
                        </CardContent>
                    </Card>
                </div>
            </div>
        </section>
    );
}
