# REQ-ORG-0004 Changelog

## 1.1.0 - 2026-06-25

```yaml
version_update:
  requirement_id: REQ-ORG-0004
  from_version: "1.0.0"
  to_version: "1.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 统一“班级群消息流”领域词，并新增资源发布不授予权限、打开资源时按当前权限二次校验的规则。

change_set:
  added_rules:
    - RULE-011
    - RULE-012
  modified_rules:
    - RULE-001
    - RULE-002
    - RULE-003
    - RULE-004
    - RULE-005
    - RULE-006
    - RULE-007
    - RULE-008
    - RULE-009
    - RULE-010
  removed_rules: []
  added_scenarios:
    - SCN-007
    - SCN-008
  modified_scenarios:
    - SCN-001
    - SCN-002
    - SCN-003
    - SCN-004
    - SCN-005
    - SCN-006
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-007
  modified_acceptance_criteria:
    - AC-001
    - AC-002
    - AC-003
    - AC-004
    - AC-005
    - AC-006
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules:
      - RULE-001
      - RULE-002
      - RULE-009
    clarified_scenarios:
      - SCN-001
      - SCN-002
      - SCN-003
      - SCN-004
      - SCN-005
      - SCN-006
    clarified_acceptance_criteria:
      - AC-001
      - AC-002
      - AC-005
    terminology_changes:
      - 用“班级群消息流”作为原“班级群”的标准领域词。
      - 将词书发布和视频分享明确为资源消息入口，而不是资源授权。
  semantic_summary:
    - 班级不再被表述为拥有独立班级群实体，而是自动拥有班级群消息流。
    - 资源发布只创建消息入口，不改变词书或视频资源权限。
    - 学生打开词书或视频时必须复用当前资源权限校验，历史消息入口不得绕过权限变化。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0001
      - REQ-ORG-0003
      - REQ-DICT-0001
      - REQ-AUTH-0001
  code_impact:
    level: medium
    reason: 新增班级群消息流接口和持久化对象，并要求资源入口打开时复用现有词书和视频权限校验。
    code_search_hints:
      - ClassroomGroupFeedService
      - ClassroomGroupFeedController
      - ClassroomGroupFeedMessageRepository
      - DictionaryService#findByIdVisibleToUser
      - VideoAssetService#getStudentPlayback
      - AccessControlService
  test_impact:
    level: high
    reason: 权限边界跨班级成员、词书分配、视频发布状态和资源打开二次校验，需要覆盖成功、越权和权限变化场景。
    recommended_tests:
      - ClassroomGroupFeedServiceTest#studentCanPostTextMessageWhenCurrentClassroomMember
      - ClassroomGroupFeedServiceTest#removedStudentCannotReadHistoricalFeedMessages
      - ClassroomGroupFeedResourceAccessTest#dictionaryEntryDoesNotGrantAccessAfterDictionaryRemoved
      - ClassroomGroupFeedResourceAccessTest#videoEntryDoesNotGrantPlaybackAfterVideoUnpublished
  review_impact:
    recommended_reviewers:
      - backend
      - frontend
      - qa
      - product
```
