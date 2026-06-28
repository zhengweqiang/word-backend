# REQ-ORG-0001 Changelog

## 3.1.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-ORG-0001
  from_version: "3.0.0"
  to_version: "3.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增班级转移后未来管理权与历史学习计划归属边界。

change_set:
  added_rules:
    - RULE-011
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-010
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-010
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“班级转移”作为未来管理权变更动作。
  semantic_summary:
    - 班级转移只影响未来班级管理权。
    - 已发布学习计划和历史统计仍归原计划老师。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0002
      - REQ-STUDY-0002
  code_impact:
    level: low
    reason: 当前班级 teacherId 更新不会自动改写 study_plans.teacher_id；需要确保后续归档和转移实现不引入批量回写。
    code_search_hints:
      - ClassroomService#updateClassroom
      - StudyPlanService#ensureCanManageStudyPlan
      - StudyPlanRepository
  test_impact:
    level: medium
    reason: 需要覆盖班级转移后新老师未来管理权与旧计划归属不变。
    recommended_tests:
      - ClassroomServiceTest#transferClassroomShouldPreservePublishedPlanOwnership
      - StudyPlanServiceTest#transferredClassroomNewTeacherCanCreateNewPlanButCannotOwnHistoricalPlan
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 3.0.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-ORG-0001
  from_version: "2.0.0"
  to_version: "3.0.0"
  recommended_bump: major
  breaking_change: true
  reason: 新增班级名称全局唯一约束，改变此前允许重名的代码行为。

change_set:
  added_rules:
    - RULE-010
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-008
    - SCN-009
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-009
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“班级名称”作为全局唯一领域词。
      - 明确班级名称唯一性不按老师或归档状态放宽。
  semantic_summary:
    - 所有班级名称必须全局唯一。
    - 不同老师不能拥有同名班级。
    - 归档班级名称仍占用全局唯一名称，不能被新班级复用。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0002
      - REQ-ORG-0003
      - REQ-STUDY-0002
  code_impact:
    level: high
    reason: 当前代码和数据库未强制班级名称唯一；需要新增服务层冲突校验、数据库唯一约束和可能的数据清理迁移。
    code_search_hints:
      - ClassroomService#createClassroom
      - ClassroomService#updateClassroom
      - ClassroomRepository
      - V10__add_classroom_support.sql
      - add_classroom_name_unique_constraint
  test_impact:
    level: high
    reason: 需要覆盖跨老师重名、归档班级名称复用、更新为重复名称和数据库唯一约束冲突。
    recommended_tests:
      - ClassroomServiceTest#createClassroomShouldRejectDuplicateNameAcrossTeachers
      - ClassroomServiceTest#createClassroomShouldRejectNameUsedByArchivedClassroom
      - ClassroomServiceTest#updateClassroomShouldRejectDuplicateName
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 2.0.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-ORG-0001
  from_version: "1.1.0"
  to_version: "2.0.0"
  recommended_bump: major
  breaking_change: true
  reason: 默认班级删除语义从物理删除改为归档班级，物理删除仅允许空班级或无历史误创建班级。

change_set:
  added_rules:
    - RULE-008
    - RULE-009
  modified_rules:
    - RULE-006
  removed_rules: []
  added_scenarios:
    - SCN-006
    - SCN-007
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-007
    - AC-008
  modified_acceptance_criteria:
    - AC-006
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“归档班级”“物理删除班级”和“历史班级结果”边界词。
  semantic_summary:
    - 默认班级删除行为改为归档。
    - 归档班级保留历史学习计划、记录、进度、统计和班级群消息流。
    - 归档班级不得承接新增成员、资源分配、新学习计划或当前消息流访问。
    - 物理删除仅允许空班级或无历史误创建班级。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0002
      - REQ-ORG-0003
      - REQ-ORG-0004
      - REQ-STUDY-0002
  code_impact:
    level: high
    reason: 当前代码物理删除班级并依赖外键级联；需要新增归档状态、归档过滤和受限物理删除校验。
    code_search_hints:
      - ClassroomService#deleteClassroom
      - ClassroomService#addStudentToClassroom
      - ClassroomDictionaryAssignmentService#assignDictionariesToClassroom
      - StudyPlanService#createStudyPlan
      - ClassroomGroupFeedService
      - V10__add_classroom_support.sql
  test_impact:
    level: high
    reason: 删除语义变化影响班级成员、辞书分配、学习计划和班级群消息流的权限边界。
    recommended_tests:
      - ClassroomServiceTest#deleteClassroomWithHistoryShouldArchive
      - ClassroomServiceTest#deleteEmptyMistakenClassroomShouldPhysicallyDelete
      - ClassroomServiceTest#archivedClassroomShouldRejectNewMembersAndAssignments
      - StudyPlanServiceTest#createStudyPlanShouldRejectArchivedClassroom
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 1.1.0

```yaml
version_update:
  requirement_id: REQ-ORG-0001
  from_version: "1.0.0"
  to_version: "1.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增班级 CRUD、成员维护、分页、数据约束和级联删除的明确规则，并将原合并需求拆分为独立关联需求。

change_set:
  added_rules:
    - RULE-005
    - RULE-006
    - RULE-007
  modified_rules:
    - RULE-001
    - RULE-002
    - RULE-003
    - RULE-004
  removed_rules: []
  added_scenarios:
    - SCN-003
    - SCN-004
    - SCN-005
  modified_scenarios:
    - SCN-001
    - SCN-002
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-004
    - AC-005
    - AC-006
  modified_acceptance_criteria:
    - AC-001
    - AC-002
    - AC-003
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules:
      - RULE-001
      - RULE-002
    clarified_scenarios:
      - SCN-001
      - SCN-002
    clarified_acceptance_criteria:
      - AC-001
    terminology_changes:
      - 将原“班级与师生关系管理”拆分为班级基础管理、师生责任范围、班级辞书分配和班级学习计划范围。
  semantic_summary:
    - 班级基础管理需求现在只覆盖班级 CRUD、成员维护和班级响应。
    - 师生责任范围、班级辞书分配和学习计划范围迁出为独立需求。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0002
      - REQ-ORG-0003
      - REQ-STUDY-0002
      - REQ-DICT-0001
  code_impact:
    level: medium
    reason: 文档反映现有代码行为，未要求实现变更。
    code_search_hints:
      - ClassroomService
      - ClassroomController
      - ClassroomMemberRepository
      - V10__add_classroom_support.sql
  test_impact:
    level: medium
    reason: 现有后端缺少直接覆盖 ClassroomService 的单元测试。
    recommended_tests:
      - ClassroomServiceTest#createClassroomShouldResolveTeacherByRole
      - ClassroomServiceTest#addStudentShouldBeIdempotent
      - ClassroomServiceTest#teacherCannotManageOtherTeacherClassroom
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```
