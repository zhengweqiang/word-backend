package com.example.words.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class QuestionBankService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<LinkedHashMap<String, String>> OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final QuestionBankItemRepository questionRepository;
    private final DictionaryRepository dictionaryRepository;
    private final MetaWordRepository metaWordRepository;
    private final DictionaryWordRepository dictionaryWordRepository;
    private final ExamPaperAccessService accessService;
    private final ExamPaperAnswerNormalizer answerNormalizer;
    private final ObjectMapper objectMapper;

    public QuestionBankService(
            QuestionBankItemRepository questionRepository,
            DictionaryRepository dictionaryRepository,
            MetaWordRepository metaWordRepository,
            DictionaryWordRepository dictionaryWordRepository,
            ExamPaperAccessService accessService,
            ExamPaperAnswerNormalizer answerNormalizer,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.metaWordRepository = metaWordRepository;
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.accessService = accessService;
        this.answerNormalizer = answerNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuestionBankItemResponse create(CreateQuestionRequest request, AppUser actor) {
        ensureStaff(actor);
        if (request == null) {
            throw new BadRequestException("Question request is required");
        }

        QuestionBankItemStatus status = defaultStatus(request.getStatus());
        ensureWritableStatus(status);
        ValidatedQuestion normalized = normalizeAndValidate(
                request.getQuestionType(),
                request.getStem(),
                request.getOptions(),
                request.getAcceptedAnswers(),
                request.getDefaultScore(),
                request.getDifficulty(),
                request.getTags(),
                request.getExplanation(),
                request.getDictionaryId(),
                request.getMetaWordId());

        QuestionBankItem question = new QuestionBankItem();
        apply(question, normalized, status, actor.getId());
        question.setCreatedByUserId(actor.getId());
        return toResponse(questionRepository.saveAndFlush(question));
    }

    public ValidatedQuestion validateAndNormalize(CreateQuestionRequest request) {
        if (request == null) {
            throw new BadRequestException("Question request is required");
        }
        return normalizeAndValidate(
                request.getQuestionType(),
                request.getStem(),
                request.getOptions(),
                request.getAcceptedAnswers(),
                request.getDefaultScore(),
                request.getDifficulty(),
                request.getTags(),
                request.getExplanation(),
                request.getDictionaryId(),
                request.getMetaWordId());
    }

    @Transactional
    public QuestionBankItemResponse createImported(CreateQuestionRequest request, Long batchId, AppUser actor) {
        ensureStaff(actor);
        if (batchId == null) {
            throw new BadRequestException("Question import batch ID is required");
        }
        ValidatedQuestion normalized = validateAndNormalize(request);

        QuestionBankItem question = new QuestionBankItem();
        apply(question, normalized, QuestionBankItemStatus.ACTIVE, actor.getId());
        question.setCreatedByUserId(actor.getId());
        question.setImportedByUserId(actor.getId());
        question.setImportBatchId(batchId);
        return toResponse(questionRepository.saveAndFlush(question));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findCanonicalDuplicateId(ValidatedQuestion candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        return questionRepository.findAll().stream()
                .filter(question -> canonicalMatch(question, candidate))
                .map(QuestionBankItem::getId)
                .findFirst();
    }

    @Transactional
    public QuestionBankItemResponse update(Long questionId, UpdateQuestionRequest request, AppUser actor) {
        ensureStaff(actor);
        if (request == null) {
            throw new BadRequestException("Question request is required");
        }

        QuestionBankItem question = findQuestion(questionId);
        accessService.ensureCanManageQuestion(actor, question);
        if (question.getStatus() == QuestionBankItemStatus.ARCHIVED) {
            throw new BadRequestException("Archived questions cannot be updated");
        }
        ensureWritableStatus(request.getStatus());

        ValidatedQuestion normalized = normalizeAndValidate(
                request.getQuestionType(),
                request.getStem(),
                request.getOptions(),
                request.getAcceptedAnswers(),
                request.getDefaultScore(),
                request.getDifficulty(),
                request.getTags(),
                request.getExplanation(),
                request.getDictionaryId(),
                request.getMetaWordId());
        apply(question, normalized, request.getStatus(), actor.getId());
        return toResponse(questionRepository.saveAndFlush(question));
    }

    @Transactional
    public QuestionBankItemResponse copy(Long questionId, CopyQuestionRequest request, AppUser actor) {
        ensureStaff(actor);
        QuestionBankItem source = findQuestion(questionId);
        accessService.ensureCanUseQuestion(actor, source);

        CopyQuestionRequest resolvedRequest = request == null ? new CopyQuestionRequest() : request;
        String stem = resolvedRequest.getStem() == null ? source.getStem() : resolvedRequest.getStem();

        ValidatedQuestion normalized = normalizeAndValidate(
                source.getQuestionType(),
                stem,
                readOptions(source.getOptionsJson()),
                readStringList(source.getAcceptedAnswersJson(), "accepted answers"),
                source.getDefaultScore(),
                source.getDifficulty(),
                readTags(source.getTags()),
                source.getExplanation(),
                source.getDictionaryId(),
                source.getMetaWordId());

        QuestionBankItem copied = new QuestionBankItem();
        apply(copied, normalized, QuestionBankItemStatus.DRAFT, actor.getId());
        copied.setSourceQuestionId(source.getId());
        copied.setCreatedByUserId(actor.getId());
        copied.setImportedByUserId(null);
        copied.setImportBatchId(null);
        return toResponse(questionRepository.saveAndFlush(copied));
    }

    @Transactional
    public void archive(Long questionId, AppUser actor) {
        ensureStaff(actor);
        QuestionBankItem question = findQuestion(questionId);
        accessService.ensureCanManageQuestion(actor, question);
        question.setStatus(QuestionBankItemStatus.ARCHIVED);
        question.setArchivedAt(LocalDateTime.now());
        question.setLastModifiedByUserId(actor.getId());
        questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public Page<QuestionBankItemResponse> search(QuestionBankSearchRequest request, AppUser actor) {
        ensureStaff(actor);
        QuestionBankSearchRequest resolvedRequest = request == null ? new QuestionBankSearchRequest() : request;
        Pageable pageable = buildPageable(resolvedRequest);
        Specification<QuestionBankItem> specification = Specification.<QuestionBankItem>where(visibleTo(actor))
                .and(keywordLike(resolvedRequest.getKeyword()))
                .and(questionTypeEquals(resolvedRequest.getQuestionType()))
                .and(statusEquals(resolvedRequest.getStatus()))
                .and(tagEquals(resolvedRequest.getTag()))
                .and(fieldEquals("dictionaryId", resolvedRequest.getDictionaryId()))
                .and(fieldEquals("metaWordId", resolvedRequest.getMetaWordId()))
                .and(fieldEquals("createdByUserId", resolvedRequest.getCreatorId()));

        Page<QuestionBankItem> result = questionRepository.findAll(specification, pageable);
        List<QuestionBankItemResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(content, pageable, result.getTotalElements());
    }

    private ValidatedQuestion normalizeAndValidate(
            QuestionType questionType,
            String stem,
            Map<String, String> options,
            List<String> acceptedAnswers,
            BigDecimal defaultScore,
            Integer difficulty,
            List<String> tags,
            String explanation,
            Long dictionaryId,
            Long metaWordId) {
        if (questionType == null) {
            throw new BadRequestException("Question type is required");
        }
        String normalizedStem = trimToNull(stem);
        if (normalizedStem == null) {
            throw new BadRequestException("Question stem is required");
        }
        if (defaultScore == null || defaultScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Question score must be positive");
        }
        int integerDigits = Math.max(defaultScore.precision() - defaultScore.scale(), 0);
        int fractionDigits = Math.max(defaultScore.scale(), 0);
        if (integerDigits > 17 || fractionDigits > 2) {
            throw new BadRequestException("Question score must fit NUMERIC(19,2)");
        }

        Map<String, String> normalizedOptions = normalizeOptions(options);
        List<String> normalizedAnswers = normalizeAnswers(questionType, acceptedAnswers);
        validateTypeSpecificRules(questionType, normalizedOptions, normalizedAnswers);
        validateTrace(dictionaryId, metaWordId);

        return new ValidatedQuestion(
                questionType,
                normalizedStem,
                normalizedOptions,
                normalizedAnswers,
                defaultScore,
                difficulty,
                normalizeTags(tags),
                trimToNull(explanation),
                dictionaryId,
                metaWordId);
    }

    private Map<String, String> normalizeOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new TreeMap<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            List<String> keys = answerNormalizer.normalizeOptionKeys(
                    entry.getKey() == null ? List.of() : List.of(entry.getKey()));
            if (keys.isEmpty()) {
                throw new BadRequestException("Option keys must not be blank");
            }
            String text = trimToNull(entry.getValue());
            if (text == null) {
                throw new BadRequestException("Option text must not be blank");
            }
            if (normalized.putIfAbsent(keys.get(0), text) != null) {
                throw new BadRequestException("Option keys must be unique after normalization");
            }
        }
        return new LinkedHashMap<>(normalized);
    }

    private List<String> normalizeAnswers(QuestionType questionType, List<String> acceptedAnswers) {
        if (questionType == QuestionType.FILL_IN_BLANK) {
            if (acceptedAnswers == null) {
                return List.of();
            }
            Set<String> normalized = new LinkedHashSet<>();
            for (String answer : acceptedAnswers) {
                String value = answerNormalizer.normalizeBlankAnswer(answer);
                if (value != null && !value.isBlank()) {
                    normalized.add(value);
                }
            }
            return List.copyOf(normalized);
        }
        return answerNormalizer.normalizeOptionKeys(acceptedAnswers);
    }

    private void validateTypeSpecificRules(
            QuestionType questionType, Map<String, String> options, List<String> acceptedAnswers) {
        if (questionType == QuestionType.FILL_IN_BLANK) {
            if (!options.isEmpty()) {
                throw new BadRequestException("Fill-in-the-blank questions cannot have options");
            }
            if (acceptedAnswers.isEmpty()) {
                throw new BadRequestException("Fill-in-the-blank questions require an accepted answer");
            }
            return;
        }

        if (options.size() < 2 || options.size() > 4) {
            throw new BadRequestException("Choice questions require two to four options");
        }
        if (questionType == QuestionType.SINGLE_CHOICE && acceptedAnswers.size() != 1) {
            throw new BadRequestException("Single-choice questions require exactly one accepted answer");
        }
        if (questionType == QuestionType.MULTIPLE_CHOICE && acceptedAnswers.size() < 2) {
            throw new BadRequestException("Multiple-choice questions require at least two distinct accepted answers");
        }
        if (!options.keySet().containsAll(acceptedAnswers)) {
            throw new BadRequestException("Accepted answers must reference existing option keys");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = trimToNull(tag);
            if (value != null) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private void validateTrace(Long dictionaryId, Long metaWordId) {
        if (dictionaryId != null && !dictionaryRepository.existsById(dictionaryId)) {
            throw new ResourceNotFoundException("Dictionary not found: " + dictionaryId);
        }
        if (metaWordId != null && !metaWordRepository.existsById(metaWordId)) {
            throw new ResourceNotFoundException("Meta word not found: " + metaWordId);
        }
        if (dictionaryId != null && metaWordId != null
                && !dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(dictionaryId, metaWordId)) {
            throw new BadRequestException("Meta word does not belong to the selected dictionary");
        }
    }

    private void apply(
            QuestionBankItem question,
            ValidatedQuestion normalized,
            QuestionBankItemStatus status,
            Long modifiedByUserId) {
        question.setQuestionType(normalized.questionType());
        question.setStem(normalized.stem());
        question.setOptionsJson(writeJson(normalized.options(), "options"));
        question.setAcceptedAnswersJson(writeJson(normalized.acceptedAnswers(), "accepted answers"));
        question.setDefaultScore(normalized.defaultScore());
        question.setDifficulty(normalized.difficulty());
        question.setTags(writeJson(normalized.tags(), "tags"));
        question.setExplanation(normalized.explanation());
        question.setDictionaryId(normalized.dictionaryId());
        question.setMetaWordId(normalized.metaWordId());
        question.setLastModifiedByUserId(modifiedByUserId);
        question.setStatus(status);
        question.setArchivedAt(null);
    }

    private boolean canonicalMatch(QuestionBankItem question, ValidatedQuestion candidate) {
        if (question.getQuestionType() != candidate.questionType()
                || !candidate.stem().equals(trimToNull(question.getStem()))) {
            return false;
        }
        Map<String, String> options = normalizeOptions(readOptions(question.getOptionsJson()));
        List<String> answers = normalizeAnswers(
                question.getQuestionType(),
                readStringList(question.getAcceptedAnswersJson(), "accepted answers"));
        return candidate.options().equals(options)
                && Set.copyOf(candidate.acceptedAnswers()).equals(Set.copyOf(answers));
    }

    private QuestionBankItem findQuestion(Long questionId) {
        if (questionId == null) {
            throw new BadRequestException("Question ID is required");
        }
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
    }

    private void ensureStaff(AppUser actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER)) {
            throw new AccessDeniedException("Only administrators and teachers can manage the question bank");
        }
    }

    private QuestionBankItemStatus defaultStatus(QuestionBankItemStatus status) {
        return status == null ? QuestionBankItemStatus.DRAFT : status;
    }

    private void ensureWritableStatus(QuestionBankItemStatus status) {
        if (status == null) {
            throw new BadRequestException("Question status is required");
        }
        if (status == QuestionBankItemStatus.ARCHIVED) {
            throw new BadRequestException("Questions can only be archived through the archive operation");
        }
    }

    private Pageable buildPageable(QuestionBankSearchRequest request) {
        int page = request.getPage() == null ? 0 : Math.max(request.getPage(), 0);
        int size = request.getSize() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(request.getSize(), 1), MAX_PAGE_SIZE);
        Sort sort = Sort.by(
                Sort.Order.desc("updatedAt"),
                Sort.Order.desc("id"));
        return PageRequest.of(page, size, sort);
    }

    private Specification<QuestionBankItem> visibleTo(AppUser actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.equal(root.get("status"), QuestionBankItemStatus.ACTIVE),
                criteriaBuilder.equal(root.get("createdByUserId"), actor.getId()),
                criteriaBuilder.equal(root.get("importedByUserId"), actor.getId()));
    }

    private Specification<QuestionBankItem> keywordLike(String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            return null;
        }
        String pattern = "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("stem")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("explanation")), pattern));
    }

    private Specification<QuestionBankItem> questionTypeEquals(QuestionType questionType) {
        if (questionType == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("questionType"), questionType);
    }

    private Specification<QuestionBankItem> statusEquals(QuestionBankItemStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    private Specification<QuestionBankItem> tagEquals(String tag) {
        String normalized = trimToNull(tag);
        if (normalized == null) {
            return null;
        }
        String pattern = "%\"" + normalized.toLowerCase(Locale.ROOT) + "\"%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("tags")), pattern);
    }

    private Specification<QuestionBankItem> fieldEquals(String field, Long value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private QuestionBankItemResponse toResponse(QuestionBankItem question) {
        return new QuestionBankItemResponse(
                question.getId(),
                question.getQuestionType(),
                question.getStem(),
                readOptions(question.getOptionsJson()),
                readStringList(question.getAcceptedAnswersJson(), "accepted answers"),
                question.getDefaultScore(),
                question.getDifficulty(),
                readTags(question.getTags()),
                question.getExplanation(),
                question.getDictionaryId(),
                question.getMetaWordId(),
                question.getSourceQuestionId(),
                question.getImportBatchId(),
                question.getCreatedByUserId(),
                question.getImportedByUserId(),
                question.getLastModifiedByUserId(),
                question.getStatus(),
                question.getCreatedAt(),
                question.getUpdatedAt(),
                question.getArchivedAt());
    }

    private Map<String, String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OPTIONS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize question options", exception);
        }
    }

    private List<String> readTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return readStringList(json, "tags");
    }

    private List<String> readStringList(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize question " + fieldName, exception);
        }
    }

    private String writeJson(Object value, String fieldName) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize question " + fieldName, exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ValidatedQuestion(
            QuestionType questionType,
            String stem,
            Map<String, String> options,
            List<String> acceptedAnswers,
            BigDecimal defaultScore,
            Integer difficulty,
            List<String> tags,
            String explanation,
            Long dictionaryId,
            Long metaWordId) {
    }
}
