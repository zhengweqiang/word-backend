package com.example.words.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.StudentAssignedPaperSummaryResponse;
import com.example.words.dto.SaveStudentPaperDraftRequest;
import com.example.words.dto.StudentPaperAnswerRequest;
import com.example.words.dto.StudentPaperAnswerResponse;
import com.example.words.dto.StudentPaperAttemptResponse;
import com.example.words.dto.StudentPaperQuestionResponse;
import com.example.words.dto.StudentPaperResultQuestionResponse;
import com.example.words.dto.StudentPaperResultResponse;
import com.example.words.dto.SubmitStudentPaperRequest;
import com.example.words.dto.SubmitStudentPaperResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ConflictException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.PointSourceType;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAnswer;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.StudentPaperAnswerRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

@Service
public class StudentPaperAttemptService {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final String EXAM_RULE_CODE = "EXAM";

    private final PaperReleaseRepository releaseRepository;
    private final PaperReleaseQuestionRepository releaseQuestionRepository;
    private final StudentPaperAttemptRepository attemptRepository;
    private final StudentPaperAnswerRepository answerRepository;
    private final ExamPaperAnswerNormalizer answerNormalizer;
    private final ObjectMapper objectMapper;
    private final StudentPointEventPublisher studentPointEventPublisher;
    private final Clock clock;

