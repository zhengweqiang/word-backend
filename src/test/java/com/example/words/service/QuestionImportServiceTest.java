package com.example.words.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.example.words.dto.ConfirmQuestionImportRequest;
import com.example.words.dto.QuestionImportConfirmResponse;
import com.example.words.dto.QuestionImportPreviewResponse;
import com.example.words.dto.QuestionImportPreviewRowResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.MetaWord;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.QuestionBankItemStatus;
import com.example.words.model.QuestionImportBatch;
import com.example.words.model.QuestionImportBatchStatus;
import com.example.words.model.QuestionImportPreviewRow;
import com.example.words.model.QuestionImportPreviewRowStatus;
import com.example.words.model.QuestionType;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.QuestionBankItemRepository;
import com.example.words.repository.QuestionImportBatchRepository;
import com.example.words.repository.QuestionImportPreviewRowRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T03:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String HEADERS = String.join(",",
            "questionType", "category", "stem", "optionA", "optionB", "optionC", "optionD",
            "correctAnswers", "score", "difficulty", "tags", "explanation", "dictionaryName", "word");

    @Mock
    private QuestionImportBatchRepository batchRepository;

    @Mock
    private QuestionImportPreviewRowRepository rowRepository;

    @Mock
    private QuestionBankItemRepository questionRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    @Mock
    private DictionaryWordRepository dictionaryWordRepository;

    private QuestionImportService service;
    private ObjectMapper objectMapper;
    private final AtomicLong questionIds = new AtomicLong(900L);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        QuestionBankService questionBankService = new QuestionBankService(
                questionRepository,
                dictionaryRepository,
                metaWordRepository,
                dictionaryWordRepository,
                new ExamPaperAccessService(mock(TeacherStudentService.class), mock(ClassroomService.class)),
                new ExamPaperAnswerNormalizer(),
                objectMapper);
        service = new QuestionImportService(
                batchRepository,
                rowRepository,
                questionBankService,
                dictionaryRepository,
                metaWordRepository,
                dictionaryWordRepository,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(batchRepository.saveAndFlush(any(QuestionImportBatch.class))).thenAnswer(invocation -> {
            QuestionImportBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(40L);
            }
            return batch;
        });
        lenient().when(batchRepository.save(any(QuestionImportBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(rowRepository.saveAll(any())).thenAnswer(invocation -> {
            List<QuestionImportPreviewRow> rows = new ArrayList<>();
            for (QuestionImportPreviewRow row : (Iterable<QuestionImportPreviewRow>) invocation.getArgument(0)) {
                if (row.getId() == null) {
                    row.setId(100L + row.getRowNumber());
                }
                rows.add(row);
            }
            return rows;
        });
        lenient().when(questionRepository.findAll()).thenReturn(List.of());
        lenient().when(questionRepository.saveAndFlush(any(QuestionBankItem.class))).thenAnswer(invocation -> {
            QuestionBankItem question = invocation.getArgument(0);
            question.setId(questionIds.incrementAndGet());
            question.setCreatedAt(LOCAL_NOW);
            question.setUpdatedAt(LOCAL_NOW);
            return question;
        });
    }

    @Test
    void previewParsesUtf8BomAndPersistsNormalizedValidRows() {
        String csv = "\uFEFF" + HEADERS + "\n"
                + "single_choice, 听力,  Capital of France?  , Paris ,London,,, a ,2.50,3, geography , Basic , , \n";

        QuestionImportPreviewResponse response = service.preview(file("questions.csv", csv), teacher(7L));

        assertEquals(40L, response.getBatchId());
        assertEquals("questions.csv", response.getFileName());
        assertEquals(1, response.getTotalRows());
        assertEquals(1, response.getValidRows());
        assertEquals(0, response.getInvalidRows());
        assertEquals(0, response.getDuplicateRows());
        assertEquals(QuestionImportBatchStatus.PREVIEWED, response.getStatus());
        assertEquals(LOCAL_NOW.plusHours(24), response.getExpiresAt());

        QuestionImportPreviewRowResponse row = response.getRows().get(0);
        assertEquals(2, row.getRowNumber());
        assertEquals(QuestionImportPreviewRowStatus.VALID, row.getStatus());
        assertEquals(QuestionType.SINGLE_CHOICE, row.getQuestionType());
        assertEquals("听力", row.getCategory());
        assertEquals("Capital of France?", row.getStem());
        assertEquals(Map.of("A", "Paris", "B", "London"), row.getOptions());
        assertEquals(List.of("A"), row.getAcceptedAnswers());
        assertEquals(new BigDecimal("2.50"), row.getScore());
        assertEquals(List.of("geography"), row.getTags());
        assertEquals("single_choice", row.getRawRow().get("questionType"));
        assertEquals(" 听力", row.getRawRow().get("category"));

        ArgumentCaptor<List<QuestionImportPreviewRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowRepository).saveAll(captor.capture());
        assertFalse(captor.getValue().get(0).getRawRowJson().isBlank());
    }

    @Test
    void previewRejectsMissingRequiredHeadersBeforePersistingBatch() {
        String csv = "questionType,stem,score\nFILL_IN_BLANK,Capital of France,2\n";

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.preview(file("missing.csv", csv), teacher(7L)));

        assertEquals("Missing required CSV headers: correctAnswers", exception.getMessage());
        verify(batchRepository, never()).saveAndFlush(any());
        verify(rowRepository, never()).saveAll(any());
    }

    @Test
    void previewPersistsInvalidOptionAndCorrectAnswerRowsWithReadableMessages() {
        String csv = HEADERS + "\n"
                + "SINGLE_CHOICE,,Question one,Only one option,,,,A,1,,,,,\n"
                + "SINGLE_CHOICE,,Question two,One,Two,,,C,1,,,,,\n";

        QuestionImportPreviewResponse response = service.preview(file("invalid.csv", csv), teacher(7L));

        assertEquals(2, response.getInvalidRows());
        assertEquals("Choice questions require two to four options", response.getRows().get(0).getMessage());
        assertEquals("Accepted answers must reference existing option keys", response.getRows().get(1).getMessage());
        assertEquals(QuestionType.SINGLE_CHOICE, response.getRows().get(0).getQuestionType());
        assertEquals("Question one", response.getRows().get(0).getStem());
        assertEquals(Map.of("A", "Only one option"), response.getRows().get(0).getOptions());
        assertEquals(List.of("A"), response.getRows().get(0).getAcceptedAnswers());
        assertTrue(response.getRows().stream()
                .allMatch(row -> row.getStatus() == QuestionImportPreviewRowStatus.INVALID));
    }

    @Test
    void previewUsesPhysicalFileLineNumbersForMultilineCsvRecords() {
        String csv = "questionType,stem,correctAnswers,score\n"
                + "FILL_IN_BLANK,\"Line one\nLine two\",answer,1\n"
                + "FILL_IN_BLANK,Next question,next,1\n";

        QuestionImportPreviewResponse response = service.preview(file("multiline.csv", csv), teacher(7L));

        assertEquals(List.of(2, 4), response.getRows().stream()
                .map(QuestionImportPreviewRowResponse::getRowNumber)
                .toList());
    }

    @Test
    void previewPreservesEveryExtraCsvValueInRawRowJson() throws Exception {
        String csv = "questionType,stem,correctAnswers,score\n"
                + "FILL_IN_BLANK,Question,answer,1,first extra,second extra\n";

        QuestionImportPreviewRowResponse row = service.preview(file("extra.csv", csv), teacher(7L))
                .getRows().get(0);

        assertEquals(QuestionImportPreviewRowStatus.INVALID, row.getStatus());
        assertEquals(List.of("first extra", "second extra"), row.getRawRow().get("_extraValues"));
        ArgumentCaptor<List<QuestionImportPreviewRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowRepository).saveAll(captor.capture());
        assertEquals("first extra", objectMapper.readTree(captor.getValue().get(0).getRawRowJson())
                .get("_extraValues").get(0).asText());
        assertEquals("second extra", objectMapper.readTree(captor.getValue().get(0).getRawRowJson())
                .get("_extraValues").get(1).asText());
    }

    @Test
    void previewScansSharedQuestionBankOnceForMultipleRows() {
        String csv = "questionType,stem,correctAnswers,score\n"
                + "FILL_IN_BLANK,Question one,one,1\n"
                + "FILL_IN_BLANK,Question two,two,1\n";

        QuestionImportPreviewResponse response = service.preview(file("two.csv", csv), teacher(7L));

        assertEquals(2, response.getValidRows());
        verify(questionRepository, times(1)).findAll();
    }

    @Test
    void previewFlagsCanonicalMatchesAgainstSharedQuestionBank() throws Exception {
        QuestionBankItem existing = new QuestionBankItem();
        existing.setId(88L);
        existing.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        existing.setStem("Select primes");
        existing.setOptionsJson(objectMapper.writeValueAsString(Map.of("A", "2", "B", "3", "C", "4")));
        existing.setAcceptedAnswersJson(objectMapper.writeValueAsString(List.of("A", "B")));
        when(questionRepository.findAll()).thenReturn(List.of(existing));
        String csv = HEADERS + "\n"
                + "multiple_choice,, Select primes , 2 , 3 , 4 ,, b|a,1,,,,,\n";

        QuestionImportPreviewRowResponse row = service.preview(file("duplicates.csv", csv), teacher(7L))
                .getRows().get(0);

        assertEquals(QuestionImportPreviewRowStatus.DUPLICATE_CANDIDATE, row.getStatus());
        assertEquals(88L, row.getDuplicateQuestionId());
        assertEquals("Possible duplicate of question 88", row.getMessage());
    }

    @Test
    void previewWarnsAboutUnresolvedTraceHintsAndPersistsNullResolvedIds() {
        when(dictionaryRepository.findByName("Unknown dictionary")).thenReturn(Optional.empty());
        when(metaWordRepository.findByNormalizedWord("missing")).thenReturn(Optional.empty());
        String csv = HEADERS + "\n"
                + "FILL_IN_BLANK,,Complete it,,,,,answer,1,,,,Unknown dictionary,Missing\n";

        QuestionImportPreviewRowResponse row = service.preview(file("trace.csv", csv), teacher(7L))
                .getRows().get(0);

        assertEquals(QuestionImportPreviewRowStatus.VALID, row.getStatus());
        assertTrue(row.getMessage().contains("Dictionary not found: Unknown dictionary"));
        assertTrue(row.getMessage().contains("Word not found: Missing"));
        assertNull(row.getDictionaryId());
        assertNull(row.getMetaWordId());

        ArgumentCaptor<List<QuestionImportPreviewRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(rowRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getDictionaryId());
        assertNull(captor.getValue().get(0).getMetaWordId());
    }

    @Test
    void readAllowsOwnerAndAdminButRejectsOtherTeacherAndStudents() {
        QuestionImportBatch batch = batch(40L, 7L, QuestionImportBatchStatus.PREVIEWED, LOCAL_NOW.plusHours(1));
        when(batchRepository.findById(40L)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchIdOrderByRowNumberAsc(40L)).thenReturn(List.of());

        assertEquals(40L, service.get(40L, teacher(7L)).getBatchId());
        assertEquals(40L, service.get(40L, user(99L, UserRole.ADMIN)).getBatchId());
        assertThrows(AccessDeniedException.class, () -> service.get(40L, teacher(8L)));
        assertThrows(AccessDeniedException.class, () -> service.get(40L, user(7L, UserRole.STUDENT)));
    }

    @Test
    void confirmExpiresPastDuePreviewAndCreatesNothing() {
        QuestionImportBatch batch = batch(40L, 7L, QuestionImportBatchStatus.PREVIEWED, LOCAL_NOW.minusSeconds(1));
        when(batchRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(batch));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.confirm(40L, new ConfirmQuestionImportRequest(List.of(102L)), teacher(7L)));

        assertEquals("Question import batch has expired", exception.getMessage());
        assertEquals(QuestionImportBatchStatus.EXPIRED, batch.getStatus());
        verify(batchRepository).save(batch);
        verify(questionRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmCreatesOnlyDistinctSelectedValidOrDuplicateRowsFromPersistedPreview() throws Exception {
        QuestionImportBatch batch = batch(40L, 7L, QuestionImportBatchStatus.PREVIEWED, LOCAL_NOW.plusHours(1));
        QuestionImportPreviewRow valid = previewRow(
                102L, 40L, 2, QuestionImportPreviewRowStatus.VALID,
                QuestionType.SINGLE_CHOICE, "Persisted one", Map.of("A", "Yes", "B", "No"), List.of("A"));
        valid.setCategory("听力");
        valid.setDictionaryId(11L);
        valid.setMetaWordId(22L);
        QuestionImportPreviewRow duplicate = previewRow(
                103L, 40L, 3, QuestionImportPreviewRowStatus.DUPLICATE_CANDIDATE,
                QuestionType.FILL_IN_BLANK, "Persisted two", Map.of(), List.of("answer"));
        QuestionImportPreviewRow invalid = previewRow(
                104L, 40L, 4, QuestionImportPreviewRowStatus.INVALID,
                null, "Invalid", Map.of(), List.of());
        when(batchRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchIdOrderByRowNumberAsc(40L)).thenReturn(List.of(valid, duplicate, invalid));
        when(dictionaryRepository.existsById(11L)).thenReturn(true);
        when(metaWordRepository.existsById(22L)).thenReturn(true);
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(11L, 22L)).thenReturn(true);

        QuestionImportConfirmResponse response = service.confirm(
                40L,
                new ConfirmQuestionImportRequest(List.of(102L, 102L, 103L)),
                teacher(7L));

        assertEquals(2, response.getImportedCount());
        assertEquals(2, response.getImportedQuestionIds().size());
        assertEquals(QuestionImportBatchStatus.CONFIRMED, response.getStatus());
        assertEquals(LOCAL_NOW, response.getConfirmedAt());
        assertEquals(QuestionImportBatchStatus.CONFIRMED, batch.getStatus());
        assertEquals(LOCAL_NOW, batch.getConfirmedAt());

        ArgumentCaptor<QuestionBankItem> captor = ArgumentCaptor.forClass(QuestionBankItem.class);
        verify(questionRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(question -> question.getStatus() == QuestionBankItemStatus.ACTIVE));
        assertTrue(captor.getAllValues().stream()
                .allMatch(question -> question.getCreatedByUserId().equals(7L)
                        && question.getImportedByUserId().equals(7L)
                        && question.getImportBatchId().equals(40L)));
        assertEquals(11L, captor.getAllValues().get(0).getDictionaryId());
        assertEquals(22L, captor.getAllValues().get(0).getMetaWordId());
        assertEquals("听力", captor.getAllValues().get(0).getCategory());
    }

    @Test
    void confirmRejectsInvalidRowsEmptySelectionsOtherOwnersAndRepeatedConfirmation() throws Exception {
        QuestionImportBatch batch = batch(40L, 7L, QuestionImportBatchStatus.PREVIEWED, LOCAL_NOW.plusHours(1));
        QuestionImportPreviewRow invalid = previewRow(
                104L, 40L, 4, QuestionImportPreviewRowStatus.INVALID,
                null, "Invalid", Map.of(), List.of());
        when(batchRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(batch));
        when(rowRepository.findByBatchIdOrderByRowNumberAsc(40L)).thenReturn(List.of(invalid));

        assertThrows(BadRequestException.class,
                () -> service.confirm(40L, new ConfirmQuestionImportRequest(List.of()), teacher(7L)));
        assertThrows(AccessDeniedException.class,
                () -> service.confirm(40L, new ConfirmQuestionImportRequest(List.of(104L)), teacher(8L)));
        assertThrows(BadRequestException.class,
                () -> service.confirm(40L, new ConfirmQuestionImportRequest(List.of(104L)), teacher(7L)));

        batch.setStatus(QuestionImportBatchStatus.CONFIRMED);
        assertThrows(BadRequestException.class,
                () -> service.confirm(40L, new ConfirmQuestionImportRequest(List.of(104L)), teacher(7L)));
        verify(questionRepository, never()).saveAndFlush(any());
    }

    private MockMultipartFile file(String name, String csv) {
        return new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private AppUser teacher(Long id) {
        return user(id, UserRole.TEACHER);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private QuestionImportBatch batch(
            Long id, Long ownerId, QuestionImportBatchStatus status, LocalDateTime expiresAt) {
        QuestionImportBatch batch = new QuestionImportBatch();
        batch.setId(id);
        batch.setImportedByUserId(ownerId);
        batch.setFileName("questions.csv");
        batch.setStatus(status);
        batch.setExpiresAt(expiresAt);
        return batch;
    }

    private QuestionImportPreviewRow previewRow(
            Long id,
            Long batchId,
            int rowNumber,
            QuestionImportPreviewRowStatus status,
            QuestionType questionType,
            String stem,
            Map<String, String> options,
            List<String> answers) throws Exception {
        QuestionImportPreviewRow row = new QuestionImportPreviewRow();
        row.setId(id);
        row.setBatchId(batchId);
        row.setRowNumber(rowNumber);
        row.setStatus(status);
        row.setQuestionType(questionType);
        row.setStem(stem);
        row.setOptionsJson(objectMapper.writeValueAsString(options));
        row.setAcceptedAnswersJson(objectMapper.writeValueAsString(answers));
        row.setScore(BigDecimal.ONE);
        row.setTags("[]");
        return row;
    }
}
