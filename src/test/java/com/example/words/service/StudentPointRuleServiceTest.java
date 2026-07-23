package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.StudentPointRuleCreateRequest;
import com.example.words.dto.StudentPointRuleUpdateRequest;
import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.AppUser;
import com.example.words.model.PointEventStatus;
import com.example.words.model.PointSourceType;
import com.example.words.model.StudentPointRule;
import com.example.words.model.StudentPointRuleAudit;
import com.example.words.model.UserRole;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleAuditRepository;
import com.example.words.repository.StudentPointRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StudentPointRuleServiceTest {

    @Mock
    private StudentPointRuleRepository ruleRepository;

    @Mock
    private StudentPointEventRepository eventRepository;

    @Mock
    private StudentPointRuleAuditRepository auditRepository;

    private StudentPointRuleService service;

    @BeforeEach
    void setUp() {
        service = new StudentPointRuleService(ruleRepository, eventRepository, auditRepository, new ObjectMapper());
    }

    @Test
    void createNormalizesStableCodeAndDefaultsScopeAndEnabled() {
        when(ruleRepository.findByCode("DAILY_TASK_COMPLETED")).thenReturn(Optional.empty());
        when(ruleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentPointRule rule = invocation.getArgument(0);
            rule.setId(4L);
            return rule;
        });

        StudentPointRule saved = service.create(admin(), new StudentPointRuleCreateRequest(
                " daily_task_completed ", "每日任务", null, PointSourceType.STUDY_TASK,
                10, null, null, null, "创建默认规则"
        ));

        assertEquals("DAILY_TASK_COMPLETED", saved.getCode());
        assertEquals("GLOBAL", saved.getScopeType());
        assertTrue(saved.getEnabled());
        ArgumentCaptor<StudentPointRuleAudit> audit = ArgumentCaptor.forClass(StudentPointRuleAudit.class);
        verify(auditRepository).save(audit.capture());
        assertEquals(4L, audit.getValue().getRuleId());
        assertEquals("CREATE", audit.getValue().getAction());
        assertEquals(9L, audit.getValue().getOperatorId());
        assertEquals("创建默认规则", audit.getValue().getReason());
        assertTrue(audit.getValue().getAfterSnapshot().contains("DAILY_TASK_COMPLETED"));
    }

    @Test
    void updateRejectsRuleWithUnfinishedEvents() {
        StudentPointRule rule = StudentPointRule.create(
                "DAILY_TASK_COMPLETED", "每日任务", PointSourceType.STUDY_TASK, 10);
        rule.setId(3L);
        when(ruleRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(rule));
        when(eventRepository.existsByRuleCodeAndStatusIn(
                org.mockito.ArgumentMatchers.eq("DAILY_TASK_COMPLETED"), any())).thenReturn(true);

        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, () ->
                service.update(admin(), 3L, new StudentPointRuleUpdateRequest(
                        "新名称", null, PointSourceType.STUDY_TASK, 20, "GLOBAL", null, false, "调整规则")));

        assertEquals("POINT_RULE_HAS_UNFINISHED_EVENTS", failure.getCode());
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void updateChangesConfigurationButCannotChangeCode() {
        StudentPointRule rule = StudentPointRule.create(
                "IMMUTABLE_CODE", "原名称", PointSourceType.STUDY_TASK, 10);
        rule.setId(3L);
        when(ruleRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(rule));
        when(eventRepository.existsByRuleCodeAndStatusIn(
                org.mockito.ArgumentMatchers.eq("IMMUTABLE_CODE"), any())).thenReturn(false);
        when(ruleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudentPointRule updated = service.update(admin(), 3L, new StudentPointRuleUpdateRequest(
                "新名称", "新说明", PointSourceType.STUDY_RECORD, 2, "global", null, false, "调整规则"));

        assertEquals("IMMUTABLE_CODE", updated.getCode());
        assertEquals("新名称", updated.getName());
        assertEquals(2, updated.getBasePoints());
        assertFalse(updated.getEnabled());
    }

    @Test
    void zeroPointRuleIsRejected() {
        StudentPointOperationException failure = assertThrows(StudentPointOperationException.class, () ->
                service.create(admin(), new StudentPointRuleCreateRequest(
                        "ZERO", "无效", null, PointSourceType.STUDY_TASK, 0,
                        null, null, true, "测试")));
        assertEquals("INVALID_POINT_RULE_POINTS", failure.getCode());
    }

    @Test
    void createAllowsBlankReasonAndWritesDefaultAuditReason() {
        when(ruleRepository.findByCode("OPTIONAL_REASON")).thenReturn(Optional.empty());
        when(ruleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentPointRule rule = invocation.getArgument(0);
            rule.setId(6L);
            return rule;
        });

        service.create(admin(), new StudentPointRuleCreateRequest(
                "OPTIONAL_REASON", "可选原因", null, PointSourceType.STUDY_TASK, 5,
                null, null, true, ""
        ));

        ArgumentCaptor<StudentPointRuleAudit> audit = ArgumentCaptor.forClass(StudentPointRuleAudit.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("未填写变更原因", audit.getValue().getReason());
    }

    @Test
    void unrelatedDatabaseConstraintIsNotMisreportedAsDuplicateCode() {
        when(ruleRepository.findByCode("RULE")).thenReturn(Optional.empty());
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException("different constraint");
        when(ruleRepository.saveAndFlush(any())).thenThrow(databaseFailure);

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class, () ->
                service.create(admin(), new StudentPointRuleCreateRequest(
                        "RULE", "规则", null, PointSourceType.STUDY_TASK, 5,
                        null, null, true, "测试")));

        assertEquals(databaseFailure, failure);
    }

    private AppUser admin() {
        AppUser admin = new AppUser();
        admin.setId(9L);
        admin.setRole(UserRole.ADMIN);
        return admin;
    }
}
