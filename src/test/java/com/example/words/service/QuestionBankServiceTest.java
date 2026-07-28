package com.example.words.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import com.example.words.dto.CopyQuestionRequest;
import com.example.words.dto.CreateQuestionRequest;
import com.example.words.dto.QuestionBankItemResponse;
import com.example.words.dto.QuestionBankSearchRequest;
import com.example.words.dto.UpdateQuestionRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.QuestionBankItemRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    private static final LocalDateTime FLUSHED_AT = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Mock
    private QuestionBankItemRepository questionRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    @Mock
    private DictionaryWordRepository dictionaryWordRepository;

    private QuestionBankService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ExamPaperAccessService accessService = new ExamPaperAccessService(
                mock(TeacherStudentService.class), mock(ClassroomService.class));
        service = new QuestionBankService(
                questionRepository,
                dictionaryRepository,
                metaWordRepository,
                dictionaryWordRepository,
                accessService,
                new ExamPaperAnswerNormalizer(),
                objectMapper);
        lenient().when(questionRepository.save(any(QuestionBankItem.class))).thenAnswer(invocation -> {
            QuestionBankItem question = invocation.getArgument(0);
            if (question.getId() == null) {
                question.setId(100L);
            }
            return question;
        });
        lenient().when(questionRepository.saveAndFlush(any(QuestionBankItem.class))).thenAnswer(invocation -> {
            QuestionBankItem question = invocation.getArgument(0);
            if (question.getId() == null) {
                question.setId(100L);
            }
            if (question.getCreatedAt() == null) {
                question.setCreatedAt(FLUSHED_AT);
            }
            question.setUpdatedAt(FLUSHED_AT);
            return question;
        });
    }

    @Test
    void createDefaultsToDraftAndNormalizesChoiceContent() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(" a ", "  First  ");
        options.put("b", "Second");

        QuestionBankItemResponse response = service.create(
                request(QuestionType.SINGLE_CHOICE, options, List.of(" a "), null),
                user(7L, UserRole.TEACHER));

        assertEquals(QuestionBankItemStatus.DRAFT, response.getStatus());
        assertEquals(Map.of("A", "First", "B", "Second"), response.getOptions());
        assertEquals(List.of("A"), response.getAcceptedAnswers());
        assertEquals(7L, response.getCreatedByUserId());
        assertEquals(7L, response.getLastModifiedByUserId());
    }

    @Test
    void createCanPublishAnActiveQuestion() {
        QuestionBankItemResponse response = service.create(
                request(QuestionType.FILL_IN_BLANK, Map.of(), List.of("  Answer ", "answer"),
                        QuestionBankItemStatus.ACTIVE),
                user(7L, UserRole.TEACHER));

        assertEquals(QuestionBankItemStatus.ACTIVE, response.getStatus());
        assertEquals(List.of("answer"), response.getAcceptedAnswers());
    }

    @Test
    void validateAndNormalizeForImportUsesCanonicalQuestionRules() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put(" b ", " Second ");
        options.put("a", " First ");
        CreateQuestionRequest request = request(
                QuestionType.MULTIPLE_CHOICE,
                options,
                List.of(" b ", "A", "a"),
                QuestionBankItemStatus.ACTIVE);
        request.setStem("  Imported stem  ");

        QuestionBankService.ValidatedQuestion normalized = service.validateAndNormalize(request);

        assertEquals("Imported stem", normalized.stem());
        assertEquals(Map.of("A", "First", "B", "Second"), normalized.options());
        assertEquals(List.of("A", "B"), normalized.acceptedAnswers());
    }

    @Test
    void createImportedAlwaysCreatesActiveQuestionWithImportTrace() {
        QuestionBankItemResponse response = service.createImported(
                request(QuestionType.FILL_IN_BLANK, Map.of(), List.of(" answer "), null),
                40L,
                user(7L, UserRole.TEACHER));

        assertEquals(QuestionBankItemStatus.ACTIVE, response.getStatus());
        assertEquals(7L, response.getCreatedByUserId());
        assertEquals(7L, response.getImportedByUserId());
        assertEquals(7L, response.getLastModifiedByUserId());
        assertEquals(40L, response.getImportBatchId());
    }

    @Test
    void createResponseUsesFlushedAuditTimestamps() {
        QuestionBankItemResponse response = service.create(
                request(QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null),
                user(7L, UserRole.TEACHER));

        assertEquals(FLUSHED_AT, response.getCreatedAt());
        assertEquals(FLUSHED_AT, response.getUpdatedAt());
        verify(questionRepository).saveAndFlush(any(QuestionBankItem.class));
    }

    @Test
    void singleChoiceRequiresTwoToFourOptionsAndOneExistingAnswer() {
        AppUser teacher = user(7L, UserRole.TEACHER);

        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.SINGLE_CHOICE, Map.of("A", "one"), List.of("A"), null), teacher));
        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.SINGLE_CHOICE,
                        Map.of("A", "one", "B", "two"), List.of("C"), null), teacher));
    }

    @Test
    void multipleChoiceRequiresAtLeastTwoDistinctExistingAnswers() {
        AppUser teacher = user(7L, UserRole.TEACHER);

        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.MULTIPLE_CHOICE,
                        Map.of("A", "one", "B", "two"), List.of("A", " a "), null), teacher));
        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.MULTIPLE_CHOICE,
                        Map.of("A", "one", "B", "two"), List.of("A", "C"), null), teacher));
    }

    @Test
    void fillInBlankRejectsOptionsAndRequiresANonBlankAnswer() {
        AppUser teacher = user(7L, UserRole.TEACHER);

        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.FILL_IN_BLANK, Map.of("A", "one"), List.of("answer"), null), teacher));
        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.FILL_IN_BLANK, Map.of(), List.of(" "), null), teacher));
    }

    @Test
    void normalizedOptionKeysMustBeUniqueAndOptionTextMustNotBeBlank() {
        AppUser teacher = user(7L, UserRole.TEACHER);
        Map<String, String> duplicateKeys = new LinkedHashMap<>();
        duplicateKeys.put("A", "one");
        duplicateKeys.put(" a ", "two");

        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.SINGLE_CHOICE, duplicateKeys, List.of("A"), null), teacher));
        assertThrows(BadRequestException.class, () -> service.create(
                request(QuestionType.SINGLE_CHOICE,
                        Map.of("A", " ", "B", "two"), List.of("B"), null), teacher));
    }

    @Test
    void directServiceCallsStillRejectInvalidRequiredFieldsAndArchivedStatus() {
        AppUser teacher = user(7L, UserRole.TEACHER);
        CreateQuestionRequest invalid = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), QuestionBankItemStatus.ARCHIVED);
        invalid.setStem(" ");
        invalid.setDefaultScore(BigDecimal.ZERO);

        assertThrows(BadRequestException.class, () -> service.create(invalid, teacher));
        verify(questionRepository, never()).save(any());
        verify(questionRepository, never()).saveAndFlush(any());
    }

    @Test
    void directServiceCallsRejectScoresOutsideNumericNineteenTwo() {
        AppUser teacher = user(7L, UserRole.TEACHER);
        CreateQuestionRequest excessiveScale = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null);
        excessiveScale.setDefaultScore(new BigDecimal("1.001"));
        CreateQuestionRequest excessiveIntegerDigits = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null);
        excessiveIntegerDigits.setDefaultScore(new BigDecimal("100000000000000000.00"));

        assertThrows(BadRequestException.class, () -> service.create(excessiveScale, teacher));
        assertThrows(BadRequestException.class, () -> service.create(excessiveIntegerDigits, teacher));
    }

    @Test
    void teacherCanEditOwnQuestionWithoutChangingOwnership() {
        QuestionBankItem existing = persistedQuestion(55L, 7L, null, QuestionBankItemStatus.DRAFT);
        when(questionRepository.findById(55L)).thenReturn(Optional.of(existing));
        UpdateQuestionRequest update = updateRequest(
                QuestionType.FILL_IN_BLANK, " Updated stem ", Map.of(), List.of(" Value "),
                QuestionBankItemStatus.ACTIVE);

        QuestionBankItemResponse response = service.update(55L, update, user(7L, UserRole.TEACHER));

        assertEquals("Updated stem", response.getStem());
        assertEquals(QuestionBankItemStatus.ACTIVE, response.getStatus());
        assertEquals(7L, response.getCreatedByUserId());
        assertEquals(7L, response.getLastModifiedByUserId());
    }

    @Test
    void updateResponseUsesFlushedAuditTimestamp() {
        QuestionBankItem existing = persistedQuestion(55L, 7L, null, QuestionBankItemStatus.DRAFT);
        when(questionRepository.findById(55L)).thenReturn(Optional.of(existing));

        QuestionBankItemResponse response = service.update(
                55L,
                updateRequest(QuestionType.FILL_IN_BLANK, "Updated", Map.of(), List.of("answer"),
                        QuestionBankItemStatus.ACTIVE),
                user(7L, UserRole.TEACHER));

        assertEquals(FLUSHED_AT, response.getUpdatedAt());
        verify(questionRepository).saveAndFlush(existing);
    }

    @Test
    void teacherCannotEditAnotherTeachersQuestion() {
        when(questionRepository.findById(55L))
                .thenReturn(Optional.of(persistedQuestion(55L, 8L, null, QuestionBankItemStatus.ACTIVE)));

        assertThrows(AccessDeniedException.class, () -> service.update(
                55L,
                updateRequest(QuestionType.FILL_IN_BLANK, "Changed", Map.of(), List.of("answer"),
                        QuestionBankItemStatus.ACTIVE),
                user(7L, UserRole.TEACHER)));
    }

    @Test
    void archivedQuestionCannotBeUpdated() {
        when(questionRepository.findById(55L))
                .thenReturn(Optional.of(persistedQuestion(55L, 7L, null, QuestionBankItemStatus.ARCHIVED)));

        assertThrows(BadRequestException.class, () -> service.update(
                55L,
                updateRequest(QuestionType.FILL_IN_BLANK, "Changed", Map.of(), List.of("answer"),
                        QuestionBankItemStatus.DRAFT),
                user(7L, UserRole.TEACHER)));
    }

    @Test
    void teacherCanCopyAnotherTeachersActiveQuestionAsIndependentDraft() throws Exception {
        QuestionBankItem source = persistedQuestion(55L, 8L, 8L, QuestionBankItemStatus.ACTIVE);
        source.setSourceQuestionId(40L);
        source.setImportBatchId(99L);
        source.setDictionaryId(10L);
        source.setMetaWordId(20L);
        when(questionRepository.findById(55L)).thenReturn(Optional.of(source));
        when(dictionaryRepository.existsById(10L)).thenReturn(true);
        when(metaWordRepository.existsById(20L)).thenReturn(true);
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 20L)).thenReturn(true);

        QuestionBankItemResponse response = service.copy(
                55L, new CopyQuestionRequest(" Copied stem "), user(7L, UserRole.TEACHER));

        ArgumentCaptor<QuestionBankItem> captor = ArgumentCaptor.forClass(QuestionBankItem.class);
        verify(questionRepository).saveAndFlush(captor.capture());
        QuestionBankItem copied = captor.getValue();
        assertEquals("Copied stem", response.getStem());
        assertEquals(QuestionBankItemStatus.DRAFT, copied.getStatus());
        assertEquals(55L, copied.getSourceQuestionId());
        assertEquals(7L, copied.getCreatedByUserId());
        assertNull(copied.getImportedByUserId());
        assertNull(copied.getImportBatchId());
        assertEquals(10L, copied.getDictionaryId());
        assertEquals(20L, copied.getMetaWordId());
        assertEquals(objectMapper.readTree(source.getOptionsJson()), objectMapper.readTree(copied.getOptionsJson()));
        verify(dictionaryWordRepository).existsByDictionaryIdAndMetaWordId(10L, 20L);
    }

    @Test
    void copyAlwaysCreatesDraft() {
        QuestionBankItem source = persistedQuestion(55L, 8L, null, QuestionBankItemStatus.ACTIVE);
        when(questionRepository.findById(55L)).thenReturn(Optional.of(source));

        QuestionBankItemResponse response = service.copy(
                55L,
                new CopyQuestionRequest(null),
                user(7L, UserRole.TEACHER));

        assertEquals(QuestionBankItemStatus.DRAFT, response.getStatus());
    }

    @Test
    void copyResponseUsesFlushedAuditTimestamps() {
        QuestionBankItem source = persistedQuestion(55L, 8L, null, QuestionBankItemStatus.ACTIVE);
        when(questionRepository.findById(55L)).thenReturn(Optional.of(source));

        QuestionBankItemResponse response = service.copy(55L, null, user(7L, UserRole.TEACHER));

        assertEquals(FLUSHED_AT, response.getCreatedAt());
        assertEquals(FLUSHED_AT, response.getUpdatedAt());
        verify(questionRepository).saveAndFlush(any(QuestionBankItem.class));
    }

    @Test
    void archivedQuestionCannotBeCopied() {
        when(questionRepository.findById(55L))
                .thenReturn(Optional.of(persistedQuestion(55L, 8L, null, QuestionBankItemStatus.ARCHIVED)));

        assertThrows(AccessDeniedException.class, () -> service.copy(
                55L, new CopyQuestionRequest(null), user(7L, UserRole.TEACHER)));
    }

    @Test
    void ownerAndAdminCanArchiveButUnrelatedTeacherCannot() {
        QuestionBankItem owned = persistedQuestion(55L, 7L, null, QuestionBankItemStatus.ACTIVE);
        when(questionRepository.findById(55L)).thenReturn(Optional.of(owned));
        service.archive(55L, user(7L, UserRole.TEACHER));
        assertEquals(QuestionBankItemStatus.ARCHIVED, owned.getStatus());
        assertNotNull(owned.getArchivedAt());

        QuestionBankItem another = persistedQuestion(56L, 8L, null, QuestionBankItemStatus.ACTIVE);
        when(questionRepository.findById(56L)).thenReturn(Optional.of(another));
        service.archive(56L, user(1L, UserRole.ADMIN));
        assertEquals(QuestionBankItemStatus.ARCHIVED, another.getStatus());

        QuestionBankItem denied = persistedQuestion(57L, 8L, null, QuestionBankItemStatus.ACTIVE);
        when(questionRepository.findById(57L)).thenReturn(Optional.of(denied));
        assertThrows(AccessDeniedException.class,
                () -> service.archive(57L, user(7L, UserRole.TEACHER)));
    }

    @Test
    void everyServiceEntryPointExplicitlyDeniesStudents() {
        AppUser student = user(20L, UserRole.STUDENT);

        assertThrows(AccessDeniedException.class,
                () -> service.create(request(QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null), student));
        assertThrows(AccessDeniedException.class,
                () -> service.update(1L, updateRequest(QuestionType.FILL_IN_BLANK, "Stem", Map.of(),
                        List.of("answer"), QuestionBankItemStatus.DRAFT), student));
        assertThrows(AccessDeniedException.class,
                () -> service.copy(1L, new CopyQuestionRequest(null), student));
        assertThrows(AccessDeniedException.class, () -> service.archive(1L, student));
        assertThrows(AccessDeniedException.class, () -> service.search(new QuestionBankSearchRequest(), student));
        verify(questionRepository, never()).findById(anyLong());
    }

    @Test
    void dictionaryAndMetaWordTraceMustExistAndBelongTogether() {
        CreateQuestionRequest request = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null);
        request.setDictionaryId(10L);
        request.setMetaWordId(20L);
        when(dictionaryRepository.existsById(10L)).thenReturn(true);
        when(metaWordRepository.existsById(20L)).thenReturn(true);
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 20L)).thenReturn(true);

        QuestionBankItemResponse response = service.create(request, user(7L, UserRole.TEACHER));

        assertEquals(10L, response.getDictionaryId());
        assertEquals(20L, response.getMetaWordId());
        verify(dictionaryWordRepository).existsByDictionaryIdAndMetaWordId(10L, 20L);
    }

    @Test
    void missingTraceOrDictionaryWordAssociationIsRejected() {
        CreateQuestionRequest request = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null);
        request.setDictionaryId(10L);
        request.setMetaWordId(20L);
        when(dictionaryRepository.existsById(10L)).thenReturn(true);
        when(metaWordRepository.existsById(20L)).thenReturn(true);
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 20L)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.create(request, user(7L, UserRole.TEACHER)));

        CreateQuestionRequest missingDictionary = request(
                QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null);
        missingDictionary.setDictionaryId(11L);
        when(dictionaryRepository.existsById(11L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(missingDictionary, user(7L, UserRole.TEACHER)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void searchAppliesVisibilityFiltersAndStablePaging() {
        QuestionBankItem item = persistedQuestion(55L, 8L, null, QuestionBankItemStatus.ACTIVE);
        when(questionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(2, 15), 31));
        QuestionBankSearchRequest request = new QuestionBankSearchRequest();
        request.setKeyword("capital");
        request.setQuestionType(QuestionType.SINGLE_CHOICE);
        request.setStatus(QuestionBankItemStatus.ACTIVE);
        request.setTag("geography");
        request.setDictionaryId(10L);
        request.setMetaWordId(20L);
        request.setCreatorId(8L);
        request.setPage(2);
        request.setSize(15);

        Page<QuestionBankItemResponse> result = service.search(request, user(7L, UserRole.TEACHER));

        ArgumentCaptor<Specification<QuestionBankItem>> specificationCaptor =
                ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(questionRepository).findAll(specificationCaptor.capture(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(15, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("updatedAt").getDirection());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
        assertEquals(31, result.getTotalElements());

        Root<QuestionBankItem> root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        specificationCaptor.getValue().toPredicate(root, query, criteriaBuilder);
        Set<String> referencedFields = mockingDetails(root).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("get"))
                .map(invocation -> invocation.getArgument(0, String.class))
                .collect(Collectors.toSet());
        assertTrue(referencedFields.containsAll(Set.of(
                "status", "createdByUserId", "importedByUserId", "stem", "explanation", "questionType", "tags",
                "dictionaryId", "metaWordId")));
        Set<Object> equalityValues = mockingDetails(criteriaBuilder).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("equal"))
                .map(invocation -> invocation.getArgument(1))
                .collect(Collectors.toSet());
        assertTrue(equalityValues.containsAll(Set.of(
                QuestionBankItemStatus.ACTIVE, QuestionType.SINGLE_CHOICE, 7L, 8L, 10L, 20L)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchRejectsMalformedPersistedJson() {
        QuestionBankItem malformed = persistedQuestion(55L, 8L, null, QuestionBankItemStatus.ACTIVE);
        malformed.setOptionsJson("{not-json");
        when(questionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(malformed)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.search(new QuestionBankSearchRequest(), user(7L, UserRole.TEACHER)));

        assertEquals("Failed to deserialize question options", exception.getMessage());
    }

    @Test
    void createWrapsObjectMapperSerializationFailures() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") {
                });
        QuestionBankService failingService = new QuestionBankService(
                questionRepository,
                dictionaryRepository,
                metaWordRepository,
                dictionaryWordRepository,
                mock(ExamPaperAccessService.class),
                new ExamPaperAnswerNormalizer(),
                failingObjectMapper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> failingService.create(
                        request(QuestionType.FILL_IN_BLANK, Map.of(), List.of("answer"), null),
                        user(7L, UserRole.TEACHER)));

        assertEquals("Failed to serialize question options", exception.getMessage());
        verify(questionRepository, never()).saveAndFlush(any());
    }

    private CreateQuestionRequest request(
            QuestionType type,
            Map<String, String> options,
            List<String> acceptedAnswers,
            QuestionBankItemStatus status) {
        return new CreateQuestionRequest(
                type,
                "Question stem",
                options,
                acceptedAnswers,
                new BigDecimal("2.50"),
                3,
                List.of("grammar", "review"),
                "Because",
                null,
                null,
                status);
    }

    private UpdateQuestionRequest updateRequest(
            QuestionType type,
            String stem,
            Map<String, String> options,
            List<String> acceptedAnswers,
            QuestionBankItemStatus status) {
        return new UpdateQuestionRequest(
                type,
                stem,
                options,
                acceptedAnswers,
                new BigDecimal("3.00"),
                4,
                List.of("updated"),
                "Updated explanation",
                null,
                null,
                status);
    }

    private QuestionBankItem persistedQuestion(
            Long id, Long createdByUserId, Long importedByUserId, QuestionBankItemStatus status) {
        QuestionBankItem question = new QuestionBankItem();
        question.setId(id);
        question.setQuestionType(QuestionType.FILL_IN_BLANK);
        question.setStem("Original stem");
        question.setOptionsJson("{}");
        question.setAcceptedAnswersJson("[\"answer\"]");
        question.setDefaultScore(new BigDecimal("1.00"));
        question.setTags("[\"original\"]");
        question.setExplanation("Original explanation");
        question.setCreatedByUserId(createdByUserId);
        question.setImportedByUserId(importedByUserId);
        question.setLastModifiedByUserId(createdByUserId);
        question.setStatus(status);
        question.setCreatedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        question.setUpdatedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        return question;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
