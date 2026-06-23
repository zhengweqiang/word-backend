# VidFlow 与当前项目视频处理需求差异总结

## 1. 文档目的

本文档对比 `/Users/wyn/code/vidFlow` 与当前 `word-backend` 项目在视频处理需求上的差异，重点关注上传链路、云厂商能力、处理状态、播放、权限、可靠性和后续演进方向。

本文档仅做需求与现状总结，不涉及代码修改。

## 2. 参考范围

本次对比主要参考以下文件：

- `vidFlow/README.md`
- `vidFlow/docs/video-upload-design.md`
- `vidFlow/AGENT.MD`
- `vidFlow/backend/src/main/java/com/vidflow/video/VideoService.java`
- `vidFlow/backend/src/main/java/com/vidflow/video/Video.java`
- `vidFlow/backend/src/main/java/com/vidflow/upload/UploadSession.java`
- `vidFlow/backend/src/main/java/com/vidflow/vod/VolcengineVodGateway.java`
- `vidFlow/backend/src/main/java/com/vidflow/vod/VodEventController.java`
- `word-backend/docs/tencent-vod-video-management-analysis.zh-CN.md`
- `word-backend/src/main/java/com/example/words/service/VideoAssetService.java`
- `word-backend/src/main/java/com/example/words/service/TencentVodSdkGateway.java`
- `word-backend/src/main/java/com/example/words/model/VideoAsset.java`
- `word-backend/src/main/java/com/example/words/model/VideoStorageConfig.java`
- `word-backend/src/main/java/com/example/words/controller/VideoController.java`
- `word-backend/frontend/admin/src/pages/videos-page.tsx`

## 3. 一句话结论

`vidFlow` 的视频处理需求是一个独立的“视频上传与观看平台”：强调浏览器直传 VOD、上传会话、发布/下架、普通用户观看、事件回调、审计和最终一致。

当前 `word-backend` 的视频处理需求是词汇学习系统后台里的“教学视频资源管理”：强调管理员/老师上传教学视频、服务端中转到腾讯云 VOD、后台预览、手动同步状态、按系统/教师资源范围管理。

## 4. 核心差异总览

| 维度 | VidFlow | 当前 word-backend |
| --- | --- | --- |
| 产品定位 | 独立视频上传与观看系统 | 词汇学习后台中的教学视频资源模块 |
| 云厂商 | 火山引擎 VOD | 腾讯云 VOD |
| 上传方式 | 浏览器使用 `tt-uploader` 直传 VOD | 后端接收 `multipart`，临时落盘后用腾讯云服务端 SDK 上传 |
| 业务服务器是否接触视频二进制 | 明确要求不接触、不落盘 | 会接收文件并创建临时文件，上传后删除 |
| 上传凭证 | 后端签发短时上传 Token | 不签发浏览器上传 Token |
| 上传会话 | 有独立 `upload_sessions` 状态机 | 无上传会话表，上传请求和视频记录创建基本绑定在同一流程 |
| 视频主标识 | 火山 `Vid` | 腾讯云 `FileId` |
| 视频状态 | `Uploaded / Processing / Unpublished / Published / Failed / Deleted` | `PROCESSING / READY / FAILED` |
| 发布语义 | 明确支持发布、下架、普通用户只看已发布 | 无发布/下架状态，主要是后台可预览资源 |
| 播放方式 | 普通用户请求播放地址，前端支持 MP4/HLS | 后台请求预览地址，直接用 `mediaUrl` 播放 |
| 权限模型 | Sa-Token RBAC，细到 `video:create`、`video:publish`、`video:play` 等权限 | Spring Security 角色，接口限制 `ADMIN`/`TEACHER`，再按资源范围判断 |
| 资源范围 | 以视频状态、播放策略、上传人、角色权限控制 | `SYSTEM` 与 `TEACHER` 两类资源范围 |
| 审计 | 登录、上传 Token、创建、播放、发布等均有审计日志 | 视频模块当前主要是业务操作，无独立视频审计模型 |
| 回调/异步 | 有 VOD 事件回调落库、任务表和补偿设计 | 当前偏手动同步，文档建议后续补齐事件驱动 |
| 配置管理 | 火山 VOD 配置中心，AK/SK 加密保存 | 腾讯云 VOD 存储配置，支持默认配置、SubAppId、ProcedureName |

## 5. 上传链路差异

### 5.1 VidFlow

VidFlow 的核心上传要求是“视频二进制不进入业务服务器”：

