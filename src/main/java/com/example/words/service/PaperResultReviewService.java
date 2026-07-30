package com.example.words.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.words.dto.PaperReleaseQuestionStatResponse;
import com.example.words.dto.PaperReleaseResponse;
import com.example.words.dto.PaperReleaseResultOverviewResponse;
import com.example.words.dto.PaperReleaseStudentResultResponse;
import com.example.words.dto.PaperReleaseTargetResponse;
import com.example.words.dto.ReleasePaperResultsRequest;
import com.example.words.dto.StudentPaperResultQuestionResponse;
import com.example.words.exception.ConflictException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperReleaseTarget;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.StudentPaperAnswer;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudentPaperAnswerRepository;
import com.example.words.repository.StudentPaperAttemptRepository;
import com.example.words.repository.TeacherStudentRelationRepository;

@Service
public class PaperResultReviewService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() { };

    private final PaperReleaseRepository releaseRepository;
    private final PaperReleaseQuestionRepository questionRepository;
    private final StudentPaperAttemptRepository attemptRepository;
    private final StudentPaperAnswerRepository answerRepository;
    private final PaperReleaseTargetRepository targetRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final TeacherStudentRelationRepository relationRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperResultReviewService(
            PaperReleaseRepository releaseRepository,
            PaperReleaseQuestionRepository questionRepository,
            StudentPaperAttemptRepository attemptRepository,
            StudentPaperAnswerRepository answerRepository,
            PaperReleaseTargetRepository targetRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            TeacherStudentRelationRepository relationRepository,
            AppUserRepository appUserRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.releaseRepository = releaseRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.targetRepository = targetRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.relationRepository = relationRepository;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaperReleaseResponse> listReleases(AppUser actor) {
        ensureReviewActor(actor);
        List<PaperReleaseResponse> releases = new ArrayList<>();
        for (PaperRelease release : releaseRepository.findAll(
                Sort.by(Sort.Direction.DESC, "createdAt"))) {
            try {
                releases.add(toReleaseResponse(release, visibleTargets(release, actor)));
            } catch (AccessDeniedException ignored) {
                // Inaccessible releases are omitted so the list never discloses their metadata.
            }
        }
        return releases;
    }

    @Transactional(readOnly = true)
    public PaperReleaseResponse getRelease(Long releaseId, AppUser actor) {
        ensureReviewActor(actor);
        PaperRelease release = requireRelease(releaseId);
        return toReleaseResponse(release, visibleTargets(release, actor));
    }

    @Transactional(readOnly = true)
    public PaperReleaseResultOverviewResponse getOverview(Long releaseId, AppUser actor) {
        PaperRelease release = requireRelease(releaseId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<StudentPaperAttempt> attempts = visibleAttempts(release, actor);
        ensureIncludedInDefaultStatistics(release);
        return buildOverview(release, attempts, now);
    }

    @Transactional(readOnly = true)
    public PaperReleaseStudentResultResponse getStudentResult(
            Long releaseId, Long attemptId, AppUser actor) {
        PaperRelease release = requireRelease(releaseId);
        StudentPaperAttempt attempt = attemptRepository.findById(attemptId)
                .filter(candidate -> releaseId.equals(candidate.getPaperReleaseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Paper attempt not found: " + attemptId));
        ensureCanReviewStudent(release, actor, attempt.getStudentId());

        Map<Long, StudentPaperAnswer> answers = answerRepository.findByAttemptIdIn(List.of(attemptId)).stream()
                .collect(Collectors.toMap(StudentPaperAnswer::getReleaseQuestionId, Function.identity()));
        List<StudentPaperResultQuestionResponse> questions = questionRepository
                .findByPaperReleaseIdOrderByQuestionOrderAsc(releaseId).stream()
                .map(question -> toQuestionResult(question, answers.get(question.getId())))
                .toList();
        StudentPaperAttemptStatus effectiveStatus = effectiveStatus(
                release, attempt, LocalDateTime.now(clock));
        String studentUsername = studentUsernames(List.of(attempt)).get(attempt.getStudentId());
        return new PaperReleaseStudentResultResponse(
                releaseId,
                attempt.getId(),
                attempt.getStudentId(),
                studentUsername,
                effectiveStatus,
                effectiveStatus == StudentPaperAttemptStatus.SUBMITTED_LATE,
                attempt.getAnsweredCount(),
                attempt.getCorrectCount(),
                attempt.getEarnedScore(),
                attempt.getTotalScore(),
                attempt.getScorePercentage(),
                attempt.getSubmittedAt(),
                questions);
    }

    @Transactional(readOnly = true)
    public List<PaperReleaseQuestionStatResponse> getQuestionStatistics(Long releaseId, AppUser actor) {
        PaperRelease release = requireRelease(releaseId);
        List<StudentPaperAttempt> attempts = visibleAttempts(release, actor).stream()
                .filter(this::isValidFinalSubmission)
                .toList();
        ensureIncludedInDefaultStatistics(release);

        Map<Long, List<StudentPaperAnswer>> answersByQuestion = new HashMap<>();
        List<Long> attemptIds = attempts.stream().map(StudentPaperAttempt::getId).toList();
        List<StudentPaperAnswer> answers = attemptIds.isEmpty()
                ? List.of()
                : answerRepository.findByAttemptIdIn(attemptIds);
        for (StudentPaperAnswer answer : answers) {
            answersByQuestion.computeIfAbsent(answer.getReleaseQuestionId(), ignored -> new ArrayList<>())
                    .add(answer);
        }

        return questionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(releaseId).stream()
                .map(question -> toQuestionStat(question, attempts.size(), answersByQuestion.get(question.getId())))
                .toList();
    }

    @Transactional
    public PaperReleaseResultOverviewResponse releaseResults(
            Long releaseId, ReleasePaperResultsRequest request, AppUser actor) {
        PaperRelease release = releaseRepository.findByIdForUpdate(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper release not found: " + releaseId));
        ensureCanChangeGlobalVisibility(release, actor);
        ensureIncludedInDefaultStatistics(release);
        if (request.getResultVisibility() == PaperResultVisibility.HIDDEN_UNTIL_RELEASED) {
            throw new ConflictException("Released results must expose a score visibility mode");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        release.setResultVisibility(request.getResultVisibility());
        release.setResultsReleasedAt(now);
        release.setResultsReleasedByUserId(actor.getId());
        releaseRepository.save(release);
        List<StudentPaperAttempt> attempts = visibleAttempts(release, actor);
        return buildOverview(release, attempts, now);
    }

    private PaperReleaseResultOverviewResponse buildOverview(
            PaperRelease release,
            List<StudentPaperAttempt> attempts,
            LocalDateTime now) {
        int notStarted = 0;
        int inProgress = 0;
        int overdue = 0;
        int submitted = 0;
        int submittedLate = 0;
        for (StudentPaperAttempt attempt : attempts) {
            switch (effectiveStatus(release, attempt, now)) {
                case NOT_STARTED -> notStarted++;
                case IN_PROGRESS -> inProgress++;
                case OVERDUE -> overdue++;
                case SUBMITTED -> submitted++;
                case SUBMITTED_LATE -> submittedLate++;
                case INVALIDATED -> {
                    // Invalid attempts are removed by visibleAttempts.
                }
            }
        }
        Map<Long, String> studentUsernames = studentUsernames(attempts);
        return new PaperReleaseResultOverviewResponse(
                release.getId(),
                release.getTitle(),
                release.getStatus(),
                attempts.size(),
                notStarted,
                inProgress,
                overdue,
                submitted,
                submittedLate,
                submitted + submittedLate,
                release.getResultVisibility(),
                release.getResultsReleasedAt() != null,
                release.getResultsReleasedAt(),
                release.getResultsReleasedByUserId(),
                attempts.stream()
                        .map(attempt -> toStudentSummary(
                                release,
                                attempt,
                                now,
                                studentUsernames.get(attempt.getStudentId())))
                        .toList());
    }

    private PaperReleaseStudentResultResponse toStudentSummary(
            PaperRelease release,
            StudentPaperAttempt attempt,
            LocalDateTime now,
            String studentUsername) {
        StudentPaperAttemptStatus status = effectiveStatus(release, attempt, now);
        return new PaperReleaseStudentResultResponse(
                release.getId(),
                attempt.getId(),
                attempt.getStudentId(),
                studentUsername,
                status,
                status == StudentPaperAttemptStatus.SUBMITTED_LATE,
                attempt.getAnsweredCount(),
                attempt.getCorrectCount(),
                attempt.getEarnedScore(),
                attempt.getTotalScore(),
                attempt.getScorePercentage(),
                attempt.getSubmittedAt(),
                List.of());
    }

    private Map<Long, String> studentUsernames(List<StudentPaperAttempt> attempts) {
        Set<Long> studentIds = attempts.stream()
                .map(StudentPaperAttempt::getStudentId)
                .collect(Collectors.toSet());
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> usernames = new HashMap<>();
        for (AppUser user : appUserRepository.findAllById(studentIds)) {
            String username = normalizeUsername(user.getUsername());
            if (user.getId() != null && username != null) {
                usernames.putIfAbsent(user.getId(), username);
            }
        }
        return usernames;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return username.trim();
    }

    private List<StudentPaperAttempt> visibleAttempts(PaperRelease release, AppUser actor) {
        List<StudentPaperAttempt> allAttempts = attemptRepository
                .findByPaperReleaseIdOrderByStudentIdAsc(release.getId());
        if (hasGlobalReviewAccess(release, actor)) {
            return allAttempts.stream()
                    .filter(attempt -> attempt.getStatus() != StudentPaperAttemptStatus.INVALIDATED)
                    .toList();
        }
        Set<Long> reviewableStudentIds = reviewableTargetStudentIds(release, actor);
        return allAttempts.stream()
                .filter(attempt -> reviewableStudentIds.contains(attempt.getStudentId()))
                .filter(attempt -> attempt.getStatus() != StudentPaperAttemptStatus.INVALIDATED)
                .toList();
    }

    private void ensureCanReviewStudent(PaperRelease release, AppUser actor, Long studentId) {
        if (hasGlobalReviewAccess(release, actor)) {
            return;
        }
        if (!reviewableTargetStudentIds(release, actor).contains(studentId)) {
            throw new AccessDeniedException("Cannot review this paper attempt");
        }
    }

    private Set<Long> reviewableTargetStudentIds(PaperRelease release, AppUser actor) {
        return reviewableTargetStudentIds(
                release, actor, targetRepository.findByPaperReleaseId(release.getId()));
    }

    private Set<Long> reviewableTargetStudentIds(
            PaperRelease release,
            AppUser actor,
            List<PaperReleaseTarget> targets) {
        if (actor == null || actor.getRole() != UserRole.TEACHER) {
            throw new AccessDeniedException("Cannot review this paper release");
        }
        Set<Long> targetStudentIds = targets.stream()
                .map(PaperReleaseTarget::getStudentId)
                .collect(Collectors.toSet());
        Set<Long> directlyResponsibleStudentIds = targetStudentIds.isEmpty()
                ? Set.of()
                : new HashSet<>(relationRepository.findStudentIdsByTeacherIdAndStudentIdIn(
                        actor.getId(), targetStudentIds));
        Set<Long> ownedClassroomIds = new HashSet<>(classroomRepository.findIdsByTeacherId(actor.getId()));
        Set<Long> currentClassroomStudentIds = targetStudentIds.isEmpty() || ownedClassroomIds.isEmpty()
                ? Set.of()
                : new HashSet<>(classroomMemberRepository.findStudentIdsByClassroomIdInAndStudentIdIn(
                        ownedClassroomIds, targetStudentIds));
        Set<Long> reviewableStudentIds = targets.stream()
                .filter(target -> directlyResponsibleStudentIds.contains(target.getStudentId())
                        || currentClassroomStudentIds.contains(target.getStudentId())
                        || hasOwnedFrozenSourceClassroom(target, ownedClassroomIds))
                .map(PaperReleaseTarget::getStudentId)
                .collect(Collectors.toSet());
        if (reviewableStudentIds.isEmpty()) {
            throw new AccessDeniedException("Cannot review this paper release");
        }
        return reviewableStudentIds;
    }

    private List<PaperReleaseTarget> visibleTargets(PaperRelease release, AppUser actor) {
        List<PaperReleaseTarget> targets = targetRepository.findByPaperReleaseId(release.getId());
        if (hasGlobalReviewAccess(release, actor)) {
            return targets;
        }
        Set<Long> visibleStudentIds = reviewableTargetStudentIds(release, actor, targets);
        return targets.stream()
                .filter(target -> visibleStudentIds.contains(target.getStudentId()))
                .toList();
    }

    private PaperReleaseResponse toReleaseResponse(
            PaperRelease release, List<PaperReleaseTarget> targets) {
        Set<Long> visibleStudentIds = targets.stream()
                .map(PaperReleaseTarget::getStudentId)
                .collect(Collectors.toSet());
        Map<Long, StudentPaperAttempt> attemptsByStudent = attemptRepository
                .findByPaperReleaseIdOrderByStudentIdAsc(release.getId()).stream()
                .filter(attempt -> visibleStudentIds.contains(attempt.getStudentId()))
                .collect(Collectors.toMap(StudentPaperAttempt::getStudentId, Function.identity()));
        List<PaperReleaseTargetResponse> targetResponses = targets.stream()
                .sorted(Comparator.comparing(PaperReleaseTarget::getStudentId))
                .map(target -> {
                    StudentPaperAttempt attempt = attemptsByStudent.get(target.getStudentId());
                    return new PaperReleaseTargetResponse(
                            target.getId(),
                            target.getStudentId(),
                            readClassroomIds(target),
                            attempt == null ? null : attempt.getId(),
                            attempt == null ? null : attempt.getStatus());
                })
                .toList();
        return new PaperReleaseResponse(
                release.getId(),
                release.getPaperTemplateId(),
                release.getTitle(),
                release.getInstructions(),
                release.getPublishedByUserId(),
                release.getStatus(),
                release.getQuestionCount(),
                release.getTotalScore(),
                categories(release.getId()),
                release.getShuffleQuestions(),
                release.getShuffleOptions(),
                release.getStartTime(),
                release.getDeadline(),
                release.getBlankAnswerPolicy(),
                release.getResultVisibility(),
                release.getWithdrawnAt(),
                release.getWithdrawnByUserId(),
                release.getWithdrawReason(),
                release.getInvalidatedAt(),
                release.getInvalidatedByUserId(),
                release.getInvalidateReason(),
                release.getSupersedesReleaseId(),
                release.getSupersededByReleaseId(),
                release.getSupersededAt(),
                release.getSupersededByUserId(),
                release.getSupersedeReason(),
                release.getShowSupersededToStudents(),
                release.getCreatedAt(),
                targetResponses);
    }

    private List<String> categories(Long releaseId) {
        return questionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(releaseId).stream()
                .map(PaperReleaseQuestion::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .toList();
    }

    private void ensureReviewActor(AppUser actor) {
        if (actor == null || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER)) {
            throw new AccessDeniedException("Cannot review paper releases");
        }
    }

    private List<Long> readClassroomIds(PaperReleaseTarget target) {
        List<Long> classroomIds = readLongList(target.getSourceClassroomIdsJson());
        if (!classroomIds.isEmpty() || target.getSourceClassroomId() == null) {
            return classroomIds;
        }
        return List.of(target.getSourceClassroomId());
    }

    private boolean hasOwnedFrozenSourceClassroom(
            PaperReleaseTarget target, Set<Long> ownedClassroomIds) {
        if (target.getSourceClassroomId() != null
                && ownedClassroomIds.contains(target.getSourceClassroomId())) {
            return true;
        }
        return readLongList(target.getSourceClassroomIdsJson()).stream()
                .anyMatch(ownedClassroomIds::contains);
    }

    private boolean hasGlobalReviewAccess(PaperRelease release, AppUser actor) {
        return actor != null && (actor.getRole() == UserRole.ADMIN
                || (actor.getRole() == UserRole.TEACHER
                && actor.getId().equals(release.getPublishedByUserId())));
    }

    private PaperReleaseQuestionStatResponse toQuestionStat(
            PaperReleaseQuestion question,
            int submissionCount,
            List<StudentPaperAnswer> answers) {
        List<StudentPaperAnswer> presentAnswers = answers == null ? List.of() : answers;
        int answeredCount = (int) presentAnswers.stream().filter(this::isAnswered).count();
        int correctCount = (int) presentAnswers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getCorrect()))
                .count();
        BigDecimal rate = submissionCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(correctCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(submissionCount), 2, RoundingMode.HALF_UP);
        return new PaperReleaseQuestionStatResponse(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getCategory(),
                question.getStem(),
                submissionCount,
                answeredCount,
                correctCount,
                rate);
    }

    private StudentPaperResultQuestionResponse toQuestionResult(
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
                answer != null && Boolean.TRUE.equals(answer.getCorrect()),
                answer == null || answer.getEarnedScore() == null ? BigDecimal.ZERO : answer.getEarnedScore(),
                question.getScore(),
                readList(question.getAcceptedAnswersJson()),
                question.getExplanation());
    }

    private boolean isAnswered(StudentPaperAnswer answer) {
        return !readList(answer.getSelectedAnswersJson()).isEmpty()
                || !readList(answer.getBlankAnswersJson()).isEmpty();
    }

    private boolean isValidFinalSubmission(StudentPaperAttempt attempt) {
        return attempt.getStatus() == StudentPaperAttemptStatus.SUBMITTED
                || attempt.getStatus() == StudentPaperAttemptStatus.SUBMITTED_LATE;
    }

    private StudentPaperAttemptStatus effectiveStatus(
            PaperRelease release,
            StudentPaperAttempt attempt,
            LocalDateTime now) {
        if (!isValidFinalSubmission(attempt)
                && attempt.getStatus() != StudentPaperAttemptStatus.INVALIDATED
                && release.getDeadline() != null
                && now.isAfter(release.getDeadline())) {
            return StudentPaperAttemptStatus.OVERDUE;
        }
        return attempt.getStatus();
    }

    private void ensureIncludedInDefaultStatistics(PaperRelease release) {
        if (release.getStatus() == PaperReleaseStatus.WITHDRAWN
                || release.getStatus() == PaperReleaseStatus.INVALIDATED) {
            throw new ConflictException("Release is excluded from default result statistics");
        }
    }

    private void ensureCanChangeGlobalVisibility(PaperRelease release, AppUser actor) {
        if (actor != null && (actor.getRole() == UserRole.ADMIN
                || (actor.getRole() == UserRole.TEACHER
                && actor.getId().equals(release.getPublishedByUserId())))) {
            return;
        }
        throw new AccessDeniedException("Only an administrator or the publishing teacher can release results");
    }

    private PaperRelease requireRelease(Long releaseId) {
        return releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper release not found: " + releaseId));
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored paper answer JSON is invalid", exception);
        }
    }

    private Map<String, String> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, STRING_MAP_TYPE));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored paper question options JSON is invalid", exception);
        }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LONG_LIST_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored release target classroom JSON is invalid", exception);
        }
    }
}
