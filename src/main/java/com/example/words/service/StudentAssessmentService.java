package com.example.words.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.example.words.dto.StudentAssessmentStatus;
import com.example.words.dto.StudentAssessmentSummaryResponse;
import com.example.words.dto.StudentAssessmentType;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.Exam;
import com.example.words.model.ExamStatus;
import com.example.words.model.PaperRelease;
import com.example.words.model.PaperReleaseStatus;
import com.example.words.model.StudentPaperAttempt;
import com.example.words.model.StudentPaperAttemptStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.ExamRepository;
import com.example.words.repository.PaperReleaseRepository;
import com.example.words.repository.StudentPaperAttemptRepository;

@Service
public class StudentAssessmentService {

    private static final Set<ExamStatus> LEGACY_STATUSES = Set.of(
            ExamStatus.GENERATED,
            ExamStatus.SUBMITTED);

    private final ExamRepository examRepository;
    private final DictionaryRepository dictionaryRepository;
    private final StudentPaperAttemptRepository attemptRepository;
    private final PaperReleaseRepository releaseRepository;
    private final Clock clock;

    public StudentAssessmentService(
            ExamRepository examRepository,
            DictionaryRepository dictionaryRepository,
            StudentPaperAttemptRepository attemptRepository,
            PaperReleaseRepository releaseRepository,
            Clock clock) {
        this.examRepository = examRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.attemptRepository = attemptRepository;
        this.releaseRepository = releaseRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentAssessmentSummaryResponse> listPending(AppUser actor) {
        AssessmentSources sources = loadSources(requireStudent(actor));
        LocalDateTime now = LocalDateTime.now(clock);
        List<StudentAssessmentSummaryResponse> result = new ArrayList<>();

        sources.exams().stream()
                .filter(exam -> exam.getStatus() == ExamStatus.GENERATED)
                .map(exam -> toLegacySummary(exam, sources.dictionaries()))
                .forEach(result::add);
        sources.attempts().stream()
                .map(attempt -> toPaperSummary(attempt, sources.releases(), now, true))
                .flatMap(java.util.Optional::stream)
                .filter(summary -> isPending(summary.getStatus()))
                .forEach(result::add);

        return result.stream().sorted(pendingComparator()).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentAssessmentSummaryResponse> listHistory(AppUser actor) {
        AssessmentSources sources = loadSources(requireStudent(actor));
        LocalDateTime now = LocalDateTime.now(clock);
        List<StudentAssessmentSummaryResponse> result = new ArrayList<>();

        sources.exams().stream()
                .filter(exam -> exam.getStatus() == ExamStatus.SUBMITTED)
                .map(exam -> toLegacySummary(exam, sources.dictionaries()))
                .forEach(result::add);
        sources.attempts().stream()
                .map(attempt -> toPaperSummary(attempt, sources.releases(), now, false))
                .flatMap(java.util.Optional::stream)
                .filter(summary -> isHistory(summary.getStatus()))
                .forEach(result::add);

        return result.stream().sorted(historyComparator()).toList();
    }

    private AppUser requireStudent(AppUser actor) {
        if (actor == null || actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can list assessments");
        }
        return actor;
    }

    private AssessmentSources loadSources(AppUser student) {
        List<Exam> exams = examRepository.findStudentAssessments(student.getId(), LEGACY_STATUSES);
        List<StudentPaperAttempt> attempts = attemptRepository
                .findByStudentIdOrderByCreatedAtDesc(student.getId());

        Set<Long> dictionaryIds = exams.stream()
                .map(Exam::getDictionaryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Dictionary> dictionaries = dictionaryIds.isEmpty()
                ? Map.of()
                : dictionaryRepository.findAllById(dictionaryIds).stream()
                        .collect(Collectors.toMap(Dictionary::getId, Function.identity()));

        Set<Long> releaseIds = attempts.stream()
                .map(StudentPaperAttempt::getPaperReleaseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, PaperRelease> releases = releaseIds.isEmpty()
                ? Map.of()
                : releaseRepository.findAllById(releaseIds).stream()
                        .collect(Collectors.toMap(
                                PaperRelease::getId,
                                Function.identity(),
                                (first, ignored) -> first,
                                LinkedHashMap::new));
        return new AssessmentSources(exams, attempts, dictionaries, releases);
    }

    private StudentAssessmentSummaryResponse toLegacySummary(
            Exam exam,
            Map<Long, Dictionary> dictionaries) {
        StudentAssessmentSummaryResponse response = new StudentAssessmentSummaryResponse();
        response.setAssessmentType(StudentAssessmentType.LEGACY_GENERATED_EXAM);
        response.setStatus(exam.getStatus() == ExamStatus.SUBMITTED
                ? StudentAssessmentStatus.SUBMITTED
                : StudentAssessmentStatus.NOT_STARTED);
        response.setAssessmentId(exam.getId());
        response.setLegacyExamId(exam.getId());
        response.setDictionaryId(exam.getDictionaryId());
        Dictionary dictionary = dictionaries.get(exam.getDictionaryId());
        response.setTitle(dictionary == null ? "词书测验" : dictionary.getName());
        response.setQuestionCount(exam.getQuestionCount());
        response.setAnsweredCount(exam.getAnsweredCount());
        boolean scoreVisible = exam.getStatus() == ExamStatus.SUBMITTED;
        response.setScoreVisible(scoreVisible);
        if (scoreVisible) {
            response.setCorrectCount(exam.getCorrectCount());
            response.setScorePercentage(exam.getScore() == null ? null : BigDecimal.valueOf(exam.getScore()));
        }
        response.setAssignedAt(exam.getAssignedAt());
        response.setCreatedAt(exam.getCreatedAt());
        response.setSubmittedAt(exam.getSubmittedAt());
        return response;
    }

    private java.util.Optional<StudentAssessmentSummaryResponse> toPaperSummary(
            StudentPaperAttempt attempt,
            Map<Long, PaperRelease> releases,
            LocalDateTime now,
            boolean pendingList) {
        PaperRelease release = releases.get(attempt.getPaperReleaseId());
        if (release == null || !visibleToStudent(release)
                || attempt.getStatus() == StudentPaperAttemptStatus.INVALIDATED
                || (pendingList && release.getStatus() == PaperReleaseStatus.SUPERSEDED)) {
            return java.util.Optional.empty();
        }

        StudentAssessmentStatus status = effectivePaperStatus(attempt, release, now);
        StudentAssessmentSummaryResponse response = new StudentAssessmentSummaryResponse();
        response.setAssessmentType(StudentAssessmentType.PAPER_RELEASE_ATTEMPT);
        response.setStatus(status);
        response.setAssessmentId(attempt.getId());
        response.setPaperAttemptId(attempt.getId());
        response.setPaperReleaseId(release.getId());
        response.setPaperTemplateId(release.getPaperTemplateId());
        response.setTitle(release.getTitle());
        response.setQuestionCount(release.getQuestionCount());
        response.setAnsweredCount(attempt.getAnsweredCount());
        response.setTotalScore(attempt.getTotalScore());
        boolean scoreVisible = isHistory(status)
                && PaperResultVisibilityPolicy.isScoreVisible(release);
        response.setScoreVisible(scoreVisible);
        if (scoreVisible) {
            response.setCorrectCount(attempt.getCorrectCount());
            response.setEarnedScore(attempt.getEarnedScore());
            response.setScorePercentage(attempt.getScorePercentage());
        }
        response.setAssignedAt(attempt.getCreatedAt());
        response.setStartTime(release.getStartTime());
        response.setDeadline(release.getDeadline());
        response.setCreatedAt(attempt.getCreatedAt());
        response.setSubmittedAt(attempt.getSubmittedAt());
        return java.util.Optional.of(response);
    }

    private boolean visibleToStudent(PaperRelease release) {
        if (release.getStatus() == PaperReleaseStatus.WITHDRAWN
                || release.getStatus() == PaperReleaseStatus.INVALIDATED) {
            return false;
        }
        return release.getStatus() != PaperReleaseStatus.SUPERSEDED
                || Boolean.TRUE.equals(release.getShowSupersededToStudents());
    }

    private StudentAssessmentStatus effectivePaperStatus(
            StudentPaperAttempt attempt,
            PaperRelease release,
            LocalDateTime now) {
        if (attempt.getStatus() == StudentPaperAttemptStatus.SUBMITTED_LATE) {
            return StudentAssessmentStatus.SUBMITTED_LATE;
        }
        if (attempt.getStatus() == StudentPaperAttemptStatus.SUBMITTED) {
            return StudentAssessmentStatus.SUBMITTED;
        }
        if (release.getDeadline() != null && now.isAfter(release.getDeadline())) {
            return StudentAssessmentStatus.OVERDUE;
        }
        if (release.getStartTime() != null && now.isBefore(release.getStartTime())) {
            return StudentAssessmentStatus.SCHEDULED;
        }
        if (attempt.getStatus() == StudentPaperAttemptStatus.IN_PROGRESS) {
            return StudentAssessmentStatus.IN_PROGRESS;
        }
        if (attempt.getStatus() == StudentPaperAttemptStatus.OVERDUE) {
            return StudentAssessmentStatus.OVERDUE;
        }
        return StudentAssessmentStatus.NOT_STARTED;
    }

    private boolean isPending(StudentAssessmentStatus status) {
        return status == StudentAssessmentStatus.SCHEDULED
                || status == StudentAssessmentStatus.NOT_STARTED
                || status == StudentAssessmentStatus.IN_PROGRESS
                || status == StudentAssessmentStatus.OVERDUE;
    }

    private boolean isHistory(StudentAssessmentStatus status) {
        return status == StudentAssessmentStatus.SUBMITTED
                || status == StudentAssessmentStatus.SUBMITTED_LATE;
    }

    private Comparator<StudentAssessmentSummaryResponse> pendingComparator() {
        return Comparator.comparingInt(
                        (StudentAssessmentSummaryResponse response) -> pendingPriority(response.getStatus()))
                .thenComparing(this::actionTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StudentAssessmentSummaryResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StudentAssessmentSummaryResponse::getAssessmentId);
    }

    private int pendingPriority(StudentAssessmentStatus status) {
        return switch (status) {
            case OVERDUE -> 0;
            case IN_PROGRESS -> 1;
            case NOT_STARTED -> 2;
            case SCHEDULED -> 3;
            default -> 4;
        };
    }

    private LocalDateTime actionTime(StudentAssessmentSummaryResponse response) {
        if (response.getStartTime() != null) {
            return response.getStartTime();
        }
        if (response.getAssignedAt() != null) {
            return response.getAssignedAt();
        }
        return response.getCreatedAt();
    }

    private Comparator<StudentAssessmentSummaryResponse> historyComparator() {
        return Comparator.comparing(
                        StudentAssessmentSummaryResponse::getSubmittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        StudentAssessmentSummaryResponse::getAssessmentId,
                        Comparator.reverseOrder());
    }

    private record AssessmentSources(
            List<Exam> exams,
            List<StudentPaperAttempt> attempts,
            Map<Long, Dictionary> dictionaries,
            Map<Long, PaperRelease> releases) {
    }
}