1. 管理员选择本地视频。
2. 前端请求 `/api/upload-token`。
3. 后端创建上传会话并签发火山 VOD 短时上传 Token。
4. 浏览器通过 `tt-uploader` 直传火山 VOD。
5. VOD 返回 `Vid`。
6. 前端再调用 `/api/videos`，用 `sessionId + vid` 绑定业务元数据。

这套设计主要解决大文件上传对业务服务器带宽、磁盘和超时的压力，也为“云端已上传但本地元数据保存失败”提供补偿空间。

### 5.2 当前 word-backend

当前项目的上传链路是“后台接收文件后上传腾讯云”：

1. 管理员或老师在后台选择视频文件。
2. 前端提交 `multipart/form-data` 到 `/api/videos/upload`。
3. 后端校验扩展名和 MIME 类型。
4. 后端创建临时文件并写入上传内容。
5. 后端使用腾讯云 VOD 服务端 SDK 上传。
6. 腾讯云返回 `FileId`、媒体地址和封面地址。
7. 后端查询媒体信息，生成 `video_assets` 记录。
8. 临时文件在 `finally` 中删除。

这套设计更容易集成在现有后台里，但业务服务器需要承受上传流量、临时磁盘占用和长请求等待。

## 6. 视频处理与转码差异

### 6.1 VidFlow

VidFlow 把“处理能力”作为可扩展方向：

- 首版不在业务服务器做转码、截图、加密或 CDN 分发。
- VOD 是媒资处理事实来源。
- 状态模型预留 `Processing`。
- 文档要求后续可扩展转码、封面截图、审核、播放鉴权、事件回调和任务补偿。
- 如果播放地址暂不可用，播放接口返回明确的处理中错误。

VidFlow 需求上更强调处理流程的状态机、幂等和补偿，但当前实现更偏 MVP，实际代码中主要已实现上传 Token、媒资查询、发布/下架、播放地址查询和回调落库。

### 6.2 当前 word-backend

当前项目已经在实现中引入更具体的腾讯云处理策略：

- 如果未配置 `ProcedureName`，上传后会主动调用 `ProcessMedia` 触发默认 360p MP4 转码。
- 后端会最多等待 10 次，每次间隔 3 秒，尝试拿到首选转码播放地址。
- 如果拿到首选转码地址，视频状态为 `READY`。
- 如果还没有可播放地址，状态为 `PROCESSING`。
- 管理员/老师可以在后台手动点击“同步状态”，重新查询腾讯云媒资信息。

这说明当前项目的视频处理需求更偏“后台教学视频尽快得到一个稳定可预览的低清 MP4 地址”，而不是完整的视频平台生命周期。

## 7. 状态模型差异

### 7.1 VidFlow 状态更细

VidFlow 区分上传会话状态和视频生命周期状态。

上传会话状态包括：

- `TokenIssued`
- `Uploading`
- `Uploaded`
- `Bound`
- `Expired`
- `Failed`
- `Cancelled`

视频生命周期状态包括：

- `Uploaded`
- `Processing`
- `Unpublished`
- `Published`
- `Failed`
- `Deleted`

这种模型适合支持直传、失败恢复、重复提交、发布/下架、普通用户可见性和后续审核。

### 7.2 当前项目状态更轻

当前项目只有视频资源状态：

- `PROCESSING`
- `READY`
- `FAILED`

这更适合后台资源管理场景：资源要么处理中，要么可预览，要么失败。它没有表达“已上传但未发布”“已下架”“已删除但保留审计”等平台型状态。

## 8. 播放需求差异

### 8.1 VidFlow

VidFlow 面向普通用户观看：

- 普通用户可以浏览已发布视频。
- 播放地址不在列表接口批量下发。
- 前端按视频调用 `/api/videos/{id}/play` 获取播放地址。
- 支持 MP4 和 HLS。
- 未来可扩展短时播放 URL、播放 Token、防盗链、加密播放和播放器 SDK。

### 8.2 当前 word-backend

当前项目主要面向后台预览：

- `/api/videos/{id}/access` 返回预览地址。
- 只有 `READY` 且有 `mediaUrl` 的视频可预览。
- 后台页面直接用原生 `<video src={url}>` 播放。
- 当前没有面向学生端的视频播放列表、发布状态和播放鉴权策略。

因此，当前项目的视频播放需求比 VidFlow 小很多，更像教学资源后台校验和预览。

## 9. 权限与业务范围差异

### 9.1 VidFlow

VidFlow 权限是平台型 RBAC：

- `ADMIN`
- `VIDEO_ADMIN`
- `VIDEO_REVIEWER`
- `USER`

权限细分到：

- 上传 Token
- 创建视频
- 编辑视频
- 发布/下架
- 删除
- 播放
- 预览未发布视频
- 同步媒资
- 查看审计

