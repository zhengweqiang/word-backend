package com.example.words.repository;

import com.example.words.model.ClassroomMember;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, Long> {

    List<ClassroomMember> findByClassroomId(Long classroomId);

    List<ClassroomMember> findByClassroomIdIn(Collection<Long> classroomIds);

    List<ClassroomMember> findByStudentId(Long studentId);

    List<ClassroomMember> findByStudentIdIn(Collection<Long> studentIds);

    @Query("SELECT DISTINCT member.studentId FROM ClassroomMember member "
            + "WHERE member.classroomId IN :classroomIds AND member.studentId IN :studentIds")
    List<Long> findStudentIdsByClassroomIdInAndStudentIdIn(
            @Param("classroomIds") Collection<Long> classroomIds,
            @Param("studentIds") Collection<Long> studentIds);

    boolean existsByClassroomIdAndStudentId(Long classroomId, Long studentId);

    boolean existsByClassroomIdInAndStudentId(Collection<Long> classroomIds, Long studentId);

    long countByClassroomId(Long classroomId);

    void deleteByClassroomIdAndStudentId(Long classroomId, Long studentId);
}
