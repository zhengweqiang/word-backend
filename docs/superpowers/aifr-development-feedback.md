# AIFR Development Feedback

Date: 2026-06-26

Context: 班级相关需求实现与审计，覆盖班级生命周期、班级辞书、班级群消息流、班级范围学习计划。

## What Helped

- 规则、场景、验收条件分层很适合驱动服务层测试，尤其是权限边界和发布时快照这类容易漏的规则。
- `trace.expected_code` 能快速定位主要服务与仓储，不需要先通读整个业务模块。
- 版本历史把“离班学生继续旧计划”“资源入口不授权”这类边界写清楚，减少了实现时的产品猜测。

## Improvement Notes

- 为每个需求增加机器可更新的 implementation status 字段，例如 `not_started | partial | implemented | verified`，并允许逐条规则标记对应测试。当前需要人工在代码和 AIFR 之间来回核对。
- `trace.expected_tests` 应区分 planned tests 和 implemented tests。部分需求列了未来测试名，但仓库里不存在，审计时容易误判为已覆盖。
- 建议为 `interfaces.apis` 增加 `reuse_existing_endpoint: true` 或 `authorization_source` 字段。群动态资源入口复用词书和视频原访问接口，如果 AIFR 明确写“由哪个现有服务二次校验”，实现会更快。
- 对大需求增加 recommended vertical slices，例如“先做隐式消息流 + 文字，再做词书入口，再做视频入口”。`REQ-ORG-0004` 一次包含消息、词书、视频和权限再校验，拆片字段会降低遗漏率。
- 增加 schema/validator 本地可用性说明。开发中需要依赖手工格式检查；如果仓库内提供固定验证命令，会更容易在每次变更后确认 AIFR 文件没有漂移。
- 对跨需求规则增加反向索引，例如“离班学生”同时影响班级成员、学习计划、辞书可见性、群动态可见性。当前需要人工从多个需求文件拼出完整影响面。
