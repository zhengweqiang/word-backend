package com.example.words.repository;

import com.example.words.model.ClassroomDictionaryAssignment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomDictionaryAssignmentRepository extends JpaRepository<ClassroomDictionaryAssignment, Long> {

    List<ClassroomDictionaryAssignment> findByClassroomId(Long classroomId);

    List<ClassroomDictionaryAssignment> findByClassroomIdIn(Collection<Long> classroomIds);

    List<ClassroomDictionaryAssignment> findByDictionaryId(Long dictionaryId);

    boolean existsByClassroomId(Long classroomId);

    boolean existsByClassroomIdAndDictionaryId(Long classroomId, Long dictionaryId);

    @Query("""
            SELECT CASE WHEN COUNT(cda) > 0 THEN true ELSE false END
            FROM ClassroomDictionaryAssignment cda
            WHERE cda.dictionaryId = :dictionaryId
              AND cda.classroomId IN :classroomIds
            """)
    boolean existsByDictionaryIdAndClassroomIdIn(
            @Param("dictionaryId") Long dictionaryId,
            @Param("classroomIds") Collection<Long> classroomIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ClassroomDictionaryAssignment cda
            WHERE cda.classroomId = :classroomId
              AND cda.dictionaryId = :dictionaryId
            """)
    int deleteByClassroomIdAndDictionaryId(
            @Param("classroomId") Long classroomId,
            @Param("dictionaryId") Long dictionaryId);
}
