import { createEffect, createMemo, createSignal, For, Show } from "solid-js";
import { createStore } from "solid-js/store";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState } from "@/components/shared/empty-state";
import { PageHeader } from "@/components/shared/page-header";
import { useAuth } from "@/features/auth/auth-context";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type {
    CreateVideoStorageConfigPayload,
    UpdateVideoStorageConfigPayload,
    VideoStorageConfigResponse,
    VideoStorageConfigStatus,
    VideoStorageProviderType,
} from "@/types/api";

type FormState = {
    configName: string;
    secretId: string;
    secretKey: string;
    region: string;
    providerType: VideoStorageProviderType;
    subAppId: string;
    spaceName: string;
    procedureName: string;
    status: VideoStorageConfigStatus;
    isDefault: boolean;
    remark: string;
};

const createEmptyForm = (): FormState => ({
    configName: "",
    secretId: "",
    secretKey: "",
    region: "cn-north-1",
    providerType: "VOLCENGINE_VOD",
    subAppId: "",
    spaceName: "",
    procedureName: "",
    status: "DISABLED",
    isDefault: false,
    remark: "",
});

const createFormFromConfig = (config?: VideoStorageConfigResponse | null): FormState => ({
    configName: config?.configName ?? "",
    secretId: "",
    secretKey: "",
    region: config?.region ?? "cn-north-1",
    providerType: config?.providerType ?? "VOLCENGINE_VOD",
    subAppId: config?.subAppId ? String(config.subAppId) : "",
    spaceName: config?.spaceName ?? "",
    procedureName: config?.procedureName ?? "",
    status: config?.status ?? "DISABLED",
    isDefault: config?.isDefault ?? false,
    remark: config?.remark ?? "",
});

