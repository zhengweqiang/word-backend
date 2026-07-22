package com.example.words.service;

import com.example.words.dto.CreateUserRequest;
import com.example.words.dto.UpdateUserRoleRequest;
import com.example.words.dto.UpdateUserStatusRequest;
import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.repository.AppUserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentPointAccountService studentPointAccountService;

    public UserService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            StudentPointAccountService studentPointAccountService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.studentPointAccountService = studentPointAccountService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedUsername = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new BadRequestException("Username already exists: " + normalizedUsername);
        }

        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName().trim());
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);
        AppUser savedUser = appUserRepository.save(user);
        if (savedUser.getRole() == UserRole.STUDENT) {
            studentPointAccountService.createForStudent(savedUser.getId());
        }
        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return appUserRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findByRole(UserRole role) {
        return appUserRepository.findByRole(role).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findPage(int page, int size, UserRole role, String name) {
        Pageable pageable = buildPageable(page, size);
        Specification<AppUser> specification = Specification.where(roleEquals(role))
                .and(displayNameContains(name));
        return appUserRepository.findAll(specification, pageable)
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getUserEntity(id));
    }

    @Transactional
    public UserResponse updateRole(Long id, UpdateUserRoleRequest request) {
        AppUser user = getUserEntity(id);
        user.setRole(request.getRole());
        return UserResponse.from(appUserRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        AppUser user = getUserEntity(id);
        user.setStatus(request.getStatus());
        return UserResponse.from(appUserRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AppUser getUserEntity(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Pageable buildPageable(int page, int size) {
        int normalizedPage = Math.max(page, 1) - 1;
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "id"));
    }

    private Specification<AppUser> roleEquals(UserRole role) {
        if (role == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("role"), role);
    }

    private Specification<AppUser> displayNameContains(String name) {
        String normalizedName = trimToNull(name);
        if (normalizedName == null) {
            return null;
        }
        String keyword = "%" + normalizedName.toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), keyword);
    }
}
