package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.InvalidatePaperReleaseRequest;
import com.example.words.dto.PaperReleaseResponse;
import com.example.words.dto.PaperReleaseTargetResponse;
import com.example.words.dto.PublishPaperRequest;
import com.example.words.dto.SupersedePaperReleaseRequest;
import com.example.words.dto.WithdrawPaperReleaseRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.ClassroomMember;
import com.example.words.model.PaperBlankAnswerPolicy;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseQuestion;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.PaperReleaseTarget;
import com.example.words.model.PaperResultVisibility;
import com.example.words.model.PaperTemplate;
import com.example.words.model.PaperTemplateQuestion;
import com.example.words.model.QuestionType;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.PaperReleaseQuestionRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

@Service
public class PaperReleaseService {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final long QUESTION_SHUFFLE_SALT = 0x51A7E5D4C3B2A190L;
    private static final long OPTION_SHUFFLE_SALT = 0x0F710A5EED1234ABL;
    private static final Set<PaperReleaseStatus> CORRECTABLE_STATUSES = Set.of(
            PaperReleaseStatus.SCHEDULED, PaperReleaseStatus.OPEN);
    private static final Set<StudentPaperAttemptStatus> FINAL_SUBMISSION_STATUSES = Set.of(
            StudentPaperAttemptStatus.SUBMITTED, StudentPaperAttemptStatus.SUBMITTED_LATE);

