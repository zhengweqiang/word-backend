# REQ-ORG-0003 Changelog

## 1.1.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-ORG-0003
  from_version: "1.0.0"
  to_version: "1.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增移除班级辞书后的当前可见性和历史学习结果保留边界。

change_set:
  added_rules:
    - RULE-010
    - RULE-011
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-006
    - SCN-007
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-007
    - AC-008
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“当前辞书可见性”和“历史学习结果”边界词。
  semantic_summary:
    - 移除班级辞书后，该辞书不再通过该班级贡献当前可见性。
    - 移除班级辞书不得删除已发布学习计划、学习记录、学习进度或既有学生单词记忆。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-STUDY-0002
      - REQ-STUDY-0001
      - REQ-MEM-0001
      - REQ-ORG-0004
  code_impact:
    level: low
    reason: 当前移除操作已删除班级辞书分配关系且未级联业务历史；需要确保后续实现不增加历史回写。
    code_search_hints:
      - ClassroomDictionaryAssignmentService#removeDictionaryFromClassroom
      - DictionaryService#findVisibleDictionariesForClassrooms
      - StudyPlanService
      - StudentWordMemoryService
  test_impact:
    level: medium
    reason: 需要覆盖移除后当前可见性消失，以及历史学习结果不被删除的回归场景。
    recommended_tests:
      - ClassroomDictionaryAssignmentServiceTest#removeDictionaryShouldNotDeleteHistoricalStudyArtifacts
      - DictionaryServiceTest#removedClassroomDictionaryShouldNotRemainVisibleThroughClassroom
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```