    public StudentPaperAttemptService(
            PaperReleaseRepository releaseRepository,
            PaperReleaseQuestionRepository releaseQuestionRepository,
            StudentPaperAttemptRepository attemptRepository,
            StudentPaperAnswerRepository answerRepository,
            ExamPaperAnswerNormalizer answerNormalizer,
            ObjectMapper objectMapper,
            StudentPointEventPublisher studentPointEventPublisher,
            Clock clock) {
        this.releaseRepository = releaseRepository;
        this.releaseQuestionRepository = releaseQuestionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.answerNormalizer = answerNormalizer;
        this.objectMapper = objectMapper;
        this.studentPointEventPublisher = studentPointEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public List<StudentAssignedPaperSummaryResponse> listAssigned(AppUser actor) {
        AppUser student = requireStudent(actor);
        List<StudentPaperAttempt> attempts = attemptRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, StudentAssignedPaperSummaryResponse> responsesByAttempt = new LinkedHashMap<>();
        attempts.stream()
                .sorted(Comparator.comparing(StudentPaperAttempt::getPaperReleaseId)
                        .thenComparing(StudentPaperAttempt::getId))
                .forEach(shell -> observeAssignedAttempt(shell, student, now)
                        .ifPresent(response -> responsesByAttempt.put(shell.getId(), response)));
        return attempts.stream()
                .map(attempt -> responsesByAttempt.get(attempt.getId()))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public StudentPaperAttemptResponse open(Long attemptId, AppUser actor) {
        LockedAttempt locked = lockAttempt(attemptId, actor);
        PaperRelease release = locked.release();
        StudentPaperAttempt attempt = locked.attempt();
        LocalDateTime now = LocalDateTime.now(clock);
        activateStartedRelease(release, now);
        ensureOpenableRelease(release);
        if (release.getStatus() == PaperReleaseStatus.SUPERSEDED
                && !Boolean.TRUE.equals(release.getShowSupersededToStudents())) {
            throw new AccessDeniedException("Superseded paper release is hidden from students");
        }
        if (!hasStarted(release, now)) {
            return toAttemptResponse(release, attempt, false, List.of(), List.of());
        }
        applyOverdueState(attempt, release, now);
        if (release.getStatus() == PaperReleaseStatus.SUPERSEDED) {
            List<PaperReleaseQuestion> questions =
                    releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(release.getId());
            List<StudentPaperAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
            return toAttemptResponse(release, attempt, false, questions, answers);
        }
        if (attempt.getStatus() == StudentPaperAttemptStatus.NOT_STARTED) {
            attempt.setStatus(StudentPaperAttemptStatus.IN_PROGRESS);
            attempt.setOpenedAt(now);
            attemptRepository.saveAndFlush(attempt);
        }
        List<PaperReleaseQuestion> questions =
                releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(release.getId());
        List<StudentPaperAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        return toAttemptResponse(release, attempt, canAnswer(release, attempt, now), questions, answers);
    }

    @Transactional
    public StudentPaperAttemptResponse saveDraft(
            Long attemptId, SaveStudentPaperDraftRequest request, AppUser actor) {
        if (request == null || request.getExpectedVersion() == null || request.getAnswers() == null) {
            throw new BadRequestException("Draft version and answers are required");
        }
        LockedAttempt locked = lockAttempt(attemptId, actor);
        PaperRelease release = locked.release();
        StudentPaperAttempt attempt = locked.attempt();
        LocalDateTime now = LocalDateTime.now(clock);
        activateStartedRelease(release, now);
        ensureAnswerable(release, attempt, now);
        if (!Objects.equals(request.getExpectedVersion(), attempt.getVersion())) {
            throw new ConflictException("Paper draft was updated by another request");
        }

        List<PaperReleaseQuestion> questions =
                releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(release.getId());
        Map<Long, PaperReleaseQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(PaperReleaseQuestion::getId, Function.identity()));
        List<StudentPaperAnswer> existingAnswers = answerRepository.findByAttemptId(attempt.getId());
        Map<Long, StudentPaperAnswer> answersByQuestion = existingAnswers.stream()
                .collect(Collectors.toMap(
                        StudentPaperAnswer::getReleaseQuestionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        LinkedHashSet<Long> requestQuestionIds = new LinkedHashSet<>();
        List<StudentPaperAnswer> changedAnswers = new java.util.ArrayList<>();
        for (StudentPaperAnswerRequest answerRequest : request.getAnswers()) {
            if (answerRequest == null || answerRequest.getReleaseQuestionId() == null) {
                throw new BadRequestException("Each draft answer requires a release question ID");
            }
            if (!requestQuestionIds.add(answerRequest.getReleaseQuestionId())) {
                throw new BadRequestException("Draft contains duplicate answers for one question");
            }
            PaperReleaseQuestion question = questionsById.get(answerRequest.getReleaseQuestionId());
            if (question == null) {
                throw new BadRequestException("Question does not belong to this paper release");
            }
            StudentPaperAnswer answer = answersByQuestion.computeIfAbsent(
                    question.getId(), ignored -> newAnswer(attempt, question));
            applyDraftAnswer(answer, question, answerRequest, now);
            changedAnswers.add(answer);
        }
        if (!changedAnswers.isEmpty()) {
            answerRepository.saveAllAndFlush(changedAnswers);
        }
        applyOverdueState(attempt, release, now);
        if (attempt.getStatus() == StudentPaperAttemptStatus.NOT_STARTED) {
            attempt.setStatus(StudentPaperAttemptStatus.IN_PROGRESS);
            attempt.setOpenedAt(now);
        }
        attempt.setAnsweredCount((int) answersByQuestion.values().stream()
                .filter(this::isAnswered)
                .count());
        attempt.setLastDraftSavedAt(now);
        attemptRepository.saveAndFlush(attempt);
        return toAttemptResponse(
                release, attempt, true, questions, List.copyOf(answersByQuestion.values()));
    }

    @Transactional
    public SubmitStudentPaperResponse submit(
            Long attemptId, SubmitStudentPaperRequest request, AppUser actor) {
        if (request == null || request.getExpectedVersion() == null || request.getAnswers() == null) {
            throw new BadRequestException("Submission version and answers are required");
        }
        LockedAttempt locked = lockAttempt(attemptId, actor);
        PaperRelease release = locked.release();
        StudentPaperAttempt attempt = locked.attempt();
        if (isFinal(attempt.getStatus())) {
            return toSubmitResponse(release, attempt, true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        activateStartedRelease(release, now);
        ensureAnswerable(release, attempt, now);
        if (!Objects.equals(request.getExpectedVersion(), attempt.getVersion())) {
            throw new ConflictException("Paper attempt was updated by another request");
        }

        List<PaperReleaseQuestion> questions =
                releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(release.getId());
        Map<Long, PaperReleaseQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(PaperReleaseQuestion::getId, Function.identity()));
        Map<Long, StudentPaperAnswer> answersByQuestion = answerRepository.findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(
                        StudentPaperAnswer::getReleaseQuestionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        applySubmissionAnswers(attempt, request.getAnswers(), questionsById, answersByQuestion, now);
        for (PaperReleaseQuestion question : questions) {
            answersByQuestion.computeIfAbsent(question.getId(), ignored -> {
                StudentPaperAnswer blank = newAnswer(attempt, question);
                applyDraftAnswer(
                        blank,
                        question,
                        new StudentPaperAnswerRequest(question.getId(), List.of(), List.of()),
                        now);
                return blank;
            });
        }
        for (PaperReleaseQuestion question : questions) {
            validateStoredAnswer(question, answersByQuestion.get(question.getId()));
        }
        if (release.getBlankAnswerPolicy() == com.example.words.model.PaperBlankAnswerPolicy.REQUIRE_ALL_ANSWERED
                && answersByQuestion.values().stream().anyMatch(answer -> !isAnswered(answer))) {
            throw new BadRequestException("All paper questions must be answered before final submission");
        }

        int answeredCount = 0;
        int correctCount = 0;
        BigDecimal earnedScore = BigDecimal.ZERO;
        List<StudentPaperAnswer> finalizedAnswers = new java.util.ArrayList<>();
        for (PaperReleaseQuestion question : questions) {
            StudentPaperAnswer answer = answersByQuestion.get(question.getId());
            boolean answered = isAnswered(answer);
            boolean correct = answered && isCorrect(question, answer);
            BigDecimal questionEarned = correct ? question.getScore() : BigDecimal.ZERO;
            answer.setCorrect(correct);
            answer.setEarnedScore(questionEarned.setScale(2, RoundingMode.HALF_UP));
            answer.setFinalizedAt(now);
            if (answered) {
                answeredCount++;
            }
            if (correct) {
                correctCount++;
                earnedScore = earnedScore.add(question.getScore());
            }
            finalizedAnswers.add(answer);
        }
        answerRepository.saveAllAndFlush(finalizedAnswers);
        attempt.setAnsweredCount(answeredCount);
        attempt.setCorrectCount(correctCount);
        attempt.setEarnedScore(earnedScore.setScale(2, RoundingMode.HALF_UP));
        attempt.setTotalScore(release.getTotalScore());
        attempt.setScorePercentage(calculatePercentage(earnedScore, release.getTotalScore()));
        attempt.setSubmittedAt(now);
        attempt.setStatus(isLate(release, now)
                ? StudentPaperAttemptStatus.SUBMITTED_LATE
                : StudentPaperAttemptStatus.SUBMITTED);
        attemptRepository.saveAndFlush(attempt);
        publishOnTimeSubmissionPointEvent(attempt);
        return toSubmitResponse(release, attempt, false);
    }

    @Transactional(readOnly = true)
    public StudentPaperResultResponse getResult(Long attemptId, AppUser actor) {
        AppUser student = requireStudent(actor);
        StudentPaperAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new AccessDeniedException("Paper attempt is not assigned to this student"));
        if (!isFinal(attempt.getStatus())) {
            throw new ConflictException("Paper result is unavailable before final submission");
        }
        PaperRelease release = releaseRepository.findById(attempt.getPaperReleaseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper release not found: " + attempt.getPaperReleaseId()));
        if (isHiddenSuperseded(release)) {
            throw new AccessDeniedException("Superseded paper release is hidden from students");
        }
        return toResultResponse(release, attempt);
    }

    private Optional<StudentAssignedPaperSummaryResponse> observeAssignedAttempt(
            StudentPaperAttempt shell, AppUser student, LocalDateTime now) {
        Optional<PaperRelease> releaseResult = releaseRepository.findByIdForUpdate(shell.getPaperReleaseId());
        if (releaseResult.isEmpty()) {
            return Optional.empty();
        }
        PaperRelease release = releaseResult.get();
        activateStartedRelease(release, now);
        if (!visibleInStudentList(release)) {
            return Optional.empty();
        }
        Optional<StudentPaperAttempt> attemptResult = attemptRepository
                .findByIdAndStudentIdForUpdate(shell.getId(), student.getId());
        if (attemptResult.isEmpty()) {
            return Optional.empty();
        }
        StudentPaperAttempt attempt = attemptResult.get();
        if (!release.getId().equals(attempt.getPaperReleaseId())) {
            throw new IllegalStateException("Attempt release changed while acquiring locks");
        }
        applyOverdueState(attempt, release, now);
        return Optional.of(toSummary(attempt, release, now));
    }

    private LockedAttempt lockAttempt(Long attemptId, AppUser actor) {
        AppUser student = requireStudent(actor);
        Long releaseId = attemptRepository.findPaperReleaseIdByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new AccessDeniedException("Paper attempt is not assigned to this student"));
        PaperRelease release = releaseRepository.findByIdForUpdate(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper release not found: " + releaseId));
        StudentPaperAttempt attempt = attemptRepository.findByIdAndStudentIdForUpdate(attemptId, student.getId())
                .orElseThrow(() -> new AccessDeniedException("Paper attempt is not assigned to this student"));
        if (!releaseId.equals(attempt.getPaperReleaseId())) {
            throw new IllegalStateException("Attempt release changed while acquiring locks");
        }
        return new LockedAttempt(release, attempt);
    }

    private AppUser requireStudent(AppUser actor) {
        if (actor == null || actor.getId() == null || actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can access assigned papers");
        }
        return actor;
    }

    private boolean visibleInStudentList(PaperRelease release) {
        return release.getStatus() != PaperReleaseStatus.WITHDRAWN
                && release.getStatus() != PaperReleaseStatus.INVALIDATED
                && !isHiddenSuperseded(release);
    }

    private boolean isHiddenSuperseded(PaperRelease release) {
        return release.getStatus() == PaperReleaseStatus.SUPERSEDED
                && !Boolean.TRUE.equals(release.getShowSupersededToStudents());
    }

    private void ensureOpenableRelease(PaperRelease release) {
        if (release.getStatus() == PaperReleaseStatus.WITHDRAWN
                || release.getStatus() == PaperReleaseStatus.INVALIDATED) {
            throw new BadRequestException("Paper release is not available");
        }
    }

    private void ensureAnswerable(
            PaperRelease release, StudentPaperAttempt attempt, LocalDateTime now) {
        if (!hasStarted(release, now)) {
            throw new BadRequestException("Paper has not started yet");
        }
        ensureOpenableRelease(release);
        if (release.getStatus() == PaperReleaseStatus.SUPERSEDED) {
            throw new BadRequestException("Superseded paper release cannot be answered");
        }
        if (isFinal(attempt.getStatus())) {
            throw new ConflictException("Final-submitted paper attempt is locked");
        }
        if (attempt.getStatus() == StudentPaperAttemptStatus.INVALIDATED) {
            throw new BadRequestException("Paper attempt is invalidated");
        }
    }

    private StudentPaperAnswer newAnswer(
            StudentPaperAttempt attempt, PaperReleaseQuestion question) {
        StudentPaperAnswer answer = new StudentPaperAnswer();
        answer.setAttemptId(attempt.getId());
        answer.setPaperReleaseId(attempt.getPaperReleaseId());
        answer.setReleaseQuestionId(question.getId());
        return answer;
    }

    private void applyDraftAnswer(
            StudentPaperAnswer answer,
            PaperReleaseQuestion question,
            StudentPaperAnswerRequest request,
            LocalDateTime now) {
        NormalizedAnswer normalized = normalizeAndValidateAnswer(question, request);
        answer.setSelectedAnswersJson(writeList(normalized.selectedAnswers()));
        answer.setBlankAnswersJson(writeList(normalized.blankAnswers()));
        answer.setCorrect(null);
        answer.setEarnedScore(null);
        answer.setFinalizedAt(null);
        answer.setAnsweredAt(isAnswered(
                normalized.selectedAnswers(), normalized.blankAnswers()) ? now : null);
    }

    private void applySubmissionAnswers(
            StudentPaperAttempt attempt,
            List<StudentPaperAnswerRequest> requests,
            Map<Long, PaperReleaseQuestion> questionsById,
            Map<Long, StudentPaperAnswer> answersByQuestion,
            LocalDateTime now) {
        LinkedHashSet<Long> requestQuestionIds = new LinkedHashSet<>();
        for (StudentPaperAnswerRequest request : requests) {
            if (request == null || request.getReleaseQuestionId() == null) {
                throw new BadRequestException("Each submission answer requires a release question ID");
            }
            if (!requestQuestionIds.add(request.getReleaseQuestionId())) {
                throw new BadRequestException("Submission contains duplicate answers for one question");
            }
            PaperReleaseQuestion question = questionsById.get(request.getReleaseQuestionId());
            if (question == null) {
                throw new BadRequestException("Question does not belong to this paper release");
            }
            StudentPaperAnswer answer = answersByQuestion.computeIfAbsent(
                    question.getId(), ignored -> newAnswer(attempt, question));
            applyDraftAnswer(answer, question, request, now);
        }
    }

    private boolean isCorrect(PaperReleaseQuestion question, StudentPaperAnswer answer) {
        List<String> acceptedAnswers = readList(question.getAcceptedAnswersJson());
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE
                || question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            List<String> expected = answerNormalizer.normalizeOptionKeys(acceptedAnswers);
            List<String> actual = answerNormalizer.normalizeOptionKeys(
                    readList(answer.getSelectedAnswersJson()));
            return actual.equals(expected);
        }
        List<String> normalizedAccepted = acceptedAnswers.stream()
                .map(answerNormalizer::normalizeBlankAnswer)
                .filter(Objects::nonNull)
                .toList();
        List<String> submitted = readList(answer.getBlankAnswersJson());
        return submitted.size() == 1
                && normalizedAccepted.contains(answerNormalizer.normalizeBlankAnswer(submitted.get(0)));
    }

    private NormalizedAnswer normalizeAndValidateAnswer(
            PaperReleaseQuestion question, StudentPaperAnswerRequest request) {
        List<String> rawSelected = request.getSelectedAnswers() == null
                ? List.of() : request.getSelectedAnswers();
        List<String> rawBlanks = request.getBlankAnswers() == null
                ? List.of() : request.getBlankAnswers();
        if (rawSelected.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new BadRequestException("Option answers must contain nonblank option keys");
        }
        List<String> selectedAnswers = answerNormalizer.normalizeOptionKeys(rawSelected);
        if (selectedAnswers.size() != rawSelected.size()) {
            throw new BadRequestException("Option answers must contain distinct option keys");
        }
        if (question.getQuestionType() == QuestionType.FILL_IN_BLANK) {
            if (!rawSelected.isEmpty()) {
                throw new BadRequestException("Fill-in-the-blank answers cannot contain option keys");
            }
            if (rawBlanks.size() > 1) {
                throw new BadRequestException("Fill-in-the-blank questions accept at most one answer value");
            }
            return new NormalizedAnswer(List.of(), normalizeDraftBlanks(rawBlanks));
        }
        if (!rawBlanks.isEmpty()) {
            throw new BadRequestException("Choice answers cannot contain blank values");
        }
        validateChoiceKeys(question, selectedAnswers);
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE && selectedAnswers.size() > 1) {
            throw new BadRequestException("Single-choice questions accept at most one option key");
        }
        return new NormalizedAnswer(selectedAnswers, List.of());
    }

    private void validateStoredAnswer(PaperReleaseQuestion question, StudentPaperAnswer answer) {
        List<String> selectedAnswers = readList(answer.getSelectedAnswersJson());
        List<String> blankAnswers = readList(answer.getBlankAnswersJson());
        if (question.getQuestionType() == QuestionType.FILL_IN_BLANK) {
            if (!selectedAnswers.isEmpty() || blankAnswers.size() > 1) {
                throw new BadRequestException("Stored fill-in-the-blank answer has an invalid shape");
            }
            return;
        }
        if (!blankAnswers.isEmpty()) {
            throw new BadRequestException("Stored choice answer has an invalid shape");
        }
        List<String> normalizedSelected = answerNormalizer.normalizeOptionKeys(selectedAnswers);
        if (normalizedSelected.size() != selectedAnswers.size()) {
            throw new BadRequestException("Stored choice answer contains duplicate option keys");
        }
        validateChoiceKeys(question, normalizedSelected);
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE
                && normalizedSelected.size() > 1) {
            throw new BadRequestException("Stored single-choice answer has an invalid shape");
        }
    }

    private void validateChoiceKeys(
            PaperReleaseQuestion question, List<String> selectedAnswers) {
        List<String> frozenOptionKeys = answerNormalizer.normalizeOptionKeys(
                readMap(question.getOptionsJson()).keySet());
        if (!frozenOptionKeys.containsAll(selectedAnswers)) {
            throw new BadRequestException("Answer contains an option key not present in the published question");
        }
    }

    private BigDecimal calculatePercentage(BigDecimal earnedScore, BigDecimal totalScore) {
        if (totalScore == null || totalScore.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return earnedScore.multiply(BigDecimal.valueOf(100))
                .divide(totalScore, 2, RoundingMode.HALF_UP);
    }

    private boolean isLate(PaperRelease release, LocalDateTime submittedAt) {
        return release.getDeadline() != null && submittedAt.isAfter(release.getDeadline());
    }

    public static PaperAttemptPointSourceIdentity pointSourceIdentity(
            Long attemptId, StudentPaperAttemptStatus submissionStatus) {
        if (attemptId == null) {
            throw new IllegalArgumentException("Paper attempt id is required for point source identity");
        }
        if (submissionStatus != StudentPaperAttemptStatus.SUBMITTED
                && submissionStatus != StudentPaperAttemptStatus.SUBMITTED_LATE) {
            throw new IllegalArgumentException("Paper attempt point source requires a final submission status");
        }
        return new PaperAttemptPointSourceIdentity(
                PointSourceType.PAPER_RELEASE_ATTEMPT,
                attemptId,
                "paper-release-attempt:" + attemptId + ":" + submissionStatus.name());
    }

    private void publishOnTimeSubmissionPointEvent(StudentPaperAttempt attempt) {
        if (attempt.getStatus() != StudentPaperAttemptStatus.SUBMITTED) {
            return;
        }
        PaperAttemptPointSourceIdentity identity = pointSourceIdentity(attempt.getId(), attempt.getStatus());
        studentPointEventPublisher.publishAfterCommit(new StudentPointEventPublisher.PublishRequest(
                attempt.getStudentId(),
                identity.sourceId(),
                identity.sourceKey(),
                EXAM_RULE_CODE
        ));
    }

    private List<String> normalizeDraftBlanks(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private boolean isAnswered(StudentPaperAnswer answer) {
        return isAnswered(
                readList(answer.getSelectedAnswersJson()),
                readList(answer.getBlankAnswersJson()));
    }

    private boolean isAnswered(List<String> selectedAnswers, List<String> blankAnswers) {
        return !selectedAnswers.isEmpty() || !blankAnswers.isEmpty();
    }

    private void applyOverdueState(
            StudentPaperAttempt attempt, PaperRelease release, LocalDateTime now) {
        if (!isFinal(attempt.getStatus())
                && attempt.getStatus() != StudentPaperAttemptStatus.INVALIDATED
                && attempt.getStatus() != StudentPaperAttemptStatus.OVERDUE
                && release.getDeadline() != null
                && now.isAfter(release.getDeadline())) {
            attempt.setStatus(StudentPaperAttemptStatus.OVERDUE);
            attemptRepository.saveAndFlush(attempt);
        }
    }

    private void activateStartedRelease(PaperRelease release, LocalDateTime now) {
        if (release.getStatus() == PaperReleaseStatus.SCHEDULED && hasStarted(release, now)) {
            release.setStatus(PaperReleaseStatus.OPEN);
            releaseRepository.saveAndFlush(release);
        }
    }

    private StudentAssignedPaperSummaryResponse toSummary(
            StudentPaperAttempt attempt, PaperRelease release, LocalDateTime now) {
        StudentPaperAttemptStatus effectiveStatus = effectiveStatus(attempt, release, now);
        return new StudentAssignedPaperSummaryResponse(
                attempt.getId(),
                release.getId(),
                release.getTitle(),
                release.getInstructions(),
                release.getStatus(),
                effectiveStatus,
                release.getQuestionCount(),
                release.getTotalScore(),
                release.getStartTime(),
                release.getDeadline(),
                canAnswer(release, effectiveStatus, now),
                isFinal(effectiveStatus) && resultVisible(release));
    }

    private StudentPaperAttemptResponse toAttemptResponse(
            PaperRelease release,
            StudentPaperAttempt attempt,
            boolean answerable,
            List<PaperReleaseQuestion> questions,
            List<StudentPaperAnswer> answers) {
        return new StudentPaperAttemptResponse(
                attempt.getId(),
                release.getId(),
                release.getTitle(),
                release.getInstructions(),
                release.getStatus(),
                attempt.getStatus(),
                attempt.getVersion(),
                release.getQuestionCount(),
                release.getTotalScore(),
                release.getStartTime(),
                release.getDeadline(),
                release.getBlankAnswerPolicy(),
                answerable,
                questions.stream().map(this::toQuestionResponse).toList(),
                answers.stream().map(this::toAnswerResponse).toList());
    }

    private StudentPaperQuestionResponse toQuestionResponse(PaperReleaseQuestion question) {
        return new StudentPaperQuestionResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getCategory(),
                question.getStem(),
                readMap(question.getOptionsJson()),
                question.getScore());
    }

    private StudentPaperAnswerResponse toAnswerResponse(StudentPaperAnswer answer) {
        return new StudentPaperAnswerResponse(
                answer.getReleaseQuestionId(),
                readList(answer.getSelectedAnswersJson()),
                readList(answer.getBlankAnswersJson()));
    }

    private boolean hasStarted(PaperRelease release, LocalDateTime now) {
        return release.getStartTime() == null || !now.isBefore(release.getStartTime());
    }

    private boolean canAnswer(PaperRelease release, StudentPaperAttempt attempt, LocalDateTime now) {
        return canAnswer(release, attempt.getStatus(), now);
    }

    private boolean canAnswer(
            PaperRelease release, StudentPaperAttemptStatus attemptStatus, LocalDateTime now) {
        return hasStarted(release, now)
                && (release.getStatus() == PaperReleaseStatus.OPEN
                    || release.getStatus() == PaperReleaseStatus.SCHEDULED)
                && !isFinal(attemptStatus)
                && attemptStatus != StudentPaperAttemptStatus.INVALIDATED;
    }

    private StudentPaperAttemptStatus effectiveStatus(
            StudentPaperAttempt attempt, PaperRelease release, LocalDateTime now) {
        if (!isFinal(attempt.getStatus())
                && attempt.getStatus() != StudentPaperAttemptStatus.INVALIDATED
                && release.getDeadline() != null
                && now.isAfter(release.getDeadline())) {
            return StudentPaperAttemptStatus.OVERDUE;
        }
        return attempt.getStatus();
    }

    private boolean isFinal(StudentPaperAttemptStatus status) {
        return status == StudentPaperAttemptStatus.SUBMITTED
                || status == StudentPaperAttemptStatus.SUBMITTED_LATE;
    }

    private boolean resultVisible(PaperRelease release) {
        return PaperResultVisibilityPolicy.isScoreVisible(release);
    }

    private SubmitStudentPaperResponse toSubmitResponse(
            PaperRelease release, StudentPaperAttempt attempt, boolean idempotent) {
        return new SubmitStudentPaperResponse(
                attempt.getId(),
                attempt.getStatus(),
                attempt.getVersion(),
                attempt.getSubmittedAt(),
                idempotent,
                toResultResponse(release, attempt));
    }

    private StudentPaperResultResponse toResultResponse(
            PaperRelease release, StudentPaperAttempt attempt) {
        boolean scoreVisible = resultVisible(release);
        boolean answersVisible = scoreVisible
                && release.getResultVisibility() != PaperResultVisibility.SCORE_ONLY;
        List<StudentPaperResultQuestionResponse> questionResults = List.of();
        if (answersVisible) {
            List<PaperReleaseQuestion> questions =
                    releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(release.getId());
            Map<Long, StudentPaperAnswer> answers = answerRepository.findByAttemptId(attempt.getId()).stream()
                    .collect(Collectors.toMap(StudentPaperAnswer::getReleaseQuestionId, Function.identity()));
            questionResults = questions.stream()
                    .map(question -> toResultQuestion(question, answers.get(question.getId())))
                    .toList();
        }
        return new StudentPaperResultResponse(
                attempt.getId(),
                release.getId(),
                attempt.getStatus(),
                attempt.getSubmittedAt(),
                scoreVisible,
                answersVisible,
                scoreVisible ? attempt.getEarnedScore() : null,
                scoreVisible ? attempt.getTotalScore() : null,
                scoreVisible ? attempt.getScorePercentage() : null,
                scoreVisible ? attempt.getAnsweredCount() : null,
                scoreVisible ? attempt.getCorrectCount() : null,
                questionResults);
    }

    private StudentPaperResultQuestionResponse toResultQuestion(
            PaperReleaseQuestion question, StudentPaperAnswer answer) {
        return new StudentPaperResultQuestionResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getCategory(),
                question.getStem(),
                readMap(question.getOptionsJson()),
                answer == null ? List.of() : readList(answer.getSelectedAnswersJson()),
                answer == null ? List.of() : readList(answer.getBlankAnswersJson()),
                answer == null ? false : answer.getCorrect(),
                answer == null ? BigDecimal.ZERO : answer.getEarnedScore(),
                question.getScore(),
                readList(question.getAcceptedAnswersJson()),
                question.getExplanation());
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, STRING_MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read release question options", exception);
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read student paper answer", exception);
        }
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist student paper answer", exception);
        }
    }

    private record LockedAttempt(PaperRelease release, StudentPaperAttempt attempt) {
    }

    private record NormalizedAnswer(List<String> selectedAnswers, List<String> blankAnswers) {
    }

    public record PaperAttemptPointSourceIdentity(
            PointSourceType sourceType,
            Long sourceId,
            String sourceKey
    ) {
    }
}
