package com.example.words.repository;

import com.example.words.model.TeacherStudentRelation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherStudentRelationRepository extends JpaRepository<TeacherStudentRelation, Long> {

    List<TeacherStudentRelation> findByTeacherId(Long teacherId);

    List<TeacherStudentRelation> findByStudentId(Long studentId);

    boolean existsByTeacherIdAndStudentId(Long teacherId, Long studentId);

    @Query("SELECT relation.studentId FROM TeacherStudentRelation relation "
            + "WHERE relation.teacherId = :teacherId AND relation.studentId IN :studentIds")
    List<Long> findStudentIdsByTeacherIdAndStudentIdIn(
            @Param("teacherId") Long teacherId,
            @Param("studentIds") Collection<Long> studentIds);

    void deleteByTeacherIdAndStudentId(Long teacherId, Long studentId);
}
