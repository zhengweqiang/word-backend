package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.CreateUserRequest;
import com.example.words.dto.UserResponse;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StudentPointAccountService studentPointAccountService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(appUserRepository, passwordEncoder, studentPointAccountService);
    }

    @Test
    void createUserShouldCreatePointAccountOnceForSavedStudent() {
        when(appUserRepository.existsByUsername("student.one")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser savedUser = invocation.getArgument(0);
            savedUser.setId(101L);
            return savedUser;
        });

        UserResponse response = userService.createUser(request(UserRole.STUDENT));

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertEquals("student.one", savedUser.getUsername());
        assertEquals("encoded-password", savedUser.getPasswordHash());
        assertEquals("Student One", savedUser.getDisplayName());
        assertEquals("student@example.com", savedUser.getEmail());
        assertEquals(101L, response.getId());
        verify(studentPointAccountService).createForStudent(101L);
    }

    @Test
    void createUserShouldNotCreatePointAccountForTeacher() {
        arrangeSavedUser(201L);

        UserResponse response = userService.createUser(request(UserRole.TEACHER));

        assertEquals(201L, response.getId());
        assertEquals(UserRole.TEACHER, response.getRole());
        verify(appUserRepository).save(any(AppUser.class));
        verify(studentPointAccountService, never()).createForStudent(any());
    }

    @Test
    void createUserShouldNotCreatePointAccountForAdmin() {
        arrangeSavedUser(301L);

        UserResponse response = userService.createUser(request(UserRole.ADMIN));

        assertEquals(301L, response.getId());
        assertEquals(UserRole.ADMIN, response.getRole());
        verify(appUserRepository).save(any(AppUser.class));
        verify(studentPointAccountService, never()).createForStudent(any());
    }

    private void arrangeSavedUser(Long id) {
        when(appUserRepository.existsByUsername("student.one")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser savedUser = invocation.getArgument(0);
            savedUser.setId(id);
            return savedUser;
        });
    }

    private CreateUserRequest request(UserRole role) {
        return new CreateUserRequest(
                "  Student.One  ",
                "password",
                "  Student One  ",
                "  student@example.com  ",
                "  ",
                role
        );
    }
}
