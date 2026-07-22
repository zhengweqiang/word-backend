package com.example.words.service;

import com.example.words.dto.StudentPointSummaryResponse;
import com.example.words.dto.StudentPointTransactionResponse;
import com.example.words.dto.TeacherStudentPointResponse;
import com.example.words.dto.UserResponse;
import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.StudentPointAccount;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StudentPointAccountRepository accountRepository;
    private final StudentPointTransactionRepository transactionRepository;
    private final TeacherStudentService teacherStudentService;

    @Transactional(readOnly = true)
    public StudentPointSummaryResponse getSummary(Long studentId) {
        StudentPointAccount account = requireAccount(studentId);
        return StudentPointSummaryResponse.from(account, todayEarned(studentId));
    }

    @Transactional(readOnly = true)
    public Page<StudentPointTransactionResponse> getTransactions(Long studentId, int page, int size) {
        requireAccount(studentId);
        return transactionRepository.findByStudentId(studentId, page(page, size))
                .map(StudentPointTransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TeacherStudentPointResponse> getManagedStudents(
            Long teacherId,
            int page,
            int size,
            String name
    ) {
        Page<UserResponse> students = teacherStudentService.getStudentsForTeacher(teacherId, page + 1, size, name);
        Map<Long, StudentPointAccount> accounts = accountRepository.findAllByStudentIdIn(
                        students.stream().map(UserResponse::getId).toList()
                ).stream()
                .collect(Collectors.toMap(StudentPointAccount::getStudentId, Function.identity()));
        Map<Long, Long> todayEarned = todayEarned(
                students.stream().map(UserResponse::getId).toList()
        );
        return students.map(student -> {
            StudentPointAccount account = accounts.get(student.getId());
            if (account == null) {
                throw error("POINT_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Student point account does not exist: " + student.getId());
            }
            return new TeacherStudentPointResponse(
                    student.getId(),
                    student.getDisplayName(),
                    account.getAvailablePoints(),
                    account.getLifetimeEarnedPoints(),
                    account.getLifetimeSpentPoints(),
                    todayEarned.getOrDefault(student.getId(), 0L)
            );
        });
    }

    @Transactional(readOnly = true)
    public StudentPointSummaryResponse getManagedStudentSummary(Long teacherId, Long studentId) {
        requireManagedStudent(teacherId, studentId);
        return getSummary(studentId);
    }

    @Transactional(readOnly = true)
    public Page<StudentPointTransactionResponse> getManagedStudentTransactions(
            Long teacherId,
            Long studentId,
            int page,
            int size
    ) {
        requireManagedStudent(teacherId, studentId);
        return getTransactions(studentId, page, size);
    }

    public Pageable page(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private void requireManagedStudent(Long teacherId, Long studentId) {
        if (!teacherStudentService.isTeacherResponsibleForStudent(teacherId, studentId)) {
            throw error("POINT_STUDENT_NOT_MANAGED", HttpStatus.FORBIDDEN,
                    "Teacher is not responsible for this student");
        }
    }

    private StudentPointAccount requireAccount(Long studentId) {
        return accountRepository.findByStudentId(studentId)
                .orElseThrow(() -> error("POINT_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Student point account does not exist"));
    }

    private long todayEarned(Long studentId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return transactionRepository.sumEarnedByStudentIdBetween(studentId, start, start.plusDays(1));
    }

    private Map<Long, Long> todayEarned(java.util.List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return transactionRepository.sumEarnedByStudentIdsBetween(studentIds, start, start.plusDays(1)).stream()
                .collect(Collectors.toMap(
                        StudentPointTransactionRepository.StudentPointEarnedTotal::getStudentId,
                        StudentPointTransactionRepository.StudentPointEarnedTotal::getTotal
                ));
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }
}
