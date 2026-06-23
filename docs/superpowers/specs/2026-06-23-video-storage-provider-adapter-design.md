# 视频存储多 Provider 兼容设计

## 1. 背景

当前 `word-backend` 已经支持教学视频资源管理，后台页面提供上传、列表、预览、同步、删除能力。现有实现强绑定腾讯云 VOD：

- `VideoStorageConfig` 保存腾讯云 `SecretId`、`SecretKey`、`Region`、`SubAppId`、`ProcedureName`
- `VideoAssetService` 直接依赖 `TencentVodGateway`
- `VideoAsset.tencentFileId` 保存腾讯云 `FileId`
- 后台视频资源页不关心云厂商，只展示本地保存的视频资源和预览地址

目标是在显示层不做大改的前提下，让当前项目底层兼容腾讯云 VOD 和火山云点播。选定方案为“Provider Adapter 最小抽象”：保留当前后台上传和管理体验，把云厂商差异收敛到后端 Provider 适配层。

## 2. 目标

1. 当前视频资源页基本不变，继续提供上传、列表、预览、同步、删除。
2. 视频存储配置支持选择 `TENCENT_VOD` 或 `VOLCENGINE_VOD`。
3. 上传视频时根据默认启用配置的 provider 选择对应云厂商。
4. 同步和删除视频时根据视频绑定的 `storageConfigId` 选择对应云厂商。
5. 历史腾讯云配置和历史腾讯云视频无需人工迁移即可继续使用。
6. 业务状态继续使用现有 `PROCESSING / READY / FAILED`。
7. 本阶段不引入 VidFlow 的发布/下架、普通用户观看、浏览器直传、上传会话、事件回调和审计模型。

## 3. 非目标

以下能力不进入本阶段：

- 浏览器直传火山云或腾讯云
- 上传会话状态机
- 视频发布、下架、审核工作流
- 学生端视频播放入口
- 播放签名、防盗链、短时播放 Token
- 云厂商事件回调消费
- 独立视频审计日志
- 重命名 `tencent_file_id` 数据库字段

这些能力可以作为后续阶段独立设计，避免本次改动影响现有教学视频后台。

## 4. 业务变更

### 4.1 视频存储配置

“视频存储配置”从腾讯云专用配置升级为多 Provider 配置。

新增 provider 类型：

| providerType | 含义 |
| --- | --- |
| `TENCENT_VOD` | 腾讯云点播 |
| `VOLCENGINE_VOD` | 火山云点播 |

配置页保持当前结构，只增加一个“提供商类型”下拉框。字段含义按 provider 动态解释：

| 现有字段 | 腾讯云 VOD | 火山云 VOD |
| --- | --- | --- |
| `secretId` | SecretId | AccessKeyId |
| `secretKey` | SecretKey | SecretAccessKey |
| `region` | 腾讯云 Region | 火山云 Region |
| `subAppId` | SubAppId | AppId |
| `procedureName` | 任务流/转码模板 | 保留为扩展字段，本阶段可为空 |

默认配置规则保持不变：

- 只能有一个默认启用配置。
- 上传视频时使用当前默认启用配置。
- 历史配置自动视为 `TENCENT_VOD`。

### 4.2 视频资源管理

视频资源页保持现有体验：

- 上传表单不增加复杂步骤。
- 视频列表、搜索、状态筛选、分页保持不变。
- 预览按钮仍只在 `READY` 且存在播放地址时可用。
- 同步状态按钮根据视频所属配置选择对应 provider。
- 删除按钮根据视频所属配置选择对应 provider 删除云端媒资。

页面可以选择性显示 provider badge，但不是必需。第一阶段建议只在视频存储配置页体现 provider，视频资源页不改变布局。

### 4.3 视频状态

保留当前三态：

- `PROCESSING`
- `READY`
- `FAILED`

Provider 适配层只返回“是否有可播放地址”和“是否首选播放地址就绪”，业务层继续按现有规则计算状态。