    private final PaperReleaseRepository releaseRepository;
    private final PaperReleaseQuestionRepository releaseQuestionRepository;
    private final PaperReleaseTargetRepository targetRepository;
    private final StudentPaperAttemptRepository attemptRepository;
    private final PaperTemplateService paperTemplateService;
    private final ExamPaperAccessService accessService;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final AppUserRepository userRepository;
    private final ExamPaperSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaperReleaseService(
            PaperReleaseRepository releaseRepository,
            PaperReleaseQuestionRepository releaseQuestionRepository,
            PaperReleaseTargetRepository targetRepository,
            StudentPaperAttemptRepository attemptRepository,
            PaperTemplateService paperTemplateService,
            ExamPaperAccessService accessService,
            ClassroomMemberRepository classroomMemberRepository,
            AppUserRepository userRepository,
            ExamPaperSnapshotService snapshotService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.releaseRepository = releaseRepository;
        this.releaseQuestionRepository = releaseQuestionRepository;
        this.targetRepository = targetRepository;
        this.attemptRepository = attemptRepository;
        this.paperTemplateService = paperTemplateService;
        this.accessService = accessService;
        this.classroomMemberRepository = classroomMemberRepository;
        this.userRepository = userRepository;
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PaperReleaseResponse publish(PublishPaperRequest request, AppUser actor) {
        if (request == null || request.getPaperId() == null) {
            throw new BadRequestException("Paper and publish request are required");
        }
        PaperTemplateService.PublicationSource source =
                paperTemplateService.lockReadyForPublishing(request.getPaperId(), actor);
        LinkedHashMap<Long, LinkedHashSet<Long>> targetSources = resolveTargets(request, actor);
        validateStudentTargets(targetSources.keySet());
        TimeWindow window = validateWindow(request.getStartTime(), request.getDeadline());
        return createRelease(
                source,
                targetSources,
                actor,
                window,
                defaultBlankPolicy(request.getBlankAnswerPolicy()),
                defaultResultVisibility(request.getResultVisibility()),
                null);
    }

    @Transactional
    public PaperReleaseResponse withdraw(
            Long releaseId, WithdrawPaperReleaseRequest request, AppUser actor) {
        String reason = requireReason(request == null ? null : request.getReason());
        PaperRelease release = correctableRelease(releaseId, actor);
        List<StudentPaperAttempt> attempts = lockAttempts(releaseId);
        if (attempts.stream().map(StudentPaperAttempt::getStatus).anyMatch(FINAL_SUBMISSION_STATUSES::contains)) {
            throw new BadRequestException("A release with final submissions cannot be withdrawn");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        release.setStatus(PaperReleaseStatus.WITHDRAWN);
        release.setWithdrawnAt(now);
        release.setWithdrawnByUserId(actor.getId());
        release.setWithdrawReason(reason);
        releaseRepository.saveAndFlush(release);
        return toResponse(release, targets(releaseId), attempts);
    }

    @Transactional
    public PaperReleaseResponse invalidate(
            Long releaseId, InvalidatePaperReleaseRequest request, AppUser actor) {
        String reason = requireReason(request == null ? null : request.getReason());
        PaperRelease release = correctableRelease(releaseId, actor);
        LocalDateTime now = LocalDateTime.now(clock);
        release.setStatus(PaperReleaseStatus.INVALIDATED);
        release.setInvalidatedAt(now);
        release.setInvalidatedByUserId(actor.getId());
        release.setInvalidateReason(reason);
        List<StudentPaperAttempt> attempts = lockAttempts(releaseId);
        for (StudentPaperAttempt attempt : attempts) {
            if (!FINAL_SUBMISSION_STATUSES.contains(attempt.getStatus())) {
                attempt.setStatus(StudentPaperAttemptStatus.INVALIDATED);
            }
            attempt.setInvalidatedAt(now);
            attempt.setInvalidatedByUserId(actor.getId());
            attempt.setInvalidateReason(reason);
        }
        attemptRepository.saveAllAndFlush(attempts);
        releaseRepository.saveAndFlush(release);
        return toResponse(release, targets(releaseId), attempts);
    }

    @Transactional
    public PaperReleaseResponse supersede(
            Long releaseId, SupersedePaperReleaseRequest request, AppUser actor) {
        if (request == null) {
            throw new BadRequestException("Supersede request is required");
        }
        String reason = requireReason(request.getReason());
        PaperRelease original = correctableRelease(releaseId, actor);
        lockAttempts(releaseId);
        List<PaperReleaseTarget> originalTargets = targets(releaseId);
        if (originalTargets.isEmpty()) {
            throw new BadRequestException("Release has no frozen targets");
        }
        PaperTemplateService.PublicationSource source =
                paperTemplateService.lockReadyForPublishing(original.getPaperTemplateId(), actor);
        LinkedHashMap<Long, LinkedHashSet<Long>> targetSources = frozenTargetSources(originalTargets);
        TimeWindow window = validateWindow(request.getStartTime(), request.getDeadline());
        PaperReleaseResponse replacement = createRelease(
                source,
                targetSources,
                actor,
                window,
                request.getBlankAnswerPolicy() == null
                        ? original.getBlankAnswerPolicy() : request.getBlankAnswerPolicy(),
                request.getResultVisibility() == null
                        ? original.getResultVisibility() : request.getResultVisibility(),
                original.getId());

        original.setStatus(PaperReleaseStatus.SUPERSEDED);
        original.setSupersededByReleaseId(replacement.getId());
        original.setSupersededAt(LocalDateTime.now(clock));
        original.setSupersededByUserId(actor.getId());
        original.setSupersedeReason(reason);
        original.setShowSupersededToStudents(Boolean.TRUE.equals(request.getShowOriginalToStudents()));
        releaseRepository.saveAndFlush(original);
        return replacement;
    }

    private PaperReleaseResponse createRelease(
            PaperTemplateService.PublicationSource source,
            LinkedHashMap<Long, LinkedHashSet<Long>> targetSources,
            AppUser actor,
            TimeWindow window,
            PaperBlankAnswerPolicy blankPolicy,
            PaperResultVisibility resultVisibility,
            Long supersedesReleaseId) {
        PaperTemplate paper = source.paper();
        List<PaperTemplateQuestion> questions = source.questions();
        if (questions.isEmpty()) {
            throw new BadRequestException("Paper must contain at least one active question");
        }
        BigDecimal totalScore = questions.stream()
                .map(PaperTemplateQuestion::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalScore.signum() <= 0) {
            throw new BadRequestException("Paper total score must be positive");
        }

        PaperRelease release = new PaperRelease();
        release.setPaperTemplateId(paper.getId());
        release.setTitle(paper.getTitle());
        release.setInstructions(paper.getInstructions());
        release.setPublishedByUserId(actor.getId());
        release.setStatus(window.startTime().isAfter(LocalDateTime.now(clock))
                ? PaperReleaseStatus.SCHEDULED : PaperReleaseStatus.OPEN);
        release.setQuestionCount(questions.size());
        release.setTotalScore(totalScore);
        release.setShuffleQuestions(Boolean.TRUE.equals(paper.getShuffleQuestions()));
        release.setShuffleOptions(Boolean.TRUE.equals(paper.getShuffleOptions()));
        release.setStartTime(window.startTime());
        release.setDeadline(window.deadline());
        release.setBlankAnswerPolicy(blankPolicy);
        release.setResultVisibility(resultVisibility);
        release.setSupersedesReleaseId(supersedesReleaseId);
        release.setShowSupersededToStudents(false);
        releaseRepository.saveAndFlush(release);

        List<PaperTemplateQuestion> presentationOrder = new ArrayList<>(questions);
        if (Boolean.TRUE.equals(release.getShuffleQuestions())) {
            shuffleToDifferentOrder(presentationOrder, shuffledSeed(
                    release.getId(), null, QUESTION_SHUFFLE_SALT));
        }
        List<PaperReleaseQuestion> snapshots = new ArrayList<>();
        for (int index = 0; index < presentationOrder.size(); index++) {
            PaperTemplateQuestion templateQuestion = presentationOrder.get(index);
            PaperReleaseQuestion snapshot =
                    snapshotService.createReleaseQuestionSnapshot(templateQuestion, release.getId());
            snapshot.setQuestionOrder(index + 1);
            if (Boolean.TRUE.equals(release.getShuffleOptions())) {
                shuffleChoiceOptions(snapshot, release.getId());
            }
            snapshots.add(snapshot);
        }
        releaseQuestionRepository.saveAllAndFlush(snapshots);

        List<PaperReleaseTarget> targets = new ArrayList<>();
        List<StudentPaperAttempt> attempts = new ArrayList<>();
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : targetSources.entrySet()) {
            List<Long> classroomIds = List.copyOf(entry.getValue());
            PaperReleaseTarget target = new PaperReleaseTarget();
            target.setPaperReleaseId(release.getId());
            target.setStudentId(entry.getKey());
            target.setSourceClassroomId(classroomIds.isEmpty() ? null : classroomIds.get(0));
            target.setSourceClassroomIdsJson(writeClassroomIds(classroomIds));
            target.setTargetedByUserId(actor.getId());
            targets.add(target);

            StudentPaperAttempt attempt = new StudentPaperAttempt();
            attempt.setPaperReleaseId(release.getId());
            attempt.setStudentId(entry.getKey());
            attempt.setStatus(StudentPaperAttemptStatus.NOT_STARTED);
            attempt.setAnsweredCount(0);
            attempt.setCorrectCount(0);
            attempt.setEarnedScore(BigDecimal.ZERO);
            attempt.setTotalScore(totalScore);
            attempts.add(attempt);
        }
        targetRepository.saveAllAndFlush(targets);
        attemptRepository.saveAllAndFlush(attempts);
        return toResponse(release, targets, attempts);
    }

    private LinkedHashMap<Long, LinkedHashSet<Long>> resolveTargets(
            PublishPaperRequest request, AppUser actor) {
        LinkedHashMap<Long, LinkedHashSet<Long>> targets = new LinkedHashMap<>();
        for (Long studentId : distinct(request.getStudentIds())) {
            accessService.ensureCanPublishToStudent(actor, studentId);
            targets.computeIfAbsent(studentId, ignored -> new LinkedHashSet<>());
        }
        for (Long classroomId : distinct(request.getClassroomIds())) {
            accessService.ensureCanPublishToClassroom(actor, classroomId);
            for (ClassroomMember member : classroomMemberRepository.findByClassroomId(classroomId)) {
                targets.computeIfAbsent(member.getStudentId(), ignored -> new LinkedHashSet<>()).add(classroomId);
            }
        }
        if (targets.isEmpty()) {
            throw new BadRequestException("At least one student target is required");
        }
        return targets;
    }

    private void validateStudentTargets(Collection<Long> studentIds) {
        List<Long> orderedIds = new ArrayList<>(studentIds);
        Map<Long, AppUser> users = userRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        for (Long studentId : orderedIds) {
            AppUser user = users.get(studentId);
            if (user == null || user.getRole() != UserRole.STUDENT) {
                throw new BadRequestException("Invalid student target: " + studentId);
            }
        }
    }

    private PaperRelease correctableRelease(Long releaseId, AppUser actor) {
        ensureCorrectionActor(actor);
        PaperRelease release = findReleaseForUpdate(releaseId);
        if (actor.getRole() != UserRole.ADMIN && !actor.getId().equals(release.getPublishedByUserId())) {
            throw new AccessDeniedException("Not authorized to correct this release");
        }
        if (!CORRECTABLE_STATUSES.contains(release.getStatus())) {
            throw new BadRequestException("Release is already in terminal status " + release.getStatus());
        }
        return release;
    }

    private PaperRelease findReleaseForUpdate(Long releaseId) {
        if (releaseId == null) {
            throw new BadRequestException("Release ID is required");
        }
        return releaseRepository.findByIdForUpdate(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper release not found: " + releaseId));
    }

    private void ensureCorrectionActor(AppUser actor) {
        if (actor == null || actor.getId() == null
                || (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.TEACHER)) {
            throw new AccessDeniedException("Only teachers and administrators can correct releases");
        }
    }

    private TimeWindow validateWindow(LocalDateTime requestedStart, LocalDateTime deadline) {
        LocalDateTime start = requestedStart == null ? LocalDateTime.now(clock) : requestedStart;
        if (deadline != null && deadline.isBefore(start)) {
            throw new BadRequestException("Deadline cannot be before start time");
        }
        return new TimeWindow(start, deadline);
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new BadRequestException("Correction reason is required");
        }
        if (normalized.length() > 500) {
            throw new BadRequestException("Correction reason must not exceed 500 characters");
        }
        return normalized;
    }

    private PaperBlankAnswerPolicy defaultBlankPolicy(PaperBlankAnswerPolicy policy) {
        return policy == null ? PaperBlankAnswerPolicy.ALLOW_BLANK : policy;
    }

    private PaperResultVisibility defaultResultVisibility(PaperResultVisibility visibility) {
        return visibility == null ? PaperResultVisibility.SCORE_ONLY : visibility;
    }

    private List<Long> distinct(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BadRequestException("Target IDs must be positive");
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private List<StudentPaperAttempt> lockAttempts(Long releaseId) {
        return attemptRepository.findByPaperReleaseIdForUpdate(releaseId);
    }

    private List<PaperReleaseTarget> targets(Long releaseId) {
        return targetRepository.findByPaperReleaseId(releaseId).stream()
                .sorted(Comparator.comparing(PaperReleaseTarget::getStudentId))
                .toList();
    }

    private LinkedHashMap<Long, LinkedHashSet<Long>> frozenTargetSources(
            List<PaperReleaseTarget> frozenTargets) {
        LinkedHashMap<Long, LinkedHashSet<Long>> result = new LinkedHashMap<>();
        frozenTargets.stream()
                .sorted(Comparator.comparing(PaperReleaseTarget::getStudentId))
                .forEach(target -> result.put(
                        target.getStudentId(), new LinkedHashSet<>(readClassroomIds(target))));
        return result;
    }

    private void shuffleChoiceOptions(PaperReleaseQuestion snapshot, Long releaseId) {
        if (snapshot.getQuestionType() != QuestionType.SINGLE_CHOICE
                && snapshot.getQuestionType() != QuestionType.MULTIPLE_CHOICE) {
            return;
        }
        try {
            Map<String, String> parsedOptions = objectMapper.readValue(snapshot.getOptionsJson(), STRING_MAP);
            TreeMap<String, String> canonicalOptions = new TreeMap<>(parsedOptions);
            if (canonicalOptions.size() <= 1) {
                return;
            }

            List<String> canonicalKeys = new ArrayList<>(canonicalOptions.keySet());
            List<String> shuffledSourceKeys = new ArrayList<>(canonicalKeys);
            shuffleToDifferentOrder(shuffledSourceKeys, shuffledSeed(
                    releaseId, snapshot.getPaperTemplateQuestionId(), OPTION_SHUFFLE_SALT));

            Map<String, String> presentedOptions = new LinkedHashMap<>();
            Map<String, String> remappedKeys = new LinkedHashMap<>();
            for (int index = 0; index < canonicalKeys.size(); index++) {
                String presentationKey = canonicalKeys.get(index);
                String sourceKey = shuffledSourceKeys.get(index);
                presentedOptions.put(presentationKey, canonicalOptions.get(sourceKey));
                remappedKeys.put(sourceKey, presentationKey);
            }

            List<String> acceptedAnswers = objectMapper.readValue(
                    snapshot.getAcceptedAnswersJson(), STRING_LIST);
            List<String> remappedAnswers = acceptedAnswers.stream()
                    .map(answer -> {
                        String remapped = remappedKeys.get(answer);
                        if (remapped == null) {
                            throw new IllegalStateException(
                                    "Accepted option is missing from release options: " + answer);
                        }
                        return remapped;
                    })
                    .distinct()
                    .sorted()
                    .toList();
            snapshot.setOptionsJson(objectMapper.writeValueAsString(presentedOptions));
            snapshot.setAcceptedAnswersJson(objectMapper.writeValueAsString(remappedAnswers));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not freeze shuffled option presentation", exception);
        }
    }

    private <T> void shuffleToDifferentOrder(List<T> values, long seed) {
        if (values.size() <= 1) {
            return;
        }
        List<T> original = List.copyOf(values);
        Collections.shuffle(values, new Random(seed));
        if (values.equals(original)) {
            Collections.rotate(values, 1);
        }
    }

    private long shuffledSeed(Long releaseId, Long questionId, long salt) {
        long seed = releaseId == null ? 0L : releaseId;
        seed ^= Long.rotateLeft(questionId == null ? 0L : questionId, 21);
        seed ^= salt;
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;
        return seed;
    }

    private String writeClassroomIds(List<Long> classroomIds) {
        try {
            return objectMapper.writeValueAsString(classroomIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist classroom target trace", exception);
        }
    }

    private List<Long> readClassroomIds(PaperReleaseTarget target) {
        String json = target.getSourceClassroomIdsJson();
        if (json == null || json.isBlank()) {
            return target.getSourceClassroomId() == null
                    ? List.of() : List.of(target.getSourceClassroomId());
        }
        try {
            return objectMapper.readValue(json, LONG_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read classroom target trace", exception);
        }
    }

    private PaperReleaseResponse toResponse(
            PaperRelease release,
            List<PaperReleaseTarget> targets,
            List<StudentPaperAttempt> attempts) {
        Map<Long, StudentPaperAttempt> attemptsByStudent = attempts.stream()
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
        return releaseQuestionRepository.findByPaperReleaseIdOrderByQuestionOrderAsc(releaseId).stream()
                .map(PaperReleaseQuestion::getCategory)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .toList();
    }

    private record TimeWindow(LocalDateTime startTime, LocalDateTime deadline) {
    }
}
