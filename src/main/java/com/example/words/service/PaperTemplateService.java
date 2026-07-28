package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.AddPaperQuestionRequest;
import com.example.words.dto.CopyPaperTemplateRequest;
import com.example.words.dto.CreatePaperTemplateRequest;
import com.example.words.dto.PaperTemplateQuestionResponse;
import com.example.words.dto.PaperTemplateResponse;
import com.example.words.dto.PaperTemplateSearchRequest;
import com.example.words.dto.ReorderPaperQuestionsRequest;
import com.example.words.dto.UpdatePaperQuestionScoreRequest;
import com.example.words.dto.UpdatePaperTemplateRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.PaperTemplateStatus;
import com.example.words.model.QuestionBankItem;
import com.example.words.model.UserRole;
import com.example.words.repository.PaperTemplateQuestionRepository;
import com.example.words.repository.PaperTemplateRepository;

@Service
public class PaperTemplateService {

    private static final BigDecimal MAX_SCORE = new BigDecimal("99999999999999999.99");

    private final PaperTemplateRepository paperRepository;
    private final PaperTemplateQuestionRepository paperQuestionRepository;
    private final QuestionBankService questionBankService;
    private final ExamPaperSnapshotService snapshotService;
    private final ExamPaperAccessService accessService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperTemplateService(
            PaperTemplateRepository paperRepository,
            PaperTemplateQuestionRepository paperQuestionRepository,
            QuestionBankService questionBankService,
            ExamPaperSnapshotService snapshotService,
            ExamPaperAccessService accessService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.questionBankService = questionBankService;
        this.snapshotService = snapshotService;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PaperTemplateResponse create(CreatePaperTemplateRequest request, AppUser actor) {
        ensureStaff(actor);
        if (request == null) {
            throw new BadRequestException("Paper request is required");
        }
        PaperTemplate paper = new PaperTemplate();
        paper.setTitle(requireTitle(request.getTitle()));
        paper.setInstructions(trimToNull(request.getInstructions()));
        paper.setOwnerUserId(actor.getId());
        paper.setStatus(PaperTemplateStatus.DRAFT);
        paper.setShuffleQuestions(defaultFalse(request.getShuffleQuestions()));
        paper.setShuffleOptions(defaultFalse(request.getShuffleOptions()));
        paper.setTotalScore(zeroScore());
        paperRepository.saveAndFlush(paper);
        return toResponse(paper, List.of());
    }

    @Transactional
    public PublicationSource lockReadyForPublishing(Long paperId, AppUser actor) {
        ensureStaff(actor);
        PaperTemplate paper = findPaperForUpdate(paperId);
        if (actor.getRole() != UserRole.ADMIN) {
            accessService.ensureCanManagePaper(actor, paper);
        }
        if (paper.getStatus() != PaperTemplateStatus.READY) {
            throw new BadRequestException("Only READY papers can be published");
        }
        List<PaperTemplateQuestion> questions = loadQuestions(paper.getId());
        if (questions.isEmpty()) {
            throw new BadRequestException("Paper must contain at least one active question");
        }
        return new PublicationSource(paper, List.copyOf(questions));
    }

    public record PublicationSource(PaperTemplate paper, List<PaperTemplateQuestion> questions) {
    }

    @Transactional
    public PaperTemplateResponse update(Long paperId, UpdatePaperTemplateRequest request, AppUser actor) {
        PaperTemplate paper = findPaperForUpdate(paperId);
        accessService.ensureCanManagePaper(actor, paper);
        ensureEditable(paper);
        if (request == null) {
            throw new BadRequestException("Paper request is required");
        }
        if (request.getStatus() == null || request.getStatus() == PaperTemplateStatus.ARCHIVED) {
            throw new BadRequestException("Use the archive operation to archive a paper");
        }
        paper.setTitle(requireTitle(request.getTitle()));
        paper.setInstructions(trimToNull(request.getInstructions()));
        paper.setShuffleQuestions(requireBoolean(request.getShuffleQuestions(), "shuffleQuestions"));
        paper.setShuffleOptions(requireBoolean(request.getShuffleOptions(), "shuffleOptions"));
        paper.setStatus(request.getStatus());
        paperRepository.saveAndFlush(paper);
        return toResponse(paper, loadQuestions(paperId));
    }

    @Transactional(readOnly = true)
    public Page<PaperTemplateResponse> search(PaperTemplateSearchRequest request, AppUser actor) {
        ensureStaff(actor);
        PaperTemplateSearchRequest criteria = request == null ? new PaperTemplateSearchRequest() : request;
        Specification<PaperTemplate> specification = visibleTo(actor);
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("title")), keyword));
        }
        if (criteria.getStatus() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), criteria.getStatus()));
        }
        if (criteria.getOwnerUserId() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("ownerUserId"), criteria.getOwnerUserId()));
        }
        return paperRepository.findAll(specification, buildPageable(criteria))
                .map(paper -> toResponse(paper, loadQuestions(paper.getId())));
    }

    @Transactional(readOnly = true)
    public PaperTemplateResponse preview(Long paperId, AppUser actor) {
        PaperTemplate paper = findPaper(paperId);
        ensureCanViewOrCopy(paper, actor);
        return toResponse(paper, loadQuestions(paperId));
    }

    @Transactional
    public PaperTemplateResponse copy(Long paperId, CopyPaperTemplateRequest request, AppUser actor) {
        ensureStaff(actor);
        PaperTemplate source = findPaperForUpdate(paperId);
        ensureCanViewOrCopy(source, actor);
        String requestedTitle = request == null ? null : request.getTitle();
        String title = requestedTitle == null
                ? generatedCopyTitle(source.getTitle())
                : requireTitle(requestedTitle);

        PaperTemplate copy = new PaperTemplate();
        copy.setTitle(title);
        copy.setInstructions(source.getInstructions());
        copy.setOwnerUserId(actor.getId());
        copy.setSourcePaperId(source.getId());
        copy.setStatus(PaperTemplateStatus.DRAFT);
        copy.setShuffleQuestions(source.getShuffleQuestions());
        copy.setShuffleOptions(source.getShuffleOptions());
        copy.setTotalScore(zeroScore());
        paperRepository.saveAndFlush(copy);

        List<PaperTemplateQuestion> copiedQuestions = new ArrayList<>();
        for (PaperTemplateQuestion sourceQuestion : loadQuestions(source.getId())) {
            copiedQuestions.add(copySnapshot(sourceQuestion, copy.getId()));
        }
        if (!copiedQuestions.isEmpty()) {
            paperQuestionRepository.saveAllAndFlush(copiedQuestions);
        }
        recalculateTotal(copy, copiedQuestions);
        paperRepository.saveAndFlush(copy);
        return toResponse(copy, copiedQuestions);
    }

    @Transactional
    public PaperTemplateResponse addQuestion(
            Long paperId, AddPaperQuestionRequest request, AppUser actor) {
        PaperTemplate paper = editablePaper(paperId, actor);
        if (request == null || request.getQuestionId() == null) {
            throw new BadRequestException("Question ID is required");
        }
        QuestionBankItem source = questionBankService.findQuestionForPaper(request.getQuestionId());
        accessService.ensureCanUseQuestion(actor, source);
        BigDecimal score = request.getScore() == null ? source.getDefaultScore() : request.getScore();
        validateScore(score);

        List<PaperTemplateQuestion> questions = new ArrayList<>(loadQuestions(paperId));
        int nextOrder = questions.stream()
                .mapToInt(PaperTemplateQuestion::getQuestionOrder)
                .max()
                .orElse(0) + 1;
        PaperTemplateQuestion snapshot = snapshotService.createTemplateQuestionSnapshot(
                source, paperId, nextOrder, score);
        questions.add(snapshot);
        recalculateTotal(paper, questions);
        paperQuestionRepository.saveAndFlush(snapshot);
        paperRepository.saveAndFlush(paper);
        return toResponse(paper, questions);
    }

    @Transactional
    public PaperTemplateResponse reorderQuestions(
            Long paperId, ReorderPaperQuestionsRequest request, AppUser actor) {
        PaperTemplate paper = editablePaper(paperId, actor);
        if (request == null || request.getPaperQuestionIds() == null
                || request.getPaperQuestionIds().isEmpty()) {
            throw new BadRequestException("Paper question IDs are required");
        }
        List<PaperTemplateQuestion> current = loadQuestions(paperId);
        List<PaperTemplateQuestion> ordered = validateAndOrder(current, request.getPaperQuestionIds());
        persistContiguousOrder(ordered);
        return toResponse(paper, ordered);
    }

    @Transactional
    public PaperTemplateResponse updateQuestionScore(
            Long paperId,
            Long paperQuestionId,
            UpdatePaperQuestionScoreRequest request,
            AppUser actor) {
        PaperTemplate paper = editablePaper(paperId, actor);
        if (request == null) {
            throw new BadRequestException("Score request is required");
        }
        validateScore(request.getScore());
        PaperTemplateQuestion question = findPaperQuestion(paperId, paperQuestionId);
        question.setScore(request.getScore());
        List<PaperTemplateQuestion> questions = loadQuestions(paperId);
        recalculateTotal(paper, questions);
        paperQuestionRepository.saveAndFlush(question);
        paperRepository.saveAndFlush(paper);
        return toResponse(paper, questions);
    }

    @Transactional
    public PaperTemplateResponse removeQuestion(Long paperId, Long paperQuestionId, AppUser actor) {
        PaperTemplate paper = editablePaper(paperId, actor);
        PaperTemplateQuestion question = findPaperQuestion(paperId, paperQuestionId);
        List<PaperTemplateQuestion> questions = new ArrayList<>(loadQuestions(paperId));
        questions.removeIf(candidate -> candidate.getId().equals(question.getId()));
        Integer minimumOrder = paperQuestionRepository.findMinimumQuestionOrder(paperId);
        int removedOrder = minimumOrder != null && minimumOrder < 0 ? minimumOrder - 1 : -1;
        question.setQuestionOrder(removedOrder);
        question.setRemovedAt(LocalDateTime.now(clock));
        paperQuestionRepository.saveAndFlush(question);
        if (!questions.isEmpty()) {
            persistContiguousOrder(questions);
        }
        recalculateTotal(paper, questions);
        paperRepository.saveAndFlush(paper);
        return toResponse(paper, questions);
    }

    @Transactional
    public void archive(Long paperId, AppUser actor) {
        PaperTemplate paper = findPaperForUpdate(paperId);
        if (!isAdmin(actor)) {
            accessService.ensureCanManagePaper(actor, paper);
        }
        if (paper.getStatus() == PaperTemplateStatus.ARCHIVED) {
            return;
        }
        paper.setStatus(PaperTemplateStatus.ARCHIVED);
        paper.setArchivedAt(LocalDateTime.now(clock));
        paperRepository.saveAndFlush(paper);
    }

    private PaperTemplate editablePaper(Long paperId, AppUser actor) {
        PaperTemplate paper = findPaperForUpdate(paperId);
        accessService.ensureCanManagePaper(actor, paper);
        ensureEditable(paper);
        return paper;
    }

    private PaperTemplate findPaper(Long paperId) {
        if (paperId == null) {
            throw new BadRequestException("Paper ID is required");
        }
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found: " + paperId));
    }

    private PaperTemplate findPaperForUpdate(Long paperId) {
        if (paperId == null) {
            throw new BadRequestException("Paper ID is required");
        }
        return paperRepository.findByIdForUpdate(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found: " + paperId));
    }

    private PaperTemplateQuestion findPaperQuestion(Long paperId, Long paperQuestionId) {
        if (paperQuestionId == null) {
            throw new BadRequestException("Paper question ID is required");
        }
        PaperTemplateQuestion question = paperQuestionRepository.findByIdAndRemovedAtIsNull(paperQuestionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper question not found: " + paperQuestionId));
        if (!paperId.equals(question.getPaperTemplateId())) {
            throw new BadRequestException("Question does not belong to paper: " + paperId);
        }
        return question;
    }

    private List<PaperTemplateQuestion> loadQuestions(Long paperId) {
        return paperQuestionRepository
                .findByPaperTemplateIdAndRemovedAtIsNullOrderByQuestionOrderAsc(paperId);
    }

    private void ensureCanViewOrCopy(PaperTemplate paper, AppUser actor) {
        ensureStaff(actor);
        boolean owner = actor.getId().equals(paper.getOwnerUserId());
        if (isAdmin(actor) || owner || paper.getStatus() == PaperTemplateStatus.READY) {
            return;
        }
        throw new AccessDeniedException("Not allowed to view this paper");
    }

    private void ensureEditable(PaperTemplate paper) {
        if (paper.getStatus() == PaperTemplateStatus.ARCHIVED) {
            throw new BadRequestException("Archived papers cannot be edited");
        }
    }

    private void ensureStaff(AppUser actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER)) {
            throw new AccessDeniedException("Only teachers and administrators can manage papers");
        }
    }

    private boolean isAdmin(AppUser actor) {
        return actor != null && actor.getRole() == UserRole.ADMIN;
    }

    private List<PaperTemplateQuestion> validateAndOrder(
            List<PaperTemplateQuestion> current, List<Long> requestedIds) {
        if (requestedIds.size() != current.size()) {
            throw new BadRequestException("Reorder must contain every paper question exactly once");
        }
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new BadRequestException("Reorder contains duplicate paper question IDs");
        }
        Map<Long, PaperTemplateQuestion> byId = new HashMap<>();
        for (PaperTemplateQuestion question : current) {
            byId.put(question.getId(), question);
        }
        List<PaperTemplateQuestion> ordered = new ArrayList<>();
        for (Long requestedId : requestedIds) {
            PaperTemplateQuestion question = byId.get(requestedId);
            if (question == null) {
                throw new BadRequestException("Reorder contains a question outside this paper");
            }
            ordered.add(question);
        }
        return ordered;
    }

    private void persistContiguousOrder(List<PaperTemplateQuestion> ordered) {
        int maxOrder = ordered.stream()
                .mapToInt(PaperTemplateQuestion::getQuestionOrder)
                .max()
                .orElse(0);
        int temporaryStart = maxOrder + ordered.size() + 1;
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setQuestionOrder(temporaryStart + index);
        }
        paperQuestionRepository.saveAllAndFlush(ordered);
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setQuestionOrder(index + 1);
        }
        paperQuestionRepository.saveAllAndFlush(ordered);
    }

    private void recalculateTotal(PaperTemplate paper, List<PaperTemplateQuestion> questions) {
        BigDecimal total = zeroScore();
        for (PaperTemplateQuestion question : questions) {
            total = total.add(question.getScore());
        }
        if (total.scale() > 2 || total.compareTo(MAX_SCORE) > 0) {
            throw new BadRequestException("Paper total score must fit NUMERIC(19,2)");
        }
        paper.setTotalScore(total);
    }

    private PaperTemplateQuestion copySnapshot(PaperTemplateQuestion source, Long paperId) {
        PaperTemplateQuestion copy = new PaperTemplateQuestion();
        copy.setPaperTemplateId(paperId);
        copy.setSourceQuestionId(source.getSourceQuestionId());
        copy.setQuestionOrder(source.getQuestionOrder());
        copy.setQuestionType(source.getQuestionType());
        copy.setStem(source.getStem());
        copy.setOptionsJson(source.getOptionsJson());
        copy.setAcceptedAnswersJson(source.getAcceptedAnswersJson());
        copy.setExplanation(source.getExplanation());
        copy.setScore(source.getScore());
        copy.setDictionaryId(source.getDictionaryId());
        copy.setMetaWordId(source.getMetaWordId());
        return copy;
    }

    private Specification<PaperTemplate> visibleTo(AppUser actor) {
        if (isAdmin(actor)) {
            return (root, query, builder) -> builder.conjunction();
        }
        return (root, query, builder) -> builder.or(
                builder.equal(root.get("ownerUserId"), actor.getId()),
                builder.equal(root.get("status"), PaperTemplateStatus.READY));
    }

    private Pageable buildPageable(PaperTemplateSearchRequest request) {
        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid paper search page or size");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private PaperTemplateResponse toResponse(PaperTemplate paper, List<PaperTemplateQuestion> questions) {
        List<PaperTemplateQuestionResponse> questionResponses = questions.stream()
                .sorted((left, right) -> left.getQuestionOrder().compareTo(right.getQuestionOrder()))
                .map(this::toQuestionResponse)
                .toList();
        return new PaperTemplateResponse(
                paper.getId(),
                paper.getTitle(),
                paper.getInstructions(),
                paper.getOwnerUserId(),
                paper.getSourcePaperId(),
                paper.getStatus(),
                paper.getShuffleQuestions(),
                paper.getShuffleOptions(),
                paper.getTotalScore(),
                questionResponses.size(),
                questionResponses,
                paper.getCreatedAt(),
                paper.getUpdatedAt(),
                paper.getArchivedAt());
    }

    private PaperTemplateQuestionResponse toQuestionResponse(PaperTemplateQuestion question) {
        return new PaperTemplateQuestionResponse(
                question.getId(),
                question.getSourceQuestionId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getStem(),
                readMap(question.getOptionsJson()),
                readList(question.getAcceptedAnswersJson()),
                question.getExplanation(),
                question.getScore(),
                question.getDictionaryId(),
                question.getMetaWordId(),
                question.getCreatedAt());
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid paper question options snapshot");
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid paper question answer snapshot");
        }
    }

    private void validateScore(BigDecimal score) {
        if (score == null || score.signum() <= 0 || score.scale() > 2 || score.compareTo(MAX_SCORE) > 0) {
            throw new BadRequestException("Score must be positive and fit NUMERIC(19,2)");
        }
    }

    private String requireTitle(String title) {
        String normalized = trimToNull(title);
        if (normalized == null) {
            throw new BadRequestException("Paper title is required");
        }
        if (normalized.length() > 200) {
            throw new BadRequestException("Paper title must not exceed 200 characters");
        }
        return normalized;
    }

    private String generatedCopyTitle(String sourceTitle) {
        String normalizedSource = requireTitle(sourceTitle);
        String suffix = " (Copy)";
        int sourceLimit = 200 - suffix.length();
        String boundedSource = normalizedSource.length() > sourceLimit
                ? normalizedSource.substring(0, sourceLimit)
                : normalizedSource;
        return requireTitle(boundedSource + suffix);
    }

    private Boolean requireBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value;
    }

    private Boolean defaultFalse(Boolean value) {
        return value == null ? false : value;
    }

    private BigDecimal zeroScore() {
        return BigDecimal.ZERO.setScale(2);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