## 5. 数据库变更

### 5.1 `video_storage_configs`

新增字段：

```sql
ALTER TABLE video_storage_configs
    ADD COLUMN provider_type VARCHAR(32) NOT NULL DEFAULT 'TENCENT_VOD';

ALTER TABLE video_storage_configs
    ADD CONSTRAINT ck_video_storage_configs_provider_type
    CHECK (provider_type IN ('TENCENT_VOD', 'VOLCENGINE_VOD'));
```

历史数据通过默认值自动成为腾讯云配置。

### 5.2 `video_assets`

本阶段不修改 `video_assets` 表。

`tencent_file_id` 暂时继续保存云端媒资 ID：

- 腾讯云：`FileId`
- 火山云：`Vid`

代码层通过统一命名 `cloudMediaId` 包装该字段，避免业务服务继续扩散腾讯云命名。数据库字段重命名可放到后续兼容迁移中处理。

## 6. 后端结构设计

### 6.1 包结构

新增 provider 适配包：

```text
src/main/java/com/example/words/service/video/
├── VideoStorageGateway.java
├── VideoStorageGatewayRegistry.java
├── VideoStorageProviderType.java
├── VideoUploadResult.java
├── VideoMediaInfo.java
├── TencentVodStorageGateway.java
└── VolcengineVodStorageGateway.java
```

保留现有 `VideoAssetService` 作为业务编排层。

### 6.2 Provider 枚举

```java
package com.example.words.service.video;

public enum VideoStorageProviderType {
    TENCENT_VOD,
    VOLCENGINE_VOD
}
```

如果项目倾向把枚举放到 `model` 包，也可以命名为 `com.example.words.model.VideoStorageProviderType`，以便 JPA Entity 直接引用。推荐放到 `model` 包，因为它会持久化到 `VideoStorageConfig`。

### 6.3 统一网关接口

```java
package com.example.words.service.video;

import com.example.words.model.VideoStorageConfig;
import java.nio.file.Path;

public interface VideoStorageGateway {
    VideoStorageProviderType providerType();

    VideoUploadResult upload(
            VideoStorageConfig config,
            Path filePath,
            String originalFileName,
            String title,
            String description);

    VideoMediaInfo describeMedia(VideoStorageConfig config, String cloudMediaId);

    void deleteMedia(VideoStorageConfig config, String cloudMediaId);

    void validate(VideoStorageConfig config);
}
```

### 6.4 统一返回模型

```java
package com.example.words.service.video;

public record VideoUploadResult(
        String mediaId,
        String mediaUrl,
        String coverUrl,
        boolean transcodeRequested) {
}
```

```java
package com.example.words.service.video;

public record VideoMediaInfo(
        String mediaId,
        String mediaUrl,
        String coverUrl,
        Long durationSeconds,
        boolean ready,
        boolean preferredPlaybackReady) {
}
```

这两个模型分别替代当前的 `TencentVodUploadResult` 和 `TencentVodMediaInfo` 在业务服务中的直接使用。

### 6.5 Gateway Registry

```java
package com.example.words.service.video;

import com.example.words.exception.BadRequestException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VideoStorageGatewayRegistry {

    private final Map<VideoStorageProviderType, VideoStorageGateway> gateways;

    public VideoStorageGatewayRegistry(List<VideoStorageGateway> gatewayList) {
        this.gateways = new EnumMap<>(VideoStorageProviderType.class);
        for (VideoStorageGateway gateway : gatewayList) {
            this.gateways.put(gateway.providerType(), gateway);
        }
    }

    public VideoStorageGateway get(VideoStorageProviderType providerType) {
        VideoStorageGateway gateway = gateways.get(providerType);
        if (gateway == null) {
            throw new BadRequestException("Unsupported video storage provider: " + providerType);
        }
        return gateway;
    }
}
```

### 6.6 腾讯云适配器

