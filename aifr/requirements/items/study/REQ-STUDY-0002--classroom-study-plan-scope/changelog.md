# REQ-STUDY-0002 Changelog

## 1.5.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-STUDY-0002
  from_version: "1.4.0"
  to_version: "1.5.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增离班计划学生在计划历史报表和当前班级统计中的统计口径。

change_set:
  added_rules:
    - RULE-018
    - RULE-019
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-014
    - SCN-015
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-016
    - AC-017
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“计划历史报表”和“当前班级统计”两个统计口径。
  semantic_summary:
    - 离班计划学生仍出现在旧计划的计划历史报表中。
    - 计划历史报表展示离班学生离班后的旧计划继续完成情况。
    - 离班计划学生不计入当前班级统计。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0001
      - REQ-STUDY-0001
  code_impact:
    level: medium
    reason: 报表查询需要区分计划学生快照和当前班级成员两个统计口径。
    code_search_hints:
      - StudyPlanService#getOverview
      - StudyPlanService#listPlanStudents
      - ClassroomMemberRepository
      - StudentStudyPlanRepository
  test_impact:
    level: high
    reason: 需要覆盖计划历史报表保留离班学生、当前班级统计排除离班学生两个相反口径。
    recommended_tests:
      - StudyPlanServiceTest#planHistoryReportShouldIncludeTransferredOutStudentProgress
      - StudyPlanServiceTest#currentClassroomStatisticsShouldExcludeTransferredOutStudent
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 1.4.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-STUDY-0002
  from_version: "1.3.0"
  to_version: "1.4.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增学生离班后继续完成已分配旧计划、不接收未来班级计划的边界。

change_set:
  added_rules:
    - RULE-016
    - RULE-017
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-012
    - SCN-013
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-014
    - AC-015
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“离班计划学生”和“已分配旧计划”边界词。
  semantic_summary:
    - 学生离班后仍可继续完成离班前已分配给自己的个人学习计划。
    - 学生离班后不再自动获得该班级未来发布、追加或创建的学习计划。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0001
      - REQ-ORG-0002
      - REQ-ORG-0004
  code_impact:
    level: medium
    reason: 需要确保学生访问个人计划按 StudentStudyPlan 所有权判断，而不是重新要求当前班级成员身份；未来计划发布仍按当前成员快照生成。
    code_search_hints:
      - StudyPlanService#ensureStudentOwnsPlan
      - StudyPlanService#publishStudyPlan
      - ClassroomService#removeStudentFromClassroom
      - StudentStudyPlanRepository
  test_impact:
    level: high
    reason: 需要同时覆盖离班后旧计划继续学习和未来班级计划不再分配两个方向。
    recommended_tests:
      - StudyPlanServiceTest#removedClassroomStudentShouldContinueAssignedLegacyPlan
      - StudyPlanServiceTest#removedClassroomStudentShouldNotReceiveFutureClassroomPlan
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 1.3.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-STUDY-0002
  from_version: "1.2.0"
  to_version: "1.3.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增班级转移后既有学习计划归属不变、新老师只管理未来计划的边界。

change_set:
  added_rules:
    - RULE-014
    - RULE-015
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-010
    - SCN-011
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-012
    - AC-013
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“学习计划归属老师”和“历史学习计划统计”边界词。
  semantic_summary:
    - 班级转移不改变既有已发布学习计划的归属老师。
    - 班级转移不改变历史统计归属。
    - 新负责老师可以创建未来学习计划，但不会自动接管旧计划。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0001
      - REQ-ORG-0002
  code_impact:
    level: low
    reason: 当前学习计划有独立 teacherId，班级转移不会自动回写；需防止未来实现误把计划归属绑定为当前班级老师。
    code_search_hints:
      - StudyPlanService#createStudyPlan
      - StudyPlanService#ensureCanManageStudyPlan
      - ClassroomService#updateClassroom
      - StudyPlanRepository
  test_impact:
    level: medium
    reason: 需要覆盖班级转移后旧计划归属不变、新老师只能创建未来计划的边界。
    recommended_tests:
      - StudyPlanServiceTest#classroomTransferShouldNotChangePublishedPlanTeacher
      - StudyPlanServiceTest#newClassroomTeacherShouldCreateFuturePlanWithoutOwningHistoricalPlan
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 1.2.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-STUDY-0002
  from_version: "1.1.0"
  to_version: "1.2.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增班级辞书移除后已发布学习计划继续有效、草稿发布前重新校验的边界。