普通用户是核心使用者之一，只能看已发布且有权限的视频。

### 9.2 当前 word-backend

当前项目的视频模块面向 `ADMIN` 和 `TEACHER`：

- `ADMIN` 可管理全部视频资源。
- `TEACHER` 可管理自己创建或归属自己的教师资源。
- 普通学生当前没有视频资源访问入口。
- 资源范围分为 `SYSTEM` 和 `TEACHER`。

这更贴合教学后台：管理员维护系统资源，老师维护自己的教学视频。

## 10. 配置与云服务差异

### 10.1 VidFlow

VidFlow 使用火山 VOD，配置关注：

- `AccessKeyId`
- `SecretAccessKey`
- `Region`
- `SpaceName`
- `AppId`
- 回调地址

连接测试主要校验空间可访问、配置可用。

### 10.2 当前 word-backend

当前项目使用腾讯云 VOD，配置关注：

- `SecretId`
- `SecretKey`
- `Region`
- `SubAppId`
- `ProcedureName`
- 默认启用配置

`SubAppId` 是资源归属边界，`ProcedureName` 是后续任务流处理入口。当前文档已经明确建议继续围绕腾讯云 API 3.0、`FileId` 和未来 `TaskId` 建模。

## 11. 可靠性侧重点差异

### 11.1 VidFlow

VidFlow 的可靠性要求更完整：

- 上传会话持久化。
- `sessionId + vid` 幂等绑定。
- 事件回调落库。
- 后台任务表和补偿任务。
- 审计日志。
- 不使用 Redis，PostgreSQL 是事实来源。
- 服务重启后可依靠数据库恢复上传会话、视频状态、回调日志和任务记录。

这些要求服务于“直传 + 普通用户观看 + 平台运营”的完整闭环。

### 11.2 当前 word-backend

当前项目的可靠性更多集中在：

- 视频存储配置加密保存。
- 默认配置必须启用。
- 上传后临时文件清理。
- 上传后查询腾讯云媒资并保存本地记录。
- 可手动同步腾讯云状态。
- 删除本地视频时同步删除腾讯云媒资。

当前还缺少上传会话、事件回调消费、任务状态表、失败补偿和视频审计日志等平台型可靠性设施。

## 12. 对当前项目的启发

如果当前项目只是继续服务“后台教学视频资源管理”，现有模型是合理的，重点应放在：

1. 上传失败提示更清晰。
2. 腾讯云转码处理中状态更可解释。
3. 手动同步能力稳定。
4. `SubAppId` 和默认配置校验更严格。
5. 删除云端媒资失败时的补偿策略更明确。

如果当前项目后续要扩展为“学生端可观看的视频学习模块”，需要从 VidFlow 借鉴以下需求：

1. 增加发布/下架状态，避免 `READY` 直接等于学生可见。
2. 增加播放接口，而不是直接长期保存并暴露 `mediaUrl`。
3. 增加播放策略，例如公开、登录可见、班级可见、教师可见。
4. 增加视频审计，例如播放、删除、同步、状态变更。
5. 增加异步处理表或事件日志，跟踪腾讯云任务结果。
6. 评估是否从服务端中转上传改为浏览器直传，降低后端带宽和磁盘压力。

## 13. 不建议直接照搬的地方

不建议把 VidFlow 的所有平台能力直接搬进当前项目，原因是两者业务边界不同：

- 当前项目不是独立视频网站，视频只是教学资源的一部分。
- 当前权限模型已经围绕管理员、老师、学生和教学资源展开，不需要引入完整 `VIDEO_ADMIN`/`VIDEO_REVIEWER` 角色体系，除非产品上确实要做视频审核工作流。
- 当前腾讯云 VOD 已经有 `SubAppId` 和 `ProcedureName` 建模，不能直接套用火山 VOD 的 `SpaceName`/`AppId` 口径。
- 当前后台上传方式虽然有服务端资源压力，但实现和运维更简单；是否改直传应由文件体积、并发量、服务器带宽和学生端播放规划共同决定。

## 14. 总结

VidFlow 的需求重心是“可靠的视频平台闭环”：直传、会话、发布、播放、审计、回调、补偿。

当前项目的需求重心是“教学后台视频资源管理”：配置腾讯云、上传教学视频、转码到可预览状态、后台同步和删除。

两者最大的需求差异不是云厂商不同，而是业务边界不同：VidFlow 把视频作为核心产品；当前项目把视频作为词汇学习系统中的辅助教学资源。因此，当前项目后续设计应优先补齐与教学场景直接相关的状态同步、播放权限和资源归属能力，再决定是否引入 VidFlow 那套更完整的平台级上传和发布模型。