`TencentVodStorageGateway` 迁移当前 `TencentVodSdkGateway` 的实现，接口从腾讯云专用模型改为统一模型。

职责：

- 使用腾讯云服务端 SDK 上传本地临时文件。
- 支持 `SubAppId`。
- 支持 `ProcedureName`。
- 当没有 `ProcedureName` 时保留当前默认 360p 转码策略。
- 查询 `DescribeMediaInfos`。
- 删除 `DeleteMedia`。
- 通过 `DescribeSubAppIds` 校验配置。

现有 `TencentVodGateway`、`TencentVodSdkGateway`、`TencentVodUploadResult`、`TencentVodMediaInfo` 可以先保留，但建议在本次重构中逐步替换为统一接口，避免双层网关长期并存。

### 6.7 火山云适配器

`VolcengineVodStorageGateway` 从 VidFlow 的 `VolcengineVodGateway` 提取可复用思路，但本阶段不使用浏览器上传 Token。

职责：

- 使用火山云 VOD 服务端 SDK 上传本地临时文件。
- 上传成功后返回 `Vid`。
- 查询媒资信息和播放地址。
- 删除云端媒资，如果 SDK 支持删除接口则实现；如果账号或 SDK 暂不可用，必须返回明确上游错误，不能静默成功。
- 连接测试至少校验 AK/SK、Region、AppId 或 SpaceName 相关配置真实可用。

火山云配置字段第一阶段复用现有表：

- `secretIdEncrypted` 存 AccessKeyId
- `secretKeyEncrypted` 存 SecretAccessKey
- `region` 存 Region
- `subAppId` 存 AppId
- `procedureName` 暂不作为必填

如果火山云服务端上传必须依赖 `spaceName`，但当前表没有专用字段，则第一阶段可选以下两种处理之一：

1. 将 `remark` 之外新增 `space_name` 字段。
2. 复用 `procedureName` 或 `remark` 会造成语义混乱，不推荐。

推荐新增 `space_name VARCHAR(128)`，仅火山云 provider 必填，腾讯云可为空。这样前端只需在火山云配置时多显示“空间名称”字段。

### 6.8 `VideoAssetService` 调整

当前依赖：

```java
private final TencentVodGateway tencentVodGateway;
```

调整为：

```java
private final VideoStorageGatewayRegistry gatewayRegistry;
```

上传流程：

```java
VideoStorageConfig storageConfig = videoStorageConfigService.getDefaultEnabledConfig();
VideoStorageGateway gateway = gatewayRegistry.get(storageConfig.getProviderType());

VideoUploadResult uploadResult = gateway.upload(
        storageConfig,
        tempFile,
        originalFileName,
        resolvedTitle,
        trimToNull(description));

VideoMediaInfo mediaInfo = uploadResult.transcodeRequested()
        ? waitForPreferredPlayback(gateway, storageConfig, uploadResult.mediaId())
        : gateway.describeMedia(storageConfig, uploadResult.mediaId());
```

同步流程：

```java
VideoStorageConfig config = videoStorageConfigService.getConfigEntity(videoAsset.getStorageConfigId());
VideoStorageGateway gateway = gatewayRegistry.get(config.getProviderType());
VideoMediaInfo mediaInfo = gateway.describeMedia(config, videoAsset.getTencentFileId());
```

删除流程：

```java
VideoStorageConfig config = videoStorageConfigService.getConfigEntity(videoAsset.getStorageConfigId());
VideoStorageGateway gateway = gatewayRegistry.get(config.getProviderType());
gateway.deleteMedia(config, videoAsset.getTencentFileId());
videoAssetRepository.delete(videoAsset);
```

辅助方法 `waitForPreferredPlayback`、`resolvePlayableUrl`、`resolveStatus` 改为使用统一 `VideoMediaInfo`。

## 7. API 和 DTO 变更

### 7.1 请求 DTO

`CreateVideoStorageConfigRequest` 和 `UpdateVideoStorageConfigRequest` 增加：

