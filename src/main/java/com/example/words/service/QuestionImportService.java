package com.example.words.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.words.dto.ConfirmQuestionImportRequest;
import com.example.words.dto.CreateQuestionRequest;
import com.example.words.dto.QuestionBankItemResponse;
import com.example.words.dto.QuestionImportConfirmResponse;
import com.example.words.dto.QuestionImportPreviewResponse;
import com.example.words.dto.QuestionImportPreviewRowResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.MetaWord;
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
import com.example.words.repository.QuestionImportBatchRepository;
import com.example.words.repository.QuestionImportPreviewRowRepository;
import com.example.words.util.WordNormalizationUtils;

@Service
public class QuestionImportService {

    private static final int RETENTION_HOURS = 24;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "questionType", "stem", "correctAnswers", "score");
    private static final Set<String> SUPPORTED_HEADERS = Set.of(
            "questionType", "category", "stem", "optionA", "optionB", "optionC", "optionD",
            "correctAnswers", "score", "difficulty", "tags", "explanation", "dictionaryName", "word");
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> RAW_ROW_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final QuestionImportBatchRepository batchRepository;
    private final QuestionImportPreviewRowRepository rowRepository;
    private final QuestionBankService questionBankService;
    private final DictionaryRepository dictionaryRepository;
    private final MetaWordRepository metaWordRepository;
    private final DictionaryWordRepository dictionaryWordRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QuestionImportService(
            QuestionImportBatchRepository batchRepository,
            QuestionImportPreviewRowRepository rowRepository,
            QuestionBankService questionBankService,
            DictionaryRepository dictionaryRepository,
            MetaWordRepository metaWordRepository,
            DictionaryWordRepository dictionaryWordRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.questionBankService = questionBankService;
        this.dictionaryRepository = dictionaryRepository;
        this.metaWordRepository = metaWordRepository;
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public QuestionImportPreviewResponse preview(MultipartFile file, AppUser actor) {
        ensureStaff(actor);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)).build()) {
            String[] headerValues = reader.readNext();
            List<String> headers = validateHeaders(headerValues);

            QuestionImportBatch batch = new QuestionImportBatch();
            batch.setImportedByUserId(actor.getId());
            batch.setFileName(file.getOriginalFilename());
            batch.setStatus(QuestionImportBatchStatus.PREVIEWED);
            batch.setExpiresAt(now().plusHours(RETENTION_HOURS));
            batch = batchRepository.saveAndFlush(batch);

            QuestionBankService.QuestionFingerprintIndex fingerprintIndex =
                    questionBankService.loadQuestionFingerprintIndex();
            List<QuestionImportPreviewRow> rows = readRows(reader, headers, batch.getId(), fingerprintIndex);
            List<QuestionImportPreviewRow> savedRows = toList(rowRepository.saveAll(rows));
            updateCounts(batch, savedRows);
            batchRepository.save(batch);
            return toResponse(batch, savedRows);
        } catch (IOException | CsvValidationException exception) {
            throw new BadRequestException("Unable to parse CSV file: " + exception.getMessage());
        }
    }

    @Transactional
    public QuestionImportPreviewResponse get(Long batchId, AppUser actor) {
        ensureStaff(actor);
        QuestionImportBatch batch = findBatch(batchId);
        ensureCanRead(batch, actor);
        expireIfNeeded(batch);
        return toResponse(batch, rowRepository.findByBatchIdOrderByRowNumberAsc(batch.getId()));
    }

    @Transactional(noRollbackFor = QuestionImportExpiredException.class)
    public QuestionImportConfirmResponse confirm(
            Long batchId, ConfirmQuestionImportRequest request, AppUser actor) {
        ensureStaff(actor);
        QuestionImportBatch batch = findBatchForUpdate(batchId);
        ensureCanConfirm(batch, actor);
        ensureConfirmable(batch);

        if (request == null || request.getSelectedRowIds() == null || request.getSelectedRowIds().isEmpty()) {
            throw new BadRequestException("At least one preview row must be selected");
        }
        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>(request.getSelectedRowIds());
        if (selectedIds.contains(null)) {
            throw new BadRequestException("Selected preview row IDs must not be null");
        }

        Map<Long, QuestionImportPreviewRow> rowsById = rowRepository
                .findByBatchIdOrderByRowNumberAsc(batch.getId())
                .stream()
                .collect(Collectors.toMap(QuestionImportPreviewRow::getId, Function.identity()));
        List<QuestionImportPreviewRow> selectedRows = new ArrayList<>();
        for (Long selectedId : selectedIds) {
            QuestionImportPreviewRow row = rowsById.get(selectedId);
            if (row == null) {
                throw new BadRequestException("Selected preview row does not belong to batch: " + selectedId);
            }
            if (row.getStatus() == QuestionImportPreviewRowStatus.INVALID) {
                throw new BadRequestException("Invalid preview row cannot be confirmed: " + selectedId);
            }
            selectedRows.add(row);
        }

        List<Long> importedQuestionIds = new ArrayList<>();
        for (QuestionImportPreviewRow row : selectedRows) {
            QuestionBankItemResponse imported = questionBankService.createImported(
                    toCreateRequest(row), batch.getId(), actor);
            importedQuestionIds.add(imported.getId());
        }

        LocalDateTime confirmedAt = now();
        batch.setStatus(QuestionImportBatchStatus.CONFIRMED);
        batch.setConfirmedAt(confirmedAt);
        batchRepository.save(batch);
        return new QuestionImportConfirmResponse(
                batch.getId(),
                importedQuestionIds.size(),
                List.copyOf(importedQuestionIds),
                batch.getStatus(),
                confirmedAt);
    }

    private List<String> validateHeaders(String[] headerValues) {
        if (headerValues == null || headerValues.length == 0) {
            throw new BadRequestException("CSV header row is required");
        }
        List<String> headers = new ArrayList<>(Arrays.asList(headerValues));
        headers.set(0, stripBom(headers.get(0)));

        Set<String> seen = new LinkedHashSet<>();
        for (String header : headers) {
            if (!SUPPORTED_HEADERS.contains(header)) {
                throw new BadRequestException("Unsupported CSV header: " + header);
            }
            if (!seen.add(header)) {
                throw new BadRequestException("Duplicate CSV header: " + header);
            }
        }
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(required -> !seen.contains(required))
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required CSV headers: " + String.join(", ", missing));
        }
        return List.copyOf(headers);
    }

    private List<QuestionImportPreviewRow> readRows(
            CSVReader reader,
            List<String> headers,
            Long batchId,
            QuestionBankService.QuestionFingerprintIndex fingerprintIndex)
            throws IOException, CsvValidationException {
        List<QuestionImportPreviewRow> rows = new ArrayList<>();
        while (true) {
            int rowNumber = Math.toIntExact(reader.getLinesRead() + 1);
            String[] values = reader.readNext();
            if (values == null) {
                break;
            }
            Map<String, String> rawRow = rawRow(headers, values);
            Map<String, Object> rawPayload = rawPayload(rawRow, headers.size(), values);
            QuestionImportPreviewRow row = baseRow(batchId, rowNumber, rawRow, rawPayload);
            if (values.length > headers.size()) {
                markInvalid(row, "CSV row contains more values than headers");
            } else {
                parseRow(row, rawRow, fingerprintIndex);
            }
            rows.add(row);
        }
        return rows;
    }

    private QuestionImportPreviewRow baseRow(
            Long batchId,
            int rowNumber,
            Map<String, String> rawRow,
            Map<String, Object> rawPayload) {
        QuestionImportPreviewRow row = new QuestionImportPreviewRow();
        row.setBatchId(batchId);
        row.setRowNumber(rowNumber);
        row.setStatus(QuestionImportPreviewRowStatus.INVALID);
        row.setRawRowJson(writeJson(rawPayload, "raw CSV row"));
        row.setDictionaryName(trimToNull(rawRow.get("dictionaryName")));
        row.setWord(trimToNull(rawRow.get("word")));
        return row;
    }

    private void parseRow(
            QuestionImportPreviewRow row,
            Map<String, String> rawRow,
            QuestionBankService.QuestionFingerprintIndex fingerprintIndex) {
        try {
            QuestionType questionType = parseQuestionType(rawRow.get("questionType"));
            Map<String, String> options = parseOptions(rawRow);
            List<String> answers = split(rawRow.get("correctAnswers"), "\\|");
            BigDecimal score = parseScore(rawRow.get("score"));
            Integer difficulty = parseDifficulty(rawRow.get("difficulty"));
            List<String> tags = split(rawRow.get("tags"), ",");
            applyParsed(row, questionType, rawRow, options, answers, score, difficulty, tags);
            TraceResolution trace = resolveTrace(row.getDictionaryName(), row.getWord());

            CreateQuestionRequest request = new CreateQuestionRequest(
                    questionType,
                    rawRow.get("category"),
                    rawRow.get("stem"),
                    options,
                    answers,
                    score,
                    difficulty,
                    tags,
                    rawRow.get("explanation"),
                    trace.dictionaryId(),
                    trace.metaWordId(),
                    QuestionBankItemStatus.ACTIVE);
            QuestionBankService.ValidatedQuestion normalized = questionBankService.validateAndNormalize(request);
            applyNormalized(row, normalized);

            Optional<Long> duplicateId = questionBankService.findCanonicalDuplicateId(fingerprintIndex, normalized);
            row.setDuplicateQuestionId(duplicateId.orElse(null));
            row.setStatus(duplicateId.isPresent()
                    ? QuestionImportPreviewRowStatus.DUPLICATE_CANDIDATE
                    : QuestionImportPreviewRowStatus.VALID);
            List<String> messages = new ArrayList<>();
            duplicateId.ifPresent(id -> messages.add("Possible duplicate of question " + id));
            messages.addAll(trace.warnings());
            row.setMessage(messages.isEmpty() ? null : String.join("; ", messages));
        } catch (BadRequestException | ResourceNotFoundException exception) {
            markInvalid(row, exception.getMessage());
        }
    }

    private void applyParsed(
            QuestionImportPreviewRow row,
            QuestionType questionType,
            Map<String, String> rawRow,
            Map<String, String> options,
            List<String> answers,
            BigDecimal score,
            Integer difficulty,
            List<String> tags) {
        row.setQuestionType(questionType);
        row.setCategory(trimToNull(rawRow.get("category")));
        row.setStem(trimToNull(rawRow.get("stem")));
        row.setOptionsJson(writeJson(options, "question options"));
        row.setAcceptedAnswersJson(writeJson(answers, "accepted answers"));
        row.setScore(score);
        row.setDifficulty(difficulty);
        row.setTags(writeJson(tags, "question tags"));
        row.setExplanation(trimToNull(rawRow.get("explanation")));
    }

    private void applyNormalized(QuestionImportPreviewRow row, QuestionBankService.ValidatedQuestion normalized) {
        row.setQuestionType(normalized.questionType());
        row.setCategory(normalized.category());
        row.setStem(normalized.stem());
        row.setOptionsJson(writeJson(normalized.options(), "question options"));
        row.setAcceptedAnswersJson(writeJson(normalized.acceptedAnswers(), "accepted answers"));
        row.setScore(normalized.defaultScore());
        row.setDifficulty(normalized.difficulty());
        row.setTags(writeJson(normalized.tags(), "question tags"));
        row.setExplanation(normalized.explanation());
        row.setDictionaryId(normalized.dictionaryId());
        row.setMetaWordId(normalized.metaWordId());
    }

    private TraceResolution resolveTrace(String dictionaryName, String word) {
        Optional<Dictionary> dictionary = dictionaryName == null
                ? Optional.empty()
                : dictionaryRepository.findByName(dictionaryName);
        Optional<MetaWord> metaWord = word == null
                ? Optional.empty()
                : metaWordRepository.findByNormalizedWord(WordNormalizationUtils.normalize(word));
        List<String> warnings = new ArrayList<>();
        if (dictionaryName != null && dictionary.isEmpty()) {
            warnings.add("Dictionary not found: " + dictionaryName);
        }
        if (word != null && metaWord.isEmpty()) {
            warnings.add("Word not found: " + word);
        }

        if (dictionaryName != null && word != null) {
            if (dictionary.isEmpty() || metaWord.isEmpty()) {
                return new TraceResolution(null, null, List.copyOf(warnings));
            }
            Long dictionaryId = dictionary.get().getId();
            Long metaWordId = metaWord.get().getId();
            if (!dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, metaWordId)) {
                warnings.add("Word " + word + " is not in dictionary " + dictionaryName);
                return new TraceResolution(null, null, List.copyOf(warnings));
            }
            return new TraceResolution(dictionaryId, metaWordId, List.copyOf(warnings));
        }
        return new TraceResolution(
                dictionary.map(Dictionary::getId).orElse(null),
                metaWord.map(MetaWord::getId).orElse(null),
                List.copyOf(warnings));
    }

    private QuestionType parseQuestionType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException("Question type is required");
        }
        try {
            return QuestionType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported question type: " + normalized);
        }
    }

    private Map<String, String> parseOptions(Map<String, String> rawRow) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String key : List.of("A", "B", "C", "D")) {
            String value = trimToNull(rawRow.get("option" + key));
            if (value != null) {
                options.put(key, value);
            }
        }
        return options;
    }

    private BigDecimal parseScore(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid score: " + normalized);
        }
    }

    private Integer parseDifficulty(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid difficulty: " + normalized);
        }
    }

    private List<String> split(String value, String separator) {
        if (value == null) {
            return List.of();
        }
        return Arrays.asList(value.split(separator, -1));
    }

    private Map<String, String> rawRow(List<String> headers, String[] values) {
        Map<String, String> rawRow = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            rawRow.put(headers.get(index), index < values.length ? values[index] : "");
        }
        return rawRow;
    }

    private Map<String, Object> rawPayload(
            Map<String, String> rawRow, int headerCount, String[] values) {
        Map<String, Object> payload = new LinkedHashMap<>(rawRow);
        if (values.length > headerCount) {
            payload.put("_extraValues", Arrays.asList(Arrays.copyOfRange(values, headerCount, values.length)));
        }
        return payload;
    }

    private void markInvalid(QuestionImportPreviewRow row, String message) {
        row.setStatus(QuestionImportPreviewRowStatus.INVALID);
        row.setMessage(message);
        row.setDuplicateQuestionId(null);
    }

    private void updateCounts(QuestionImportBatch batch, List<QuestionImportPreviewRow> rows) {
        batch.setTotalRows(rows.size());
        batch.setValidRows(count(rows, QuestionImportPreviewRowStatus.VALID));
        batch.setInvalidRows(count(rows, QuestionImportPreviewRowStatus.INVALID));
        batch.setDuplicateRows(count(rows, QuestionImportPreviewRowStatus.DUPLICATE_CANDIDATE));
    }

    private int count(List<QuestionImportPreviewRow> rows, QuestionImportPreviewRowStatus status) {
        return (int) rows.stream().filter(row -> row.getStatus() == status).count();
    }

    private QuestionImportBatch findBatch(Long batchId) {
        if (batchId == null) {
            throw new BadRequestException("Question import batch ID is required");
        }
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Question import batch not found: " + batchId));
    }

    private QuestionImportBatch findBatchForUpdate(Long batchId) {
        if (batchId == null) {
            throw new BadRequestException("Question import batch ID is required");
        }
        return batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Question import batch not found: " + batchId));
    }

    private void ensureStaff(AppUser actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER)) {
            throw new AccessDeniedException("Only administrators and teachers can import questions");
        }
    }

    private void ensureCanRead(QuestionImportBatch batch, AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN && !batch.getImportedByUserId().equals(actor.getId())) {
            throw new AccessDeniedException("Question import batch belongs to another user");
        }
    }

    private void ensureCanConfirm(QuestionImportBatch batch, AppUser actor) {
        if (!batch.getImportedByUserId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the importing user can confirm this question import batch");
        }
    }

    private void ensureConfirmable(QuestionImportBatch batch) {
        if (batch.getStatus() == QuestionImportBatchStatus.CONFIRMED) {
            throw new BadRequestException("Question import batch has already been confirmed");
        }
        if (batch.getStatus() == QuestionImportBatchStatus.EXPIRED) {
            throw new BadRequestException("Question import batch has expired");
        }
        if (isExpired(batch)) {
            batch.setStatus(QuestionImportBatchStatus.EXPIRED);
            batchRepository.save(batch);
            throw new QuestionImportExpiredException();
        }
    }

    private void expireIfNeeded(QuestionImportBatch batch) {
        if (batch.getStatus() == QuestionImportBatchStatus.PREVIEWED && isExpired(batch)) {
            batch.setStatus(QuestionImportBatchStatus.EXPIRED);
            batchRepository.save(batch);
        }
    }

    private boolean isExpired(QuestionImportBatch batch) {
        return batch.getExpiresAt() != null && !batch.getExpiresAt().isAfter(now());
    }

    private CreateQuestionRequest toCreateRequest(QuestionImportPreviewRow row) {
        return new CreateQuestionRequest(
                row.getQuestionType(),
                row.getCategory(),
                row.getStem(),
                readMap(row.getOptionsJson(), "question options"),
                readList(row.getAcceptedAnswersJson(), "accepted answers"),
                row.getScore(),
                row.getDifficulty(),
                readList(row.getTags(), "question tags"),
                row.getExplanation(),
                row.getDictionaryId(),
                row.getMetaWordId(),
                QuestionBankItemStatus.ACTIVE);
    }

    private QuestionImportPreviewResponse toResponse(
            QuestionImportBatch batch, List<QuestionImportPreviewRow> rows) {
        return new QuestionImportPreviewResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getTotalRows(),
                batch.getValidRows(),
                batch.getInvalidRows(),
                batch.getDuplicateRows(),
                batch.getStatus(),
                batch.getExpiresAt(),
                rows.stream().map(this::toRowResponse).toList());
    }

    private QuestionImportPreviewRowResponse toRowResponse(QuestionImportPreviewRow row) {
        return new QuestionImportPreviewRowResponse(
                row.getId(),
                row.getRowNumber(),
                row.getStatus(),
                row.getQuestionType(),
                row.getCategory(),
                row.getStem(),
                readMap(row.getOptionsJson(), "question options"),
                readList(row.getAcceptedAnswersJson(), "accepted answers"),
                row.getScore(),
                row.getDifficulty(),
                readList(row.getTags(), "question tags"),
                row.getExplanation(),
                row.getDictionaryName(),
                row.getWord(),
                row.getDictionaryId(),
                row.getMetaWordId(),
                row.getMessage(),
                row.getDuplicateQuestionId(),
                readRawRow(row.getRawRowJson()));
    }

    private Map<String, String> readMap(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize " + fieldName, exception);
        }
    }

    private List<String> readList(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize " + fieldName, exception);
        }
    }

    private Map<String, Object> readRawRow(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, RAW_ROW_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize raw CSV row", exception);
        }
    }

    private String writeJson(Object value, String fieldName) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize " + fieldName, exception);
        }
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private <T> List<T> toList(Iterable<T> values) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private record TraceResolution(Long dictionaryId, Long metaWordId, List<String> warnings) {
    }

    private static final class QuestionImportExpiredException extends BadRequestException {

        private QuestionImportExpiredException() {
            super("Question import batch has expired");
        }
    }
}