export function VideoStoragePage() {
    const auth = useAuth();
    const [configs, setConfigs] = createSignal<VideoStorageConfigResponse[]>([]);
    const [selectedConfigId, setSelectedConfigId] = createSignal<number | null>(null);
    const [loading, setLoading] = createSignal(false);
    const [saving, setSaving] = createSignal(false);
    const [actionKey, setActionKey] = createSignal<string | null>(null);
    const [feedback, setFeedback] = createSignal("");
    const [error, setError] = createSignal("");
    const [form, setForm] = createStore<FormState>(createEmptyForm());

    const selectedConfig = createMemo(
        () => configs().find((config) => config.id === selectedConfigId()) ?? null,
    );
    const isEditing = createMemo(() => selectedConfig() !== null);
    const isVolcengine = createMemo(() => form.providerType === "VOLCENGINE_VOD");
    const secretIdLabel = createMemo(() => isVolcengine() ? "AccessKeyId" : "SecretId");
    const secretKeyLabel = createMemo(() => isVolcengine() ? "SecretAccessKey" : "SecretKey");
    const subAppIdLabel = createMemo(() => isVolcengine() ? "AppId" : "SubAppId");

    const providerLabel = (providerType?: VideoStorageProviderType | null) =>
        providerType === "TENCENT_VOD" ? "腾讯云点播（已停用）" : "火山云点播";

    const syncForm = (config?: VideoStorageConfigResponse | null) => {
        setForm(createFormFromConfig(config));
    };

    const loadConfigs = async (preferredConfigId?: number | null) => {
        setLoading(true);
        setError("");

        try {
            const nextConfigs = await api.listVideoStorageConfigs();
            setConfigs(nextConfigs);
            const nextSelected = preferredConfigId !== undefined
                ? nextConfigs.find((item) => item.id === preferredConfigId) ?? null
                : nextConfigs.find((item) => item.id === selectedConfigId()) ?? nextConfigs[0] ?? null;
            setSelectedConfigId(nextSelected?.id ?? null);
            syncForm(nextSelected);
        } catch (loadError) {
            setError(loadError instanceof Error ? loadError.message : "加载视频存储配置失败");
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
        if (auth.user()?.role === "ADMIN") {
            void loadConfigs();
        }
    });

    const buildPayload = (): CreateVideoStorageConfigPayload | UpdateVideoStorageConfigPayload => ({
        configName: form.configName.trim(),
        secretId: form.secretId,
        secretKey: form.secretKey,
        region: form.region.trim(),
        providerType: form.providerType,
        subAppId: form.subAppId.trim() ? Number(form.subAppId.trim()) : undefined,
        spaceName: form.spaceName.trim() || undefined,
        procedureName: form.procedureName.trim() || undefined,
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
            if (!form.configName.trim()) {
                throw new Error("请输入配置名称");
            }
            if (!form.region.trim()) {
                throw new Error("请输入地域");
            }
            if (!isEditing() && !form.secretId.trim()) {
                throw new Error(`新建配置时必须填写 ${secretIdLabel()}`);
            }
            if (!isEditing() && !form.secretKey.trim()) {
                throw new Error(`新建配置时必须填写 ${secretKeyLabel()}`);
            }
            if (isVolcengine() && !form.spaceName.trim()) {
                throw new Error("请输入火山云空间名称");
            }
            if (form.isDefault && form.status !== "ENABLED") {
                throw new Error("默认配置必须处于启用状态");
            }
            if (form.subAppId.trim() && Number.isNaN(Number(form.subAppId.trim()))) {
                throw new Error(`${subAppIdLabel()} 必须是数字`);
            }

            const payload = buildPayload();
            const saved = isEditing() && selectedConfig()
                ? await api.updateVideoStorageConfig(selectedConfig()!.id, payload)
                : await api.createVideoStorageConfig(payload as CreateVideoStorageConfigPayload);

            setFeedback(isEditing() ? "视频存储配置已更新。" : "视频存储配置已创建。");
            await loadConfigs(saved.id);
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "保存视频存储配置失败");
        } finally {
            setSaving(false);
        }
    };

    const handleToggleStatus = async (config: VideoStorageConfigResponse) => {
        setActionKey(`status-${config.id}`);
        setFeedback("");
        setError("");

        try {
            const nextStatus: VideoStorageConfigStatus = config.status === "ENABLED" ? "DISABLED" : "ENABLED";
            await api.updateVideoStorageConfigStatus(config.id, nextStatus);
            setFeedback(`${config.configName} 已切换为 ${nextStatus === "ENABLED" ? "启用" : "禁用"}。`);
            await loadConfigs(config.id);
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "更新配置状态失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleSetDefault = async (config: VideoStorageConfigResponse) => {
        setActionKey(`default-${config.id}`);
        setFeedback("");
        setError("");

        try {
            await api.setDefaultVideoStorageConfig(config.id);
            setFeedback(`${config.configName} 已设为默认视频存储配置。`);
            await loadConfigs(config.id);
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "设置默认配置失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleTest = async (config: VideoStorageConfigResponse) => {
        setActionKey(`test-${config.id}`);
        setFeedback("");
        setError("");

        try {
            const result = await api.testVideoStorageConfig(config.id);
            setFeedback(`测试成功：${result.message}`);
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "测试配置失败");
        } finally {
            setActionKey(null);
        }
    };

    const handleDelete = async (config: VideoStorageConfigResponse) => {
        if (!window.confirm(`确定删除配置「${config.configName}」吗？`)) {
            return;
        }

        setActionKey(`delete-${config.id}`);
        setFeedback("");
        setError("");

        try {
            await api.deleteVideoStorageConfig(config.id);
            setFeedback("视频存储配置已删除。");
            await loadConfigs(config.id === selectedConfigId() ? null : selectedConfigId());
        } catch (actionError) {
            setError(actionError instanceof Error ? actionError.message : "删除配置失败");
        } finally {
            setActionKey(null);
        }
    };

    if (auth.user()?.role !== "ADMIN") {
        return (
            <section class="space-y-6">
                <PageHeader
                    eyebrow="Media"
                    title="视频存储"
                    description="该页面仅面向管理员开放。"
                />
                <Card>
                    <CardContent class="p-6">
                        <EmptyState title="无访问权限" description="当前账号没有访问视频存储页面的权限。" />
                    </CardContent>
                </Card>
            </section>
        );
    }

    return (
        <section class="space-y-6">
            <PageHeader
                eyebrow="Media"
                title="视频存储"
                description="统一维护火山云点播连接信息，控制平台默认上传配置，并验证后台连通性。"
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

            <div class="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
                <Card>
                    <CardHeader>
                        <CardTitle>配置列表</CardTitle>
                        <CardDescription>支持启用、禁用、设默认、测试和删除。</CardDescription>
                    </CardHeader>
                    <CardContent class="space-y-4">
                        <Show
                            when={!loading()}
                            fallback={<p class="text-sm text-muted-foreground">正在加载配置...</p>}
                        >
                            <Show
                                when={configs().length > 0}
                                fallback={<EmptyState title="暂无配置" description="先创建一条视频点播配置。" />}
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
                                                        <p class="font-medium text-foreground">{config.configName}</p>
                                                        <p class="text-xs text-muted-foreground">{providerLabel(config.providerType)}</p>
                                                        <p class="text-xs text-muted-foreground">{config.region}</p>
                                                        <p class="text-xs text-muted-foreground">{config.secretIdMasked}</p>
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
                                                            void handleTest(config);
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

                <Card>
                    <CardHeader>
                        <CardTitle>{isEditing() ? "编辑配置" : "创建配置"}</CardTitle>
                        <CardDescription>
                            编辑时如不填写新凭据，后端会保留原有密钥信息。
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form class="space-y-4" onSubmit={(event) => void handleSubmit(event)}>
                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label for="configName">配置名称</Label>
                                    <Input
                                        id="configName"
                                        value={form.configName}
                                        onInput={(event) => setForm("configName", event.currentTarget.value)}
                                        placeholder="例如 火山云点播主账号"
                                    />
                                </div>
                                <div class="space-y-2">
                                    <Label for="providerType">存储提供商</Label>
                                    <select
                                        id="providerType"
                                        class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                        value={form.providerType}
                                        onChange={(event) => {
                                            const nextProvider = event.currentTarget.value as VideoStorageProviderType;
                                            setForm("providerType", nextProvider);
                                        }}
                                    >
                                        <option value="VOLCENGINE_VOD">火山云点播</option>
                                    </select>
                                </div>
                            </div>

                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label for="region">地域</Label>
                                    <Input
                                        id="region"
                                        value={form.region}
                                        onInput={(event) => setForm("region", event.currentTarget.value)}
                                        placeholder="例如 cn-north-1"
                                    />
                                </div>
                                <Show
                                    when={isVolcengine()}
                                    fallback={
                                        <div class="space-y-2">
                                            <Label for="subAppId">{subAppIdLabel()}</Label>
                                            <Input
                                                id="subAppId"
                                                value={form.subAppId}
                                                onInput={(event) => setForm("subAppId", event.currentTarget.value)}
                                                placeholder="可选，例如 123456"
                                            />
                                        </div>
                                    }
                                >
                                    <div class="space-y-2">
                                        <Label for="spaceName">空间名称</Label>
                                        <Input
                                            id="spaceName"
                                            value={form.spaceName}
                                            onInput={(event) => setForm("spaceName", event.currentTarget.value)}
                                            placeholder="例如 learning-video-space"
                                        />
                                    </div>
                                </Show>
                            </div>

                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label for="secretId">{secretIdLabel()}</Label>
                                    <Input
                                        id="secretId"
                                        type="password"
                                        value={form.secretId}
                                        onInput={(event) => setForm("secretId", event.currentTarget.value)}
                                        placeholder={selectedConfig()?.secretIdMasked
                                            ? `留空表示沿用当前 ${secretIdLabel()}`
                                            : `请输入 ${secretIdLabel()}`}
                                    />
                                    <Show when={selectedConfig()?.secretIdMasked}>
                                        <p class="text-xs text-muted-foreground">当前：{selectedConfig()?.secretIdMasked}</p>
                                    </Show>
                                </div>
                                <div class="space-y-2">
                                    <Label for="secretKey">{secretKeyLabel()}</Label>
                                    <Input
                                        id="secretKey"
                                        type="password"
                                        value={form.secretKey}
                                        onInput={(event) => setForm("secretKey", event.currentTarget.value)}
                                        placeholder={selectedConfig()?.secretKeyMasked
                                            ? `留空表示沿用当前 ${secretKeyLabel()}`
                                            : `请输入 ${secretKeyLabel()}`}
                                    />
                                    <Show when={selectedConfig()?.secretKeyMasked}>
                                        <p class="text-xs text-muted-foreground">当前：{selectedConfig()?.secretKeyMasked}</p>
                                    </Show>
                                </div>
                            </div>

                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label for="procedureName">任务流</Label>
                                    <Input
                                        id="procedureName"
                                        value={form.procedureName}
                                        onInput={(event) => setForm("procedureName", event.currentTarget.value)}
                                        placeholder={isVolcengine()
                                            ? "可选，火山云上传扩展参数"
                                            : "可选，例如 video-transcode"}
                                    />
                                </div>
                                <Show when={isVolcengine()}>
                                    <div class="space-y-2">
                                        <Label for="subAppId">{subAppIdLabel()}</Label>
                                        <Input
                                            id="subAppId"
                                            value={form.subAppId}
                                            onInput={(event) => setForm("subAppId", event.currentTarget.value)}
                                            placeholder="可选，例如 123456"
                                        />
                                    </div>
                                </Show>
                            </div>

                            <div class="grid gap-4 md:grid-cols-2">
                                <div class="space-y-2">
                                    <Label for="status">状态</Label>
                                    <select
                                        id="status"
                                        class="h-11 w-full rounded-lg border border-input bg-background/70 px-3 text-sm"
                                        value={form.status}
                                        onChange={(event) => setForm("status", event.currentTarget.value as VideoStorageConfigStatus)}
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
                                        placeholder="例如 教学视频主配置"
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
            </div>
        </section>
    );
}