```java
private VideoStorageProviderType providerType;
private String spaceName;
```

规则：

- 创建时 `providerType` 必填。
- 更新时 `providerType` 必填，但不建议允许已经被视频引用的配置切换 provider。
- `providerType = TENCENT_VOD` 时，`spaceName` 可为空。
- `providerType = VOLCENGINE_VOD` 时，`spaceName` 必填。

### 7.2 响应 DTO

`VideoStorageConfigResponse` 增加：

```java
private VideoStorageProviderType providerType;
private String spaceName;
```

### 7.3 接口路径

接口路径保持不变：

- `GET /api/video-storage-configs`
- `POST /api/video-storage-configs`
- `PUT /api/video-storage-configs/{id}`
- `POST /api/video-storage-configs/{id}/test`
- `POST /api/videos/upload`
- `POST /api/videos/{id}/sync`
- `DELETE /api/videos/{id}`

不新增视频资源页 API。

## 8. 前端变更

### 8.1 视频资源页

`frontend/admin/src/pages/videos-page.tsx` 基本不变。

可选小改：

- 列表卡片显示配置名称后追加 provider badge。
- 错误信息中把“腾讯云状态”改为“云端状态”。

建议第一阶段只改文案，不改布局。

### 8.2 视频存储配置页

`frontend/admin/src/pages/video-storage-page.tsx` 增加 provider 下拉框：

```text
提供商类型：腾讯云 VOD / 火山云 VOD
```

字段标签按 provider 切换：

| provider | 密钥 ID 标签 | 密钥 Key 标签 | 应用字段标签 | 空间字段 |
| --- | --- | --- | --- | --- |
| `TENCENT_VOD` | SecretId | SecretKey | SubAppId | 隐藏 |
| `VOLCENGINE_VOD` | AccessKeyId | SecretAccessKey | AppId | 显示 SpaceName |

表单 payload 增加：

```ts
providerType: "TENCENT_VOD" | "VOLCENGINE_VOD";
spaceName?: string;
```

类型文件 `frontend/admin/src/types/api.ts` 增加同名字段。

## 9. 兼容与迁移策略

1. 新增 Flyway migration，为 `video_storage_configs` 添加 `provider_type`，默认 `TENCENT_VOD`。
2. 如采纳火山云 `spaceName` 字段，新增 `space_name`，可为空。
3. `VideoStorageConfig` Entity 增加 `providerType` 和 `spaceName`。
4. 历史配置无需人工处理，默认走腾讯云适配器。
5. 历史视频继续通过 `storageConfigId` 找到腾讯云配置，同步和删除仍走腾讯云。
6. 新建火山云配置并设为默认后，新上传视频走火山云。
7. 已上传视频不随默认配置变化而改变 provider，它永远跟随自己的 `storageConfigId`。

## 10. 错误处理

统一 provider 错误语义：

- 配置缺失或 provider 不支持：`BadRequestException`
- 云厂商鉴权失败：`BadGatewayException`，message 中保留安全的业务原因
- 云厂商媒资不存在：同步时可标记 `FAILED` 或返回明确错误；删除时可按当前腾讯云逻辑兼容“云端已不存在”
- 火山云未配置 SpaceName：`BadRequestException`
- 火山云播放地址尚不可用：保持 `PROCESSING`，提示稍后同步

密钥和完整播放鉴权 URL 不应进入日志或错误响应。

## 11. 测试策略

### 11.1 后端单元测试

重点测试：

- `VideoStorageGatewayRegistry` 能按 provider 返回正确 gateway。
- 不支持 provider 时抛出明确异常。
- `VideoStorageConfigService` 创建/更新时校验 provider 字段。
- 腾讯云历史配置默认 provider 为 `TENCENT_VOD`。
- 火山云配置缺少 `spaceName` 时创建或测试失败。
- `VideoAssetService.upload` 根据默认配置选择正确 gateway。
- `VideoAssetService.sync/delete` 根据视频所属配置选择正确 gateway，而不是当前默认配置。