change_set:
  added_rules:
    - RULE-012
    - RULE-013
  modified_rules: []
  removed_rules: []
  added_scenarios:
    - SCN-008
    - SCN-009
  modified_scenarios: []
  removed_scenarios: []
  added_acceptance_criteria:
    - AC-010
    - AC-011
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“已发布学习计划资源连续性”和“发布前辞书重校验”边界词。
  semantic_summary:
    - 班级辞书分配在计划发布后移除，不会让已发布学习计划失效。
    - 已发布学习计划保留既有个人计划、任务、记录和进度。
    - 草稿或未发布计划在发布前必须按当前班级辞书交集重新校验。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0003
      - REQ-STUDY-0001
      - REQ-MEM-0001
  code_impact:
    level: medium
    reason: 已发布计划保留历史与当前代码一致；草稿发布前重新校验可能需要在发布流程增加当前班级辞书交集检查。
    code_search_hints:
      - StudyPlanService#publishStudyPlan
      - StudyPlanService#ensureDictionaryAvailableForClassrooms
      - ClassroomDictionaryAssignmentService#intersectAssignedDictionaryIdsForClassrooms
      - DictionaryService#findVisibleDictionariesForClassrooms
  test_impact:
    level: high
    reason: 发布流程必须区分已发布历史保留和未发布计划当前权限校验。
    recommended_tests:
      - StudyPlanServiceTest#publishedPlanShouldRemainValidAfterClassroomDictionaryRemoved
      - StudyPlanServiceTest#draftPlanPublishShouldRevalidateDictionaryClassroomIntersection
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```

## 1.1.0 - 2026-06-26

```yaml
version_update:
  requirement_id: REQ-STUDY-0002
  from_version: "1.0.0"
  to_version: "1.1.0"
  recommended_bump: minor
  breaking_change: false
  reason: 新增已发布学习计划的学生快照、显式追加和离班历史保留边界，不改变当前代码的默认发布行为。

change_set:
  added_rules:
    - RULE-009
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
    - AC-009
  modified_acceptance_criteria: []
  removed_acceptance_criteria: []
  textual_changes:
    clarified_rules: []
    clarified_scenarios: []
    clarified_acceptance_criteria: []
    terminology_changes:
      - 新增“学习计划学生快照”作为发布后学生范围的标准领域词。
  semantic_summary:
    - 已发布学习计划采用发布时学生快照。
    - 班级新增学生不会自动加入既有已发布学习计划。
    - 新学生参与既有计划需要显式追加。
    - 学生离开班级后保留已存在的个人学习计划和学习历史。

impact_analysis:
  requirement_impact:
    related_requirements:
      - REQ-ORG-0001
      - REQ-ORG-0002
      - REQ-ORG-0003
      - REQ-STUDY-0001
  code_impact:
    level: low
    reason: 当前发布流程已按发布时班级成员创建个人计划；后续若实现显式追加，需要新增入口但不要求改变现有发布流程。
    code_search_hints:
      - StudyPlanService#publishStudyPlan
      - StudyPlanService#getOrCreateStudentStudyPlan
      - ClassroomService#addStudentToClassroom
      - ClassroomService#removeStudentFromClassroom
      - StudentStudyPlanRepository
  test_impact:
    level: medium
    reason: 需要补充班级成员变更后不隐式改写已发布计划，以及离班保留历史的回归测试。
    recommended_tests:
      - StudyPlanServiceTest#classroomMemberAddedAfterPublishShouldNotCreateStudentStudyPlan
      - StudyPlanServiceTest#removingClassroomMemberShouldPreserveExistingStudentStudyPlanHistory
  review_impact:
    recommended_reviewers:
      - backend
      - qa
      - product
```
