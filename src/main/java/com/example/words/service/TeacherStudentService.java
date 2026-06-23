package com.example.words.service;

import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.TeacherStudentRelation;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.TeacherStudentRelationRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherStudentService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TeacherStudentRelationRepository teacherStudentRelationRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final AppUserRepository appUserRepository;
    private final UserService userService;

    public TeacherStudentService(
            TeacherStudentRelationRepository teacherStudentRelationRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            AppUserRepository appUserRepository,
            UserService userService) {
        this.teacherStudentRelationRepository = teacherStudentRelationRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.appUserRepository = appUserRepository;
        this.userService = userService;
    }

    @Transactional
    public void assignStudent(Long teacherId, Long studentId) {
        AppUser teacher = userService.getUserEntity(teacherId);
        AppUser student = userService.getUserEntity(studentId);
        validateTeacherAndStudent(teacher, student);

        if (teacherStudentRelationRepository.existsByTeacherIdAndStudentId(teacherId, studentId)) {
            return;
        }

        teacherStudentRelationRepository.save(new TeacherStudentRelation(null, teacherId, studentId, null));
    }

    @Transactional
    public void removeStudent(Long teacherId, Long studentId) {
        teacherStudentRelationRepository.deleteByTeacherIdAndStudentId(teacherId, studentId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getStudentsForTeacher(Long teacherId) {
        return getResponsibleStudentIds(teacherId).stream()
                .map(userService::findById)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getStudentsForTeacher(Long teacherId, int page, int size, String name) {
        Pageable pageable = buildPageable(page, size);
        Set<Long> studentIds = getResponsibleStudentIds(teacherId);
        if (studentIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<AppUser> specification = Specification.<AppUser>where(
                (root, query, criteriaBuilder) -> root.get("id").in(studentIds)
        ).and(displayNameContains(name));

        return appUserRepository.findAll(specification, pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public boolean isTeacherResponsibleForStudent(Long teacherId, Long studentId) {
        if (teacherStudentRelationRepository.existsByTeacherIdAndStudentId(teacherId, studentId)) {
            return true;
        }

        List<Long> classroomIds = classroomRepository.findByTeacherId(teacherId).stream()
                .map(Classroom::getId)
                .toList();

        return !classroomIds.isEmpty()
                && classroomMemberRepository.existsByClassroomIdInAndStudentId(classroomIds, studentId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getResponsibleTeacherIdsForStudent(Long studentId) {
        Set<Long> teacherIds = teacherStudentRelationRepository.findByStudentId(studentId).stream()
                .map(TeacherStudentRelation::getTeacherId)
                .collect(Collectors.toSet());

        List<Long> classroomIds = classroomMemberRepository.findByStudentId(studentId).stream()
                .map(ClassroomMember::getClassroomId)
                .toList();

        if (!classroomIds.isEmpty()) {
            classroomRepository.findAllById(classroomIds).stream()
                    .map(Classroom::getTeacherId)
                    .forEach(teacherIds::add);
        }

        return teacherIds;
    }

    private void validateTeacherAndStudent(AppUser teacher, AppUser student) {
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BadRequestException("User is not a teacher: " + teacher.getId());
        }
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("User is not a student: " + student.getId());
        }
    }

    private Set<Long> getResponsibleStudentIds(Long teacherId) {
        Set<Long> studentIds = teacherStudentRelationRepository.findByTeacherId(teacherId).stream()
                .map(TeacherStudentRelation::getStudentId)
                .collect(Collectors.toSet());

        List<Long> classroomIds = classroomRepository.findByTeacherId(teacherId).stream()
                .map(Classroom::getId)
                .toList();

        if (!classroomIds.isEmpty()) {
            classroomMemberRepository.findByClassroomIdIn(classroomIds).stream()
                    .map(ClassroomMember::getStudentId)
                    .forEach(studentIds::add);
        }

        return studentIds;
    }

    private Pageable buildPageable(int page, int size) {
        int normalizedPage = Math.max(page, 1) - 1;
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "id"));
    }

    private Specification<AppUser> displayNameContains(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String keyword = "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), keyword);
    }
}