### 11.2 集成测试

使用 mock gateway 或 Spring test bean 验证：

- 腾讯云配置上传仍写入 `video_assets.tencent_file_id`。
- 火山云配置上传也写入同一字段，但值是 `Vid`。
- `READY` 计算仍依赖统一 `VideoMediaInfo.ready/preferredPlaybackReady`。

### 11.3 前端测试

重点测试：

- provider 下拉框切换后字段标签变化。
- 腾讯云配置不要求 SpaceName。
- 火山云配置要求 SpaceName。
- payload 包含 `providerType` 和 `spaceName`。

## 12. 分阶段实施建议

### 第一阶段：后端抽象与腾讯云回归

目标：

- 引入 provider 抽象。
- 把现有腾讯云实现迁移到 `TencentVodStorageGateway`。
- 所有现有腾讯云行为保持一致。

验收：

- 旧配置可上传、同步、删除。
- 现有视频资源页无功能回退。

### 第二阶段：配置模型和前端 provider 选择

目标：

- `video_storage_configs` 增加 `provider_type` 和可选 `space_name`。
- 配置页支持选择腾讯云/火山云。
- DTO 和类型同步。

验收：

- 可创建腾讯云配置。
- 可创建火山云配置。
- 默认配置仍唯一。

### 第三阶段：火山云适配器

目标：

- 实现 `VolcengineVodStorageGateway`。
- 支持服务端上传、媒资查询、删除和配置测试。

验收：

- 火山云配置设为默认后，同一个上传入口可以上传到火山 VOD。
- 上传后视频可预览或处于 `PROCESSING` 并可同步到 `READY`。
- 删除视频能删除火山云媒资或返回明确错误。

### 第四阶段：收口命名与文案

目标：

- 后端变量名逐步使用 `cloudMediaId`。
- 前端文案从“腾讯云状态”改为“云端状态”。
- 文档补充多 provider 使用说明。

验收：

- 用户在页面上能理解当前配置属于哪个云厂商。
- 代码中的腾讯云命名不再扩散到新逻辑。

## 13. 风险与约束

1. 火山云服务端上传 SDK 的 API 与 VidFlow 当前直传实现不同，需要单独验证 SDK 方法和 Maven 依赖。
2. 当前表没有 `spaceName` 字段，如果火山云上传必须依赖 SpaceName，应新增字段，不建议复用 `remark`。
3. `tencent_file_id` 字段会在第一阶段保存火山 `Vid`，这是有意兼容取舍。代码层必须避免继续把它理解成腾讯专属 ID。
4. 当前后端仍会接收视频二进制并临时落盘，兼容火山云后也不会自动获得 VidFlow 的直传优势。
5. 不引入事件回调意味着云端处理完成仍依赖手动同步或当前等待逻辑。

## 14. 验收标准

本次变更完成后应满足：

1. 历史腾讯云配置无需人工迁移，仍可正常使用。
2. 腾讯云上传、同步、预览、删除行为不回退。
3. 管理员可以创建火山云视频存储配置并设为默认。
4. 默认配置为火山云时，当前视频上传入口上传到火山云点播。
5. 火山云上传后的视频可以通过当前后台视频资源页预览、同步、删除。
6. 视频资源页布局不发生大规模变化。
7. 代码中云厂商差异集中在 Provider Gateway 实现内。
8. `VideoAssetService` 不再直接依赖腾讯云专用 gateway。

## 15. 后续可选演进

在多 provider 基础稳定后，可以独立评估：

- 浏览器直传，迁移 VidFlow 的上传 Token 和上传会话模型。
- 学生端视频播放，增加发布/下架和播放策略。
- 云厂商事件回调，自动更新 `PROCESSING` 到 `READY`。
- 视频审计日志，记录上传、同步、删除和播放。
- 数据库字段从 `tencent_file_id` 正式迁移为 `cloud_media_id`。
