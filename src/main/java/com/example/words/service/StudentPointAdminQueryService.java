package com.example.words.service;

import com.example.words.dto.AdminStudentPointAccountResponse;
import com.example.words.dto.StudentPointEventAttemptResponse;
import com.example.words.dto.StudentPointEventResponse;
import com.example.words.dto.StudentPointRuleAuditResponse;
import com.example.words.dto.StudentPointRuleResponse;
import com.example.words.dto.StudentPointTransactionResponse;
import com.example.words.model.AppUser;
import com.example.words.model.PointEventStatus;
import com.example.words.model.StudentPointAccount;
import com.example.words.model.StudentPointEvent;
import com.example.words.model.StudentPointTransaction;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointEventAttemptRepository;
import com.example.words.repository.StudentPointEventRepository;
import com.example.words.repository.StudentPointRuleAuditRepository;
import com.example.words.repository.StudentPointRuleRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointAdminQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StudentPointAccountRepository accountRepository;
    private final StudentPointTransactionRepository transactionRepository;
    private final StudentPointEventRepository eventRepository;
    private final StudentPointEventAttemptRepository attemptRepository;
    private final StudentPointRuleRepository ruleRepository;
    private final StudentPointRuleAuditRepository ruleAuditRepository;
    private final AppUserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<AdminStudentPointAccountResponse> getAccounts(int page, int size) {
        Page<StudentPointAccount> accounts = accountRepository.findAll(page(page, size));
        Map<Long, AppUser> users = findUsersByStudentIds(
                accounts.stream().map(StudentPointAccount::getStudentId).toList()
        );
        return accounts.map(account -> AdminStudentPointAccountResponse.from(
                account,
                studentName(users, account.getStudentId()),
                studentUsername(users, account.getStudentId())
        ));
    }

    @Transactional(readOnly = true)
    public Page<StudentPointTransactionResponse> getTransactions(int page, int size) {
        Page<StudentPointTransaction> transactions = transactionRepository.findAll(page(page, size));
        Map<Long, AppUser> users = findUsersByStudentIds(
                transactions.stream().map(StudentPointTransaction::getStudentId).toList()
        );
        return transactions.map(transaction -> StudentPointTransactionResponse.from(
                transaction,
                studentName(users, transaction.getStudentId()),
                studentUsername(users, transaction.getStudentId())
        ));
    }

    @Transactional(readOnly = true)
    public Page<StudentPointEventResponse> getEvents(PointEventStatus status, int page, int size) {
        Pageable pageable = page(page, size);
        Page<StudentPointEvent> events =
                status == null ? eventRepository.findAll(pageable) : eventRepository.findByStatus(status, pageable);
        Map<Long, AppUser> users = findUsersByStudentIds(
                events.stream().map(StudentPointEvent::getStudentId).toList()
        );
        return events.map(event -> StudentPointEventResponse.from(
                event,
                studentName(users, event.getStudentId()),
                studentUsername(users, event.getStudentId())
        ));
    }

    @Transactional(readOnly = true)
    public List<StudentPointEventAttemptResponse> getAttempts(Long eventId) {
        return attemptRepository.findByEventIdOrderByAttemptNoAsc(eventId).stream()
                .map(StudentPointEventAttemptResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentPointRuleResponse> getRules() {
        return ruleRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(StudentPointRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentPointRuleAuditResponse> getRuleAudits(Long ruleId) {
        return ruleAuditRepository.findByRuleIdOrderByCreatedAtDesc(ruleId).stream()
                .map(StudentPointRuleAuditResponse::from)
                .toList();
    }

    private Pageable page(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private Map<Long, AppUser> findUsersByStudentIds(List<Long> studentIds) {
        List<Long> ids = studentIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private String studentName(Map<Long, AppUser> users, Long studentId) {
        AppUser user = users.get(studentId);
        return user == null ? null : user.getDisplayName();
    }

    private String studentUsername(Map<Long, AppUser> users, Long studentId) {
        AppUser user = users.get(studentId);
        return user == null ? null : user.getUsername();
    }
}
