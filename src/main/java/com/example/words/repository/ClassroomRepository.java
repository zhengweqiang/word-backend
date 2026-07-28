package com.example.words.repository;

import com.example.words.model.Classroom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long>, JpaSpecificationExecutor<Classroom> {

    List<Classroom> findByTeacherId(Long teacherId);

    @Query("SELECT classroom.id FROM Classroom classroom WHERE classroom.teacherId = :teacherId")
    List<Long> findIdsByTeacherId(@Param("teacherId") Long teacherId);
}
