package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.CreateUserRequest;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(UserService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserServiceTransactionIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private StudentPointAccountService studentPointAccountService;

    @BeforeEach
    void setUp() {
        appUserRepository.deleteAll();
    }

    @Test
    void createStudentShouldRollbackUserWhenPointAccountCreationFails() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate student account");
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        doThrow(failure).when(studentPointAccountService).createForStudent(anyLong());

        assertThrows(DataIntegrityViolationException.class, () -> userService.createUser(studentRequest()));

        assertTrue(appUserRepository.findByUsername("rollback.student").isEmpty());
        verify(studentPointAccountService).createForStudent(anyLong());
    }

    private CreateUserRequest studentRequest() {
        return new CreateUserRequest(
                "rollback.student",
                "password",
                "Rollback Student",
                null,
                null,
                UserRole.STUDENT
        );
    }
}
