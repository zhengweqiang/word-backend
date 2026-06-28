package com.example.words.service;

import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomDictionaryAssignment;
import com.example.words.model.ClassroomMember;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.Dictionary;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomDictionaryAssignmentService {

    private final ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final DictionaryRepository dictionaryRepository;

    public ClassroomDictionaryAssignmentService(
            ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            DictionaryRepository dictionaryRepository) {
        this.classroomDictionaryAssignmentRepository = classroomDictionaryAssignmentRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.dictionaryRepository = dictionaryRepository;
    }

    @Transactional(readOnly = true)
    public List<Dictionary> findDictionariesForClassroom(Long classroomId, AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanManageClassroom(actor, classroom);

        Set<Long> dictionaryIds = getAssignedDictionaryIdsForClassroom(classroomId);
        if (dictionaryIds.isEmpty()) {
            return List.of();
        }

        return dictionaryRepository.findAllById(dictionaryIds).stream()
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .toList();
    }

    @Transactional
    public int assignDictionariesToClassroom(Long classroomId, Collection<Long> dictionaryIds, AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanManageClassroom(actor, classroom);
        ensureClassroomActive(classroom);

        int assignedCount = 0;
        for (Long dictionaryId : dictionaryIds == null ? List.<Long>of() : dictionaryIds.stream().distinct().toList()) {
            if (dictionaryId == null) {
                continue;
            }

            Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + dictionaryId));
            ensureCanAssignDictionaryToClassroom(actor, classroom, dictionary);

            if (classroomDictionaryAssignmentRepository.existsByClassroomIdAndDictionaryId(classroomId, dictionaryId)) {
                continue;
            }

            ClassroomDictionaryAssignment assignment = new ClassroomDictionaryAssignment();
            assignment.setClassroomId(classroomId);
            assignment.setDictionaryId(dictionaryId);
            assignment.setAssignedByUserId(actor.getId());
            classroomDictionaryAssignmentRepository.save(assignment);
            assignedCount++;
        }
        return assignedCount;
    }

    @Transactional
    public int assignDictionaryToClassrooms(Long dictionaryId, Collection<Long> classroomIds, AppUser actor) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            return 0;
        }

        int assignedCount = 0;
        for (Long classroomId : classroomIds.stream().distinct().toList()) {
            assignedCount += assignDictionariesToClassroom(classroomId, List.of(dictionaryId), actor);
        }
        return assignedCount;
    }

    @Transactional
    public void removeDictionaryFromClassroom(Long classroomId, Long dictionaryId, AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanManageClassroom(actor, classroom);
        classroomDictionaryAssignmentRepository.deleteByClassroomIdAndDictionaryId(classroomId, dictionaryId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getAssignedDictionaryIdsForClassroom(Long classroomId) {
        return classroomDictionaryAssignmentRepository.findByClassroomId(classroomId).stream()
                .map(ClassroomDictionaryAssignment::getDictionaryId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<Long> intersectAssignedDictionaryIdsForClassrooms(Collection<Long> classroomIds) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            return Set.of();
        }

        List<Long> normalizedIds = classroomIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Set.of();
        }

        Map<Long, Set<Long>> dictionaryIdsByClassroom = new LinkedHashMap<>();
        for (Long classroomId : normalizedIds) {
            dictionaryIdsByClassroom.put(classroomId, new LinkedHashSet<>());
        }

        for (ClassroomDictionaryAssignment assignment : classroomDictionaryAssignmentRepository.findByClassroomIdIn(normalizedIds)) {
            dictionaryIdsByClassroom.computeIfAbsent(assignment.getClassroomId(), ignored -> new LinkedHashSet<>())
                    .add(assignment.getDictionaryId());
        }

        Set<Long> intersection = null;
        for (Long classroomId : normalizedIds) {
            Set<Long> dictionaryIds = dictionaryIdsByClassroom.getOrDefault(classroomId, Set.of());
            if (dictionaryIds.isEmpty()) {
                return Set.of();
            }
            if (intersection == null) {
                intersection = new LinkedHashSet<>(dictionaryIds);
            } else {
                intersection.retainAll(dictionaryIds);
            }
            if (intersection.isEmpty()) {
                return Set.of();
            }
        }

        return intersection == null ? Set.of() : intersection;
    }

    @Transactional(readOnly = true)
    public Set<Long> getAssignedDictionaryIdsForStudent(Long studentId) {
        List<Long> classroomIds = classroomMemberRepository.findByStudentId(studentId).stream()
                .map(ClassroomMember::getClassroomId)
                .distinct()
                .toList();
        if (classroomIds.isEmpty()) {
            return Set.of();
        }

        return classroomDictionaryAssignmentRepository.findByClassroomIdIn(classroomIds).stream()
                .map(ClassroomDictionaryAssignment::getDictionaryId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<Long> getAssignedDictionaryIdsForTeacher(Long teacherId) {
        List<Long> classroomIds = classroomRepository.findByTeacherId(teacherId).stream()
                .map(Classroom::getId)
                .toList();
        if (classroomIds.isEmpty()) {
            return Set.of();
        }

        return classroomDictionaryAssignmentRepository.findByClassroomIdIn(classroomIds).stream()
                .map(ClassroomDictionaryAssignment::getDictionaryId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public boolean isDictionaryAssignedToStudent(Long dictionaryId, Long studentId) {
        List<Long> classroomIds = classroomMemberRepository.findByStudentId(studentId).stream()
                .map(ClassroomMember::getClassroomId)
                .distinct()
                .toList();
        if (classroomIds.isEmpty()) {
            return false;
        }
        return classroomDictionaryAssignmentRepository.existsByDictionaryIdAndClassroomIdIn(dictionaryId, classroomIds);
    }

    @Transactional(readOnly = true)
    public boolean isDictionaryAssignedToTeacherClassrooms(Long dictionaryId, Long teacherId) {
        List<Long> classroomIds = classroomRepository.findByTeacherId(teacherId).stream()
                .map(Classroom::getId)
                .toList();
        if (classroomIds.isEmpty()) {
            return false;
        }
        return classroomDictionaryAssignmentRepository.existsByDictionaryIdAndClassroomIdIn(dictionaryId, classroomIds);
    }

    private Classroom getClassroomOrThrow(Long classroomId) {
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId));
    }

    private void ensureCanManageClassroom(AppUser actor, Classroom classroom) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER && actor.getId().equals(classroom.getTeacherId())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to manage this classroom");
    }

    private void ensureClassroomActive(Classroom classroom) {
        if (classroom.getStatus() == ClassroomStatus.ARCHIVED) {
            throw new AccessDeniedException("Archived classroom cannot accept dictionary assignments");
        }
    }

    private void ensureCanAssignDictionaryToClassroom(AppUser actor, Classroom classroom, Dictionary dictionary) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER
                && actor.getId().equals(classroom.getTeacherId())
                && (dictionary.getScopeType() == ResourceScopeType.SYSTEM
                || actor.getId().equals(dictionary.getOwnerUserId())
                || actor.getId().equals(dictionary.getCreatedBy()))) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to assign this dictionary to the classroom");
    }
}
