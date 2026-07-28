package com.example.words.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import com.example.words.model.Exam;
import com.example.words.model.ExamStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExamRepositoryIntegrationTest {

    @Autowired
    private ExamRepository examRepository;

    @Test
    void studentAssessmentQueryMatchesTargetUserPolicyAndNeverCreatorOwnership() {
        Exam targetedSubmitted = saveExam(5L, 20L, ExamStatus.SUBMITTED);
        Exam targetedGenerated = saveExam(6L, 20L, ExamStatus.GENERATED);
        saveExam(20L, 21L, ExamStatus.SUBMITTED);
        saveExam(20L, null, ExamStatus.GENERATED);
        saveExam(7L, 22L, ExamStatus.SUBMITTED);

        List<Exam> result = examRepository.findStudentAssessments(
                20L,
                Set.of(ExamStatus.GENERATED, ExamStatus.SUBMITTED));

        assertEquals(
                Set.of(targetedSubmitted.getId(), targetedGenerated.getId()),
                result.stream().map(Exam::getId).collect(java.util.stream.Collectors.toSet()));
    }

    private Exam saveExam(Long creatorId, Long targetId, ExamStatus status) {
        Exam exam = new Exam();
        exam.setDictionaryId(31L);
        exam.setQuestionCount(1);
        exam.setAnsweredCount(status == ExamStatus.SUBMITTED ? 1 : 0);
        exam.setCorrectCount(status == ExamStatus.SUBMITTED ? 1 : 0);
        exam.setScore(status == ExamStatus.SUBMITTED ? 100 : 0);
        exam.setCreatedByUserId(creatorId);
        exam.setTargetUserId(targetId);
        exam.setStatus(status);
        exam.setCreatedAt(LocalDateTime.of(2026, 7, 29, 9, 0));
        exam.setSubmittedAt(status == ExamStatus.SUBMITTED
                ? LocalDateTime.of(2026, 7, 29, 10, 0)
                : null);
        return examRepository.saveAndFlush(exam);
    }
}
