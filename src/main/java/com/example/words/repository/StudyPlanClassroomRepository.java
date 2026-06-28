package com.example.words.repository;

import com.example.words.model.StudyPlanClassroom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyPlanClassroomRepository extends JpaRepository<StudyPlanClassroom, Long> {

    List<StudyPlanClassroom> findByStudyPlanId(Long studyPlanId);

    boolean existsByStudyPlanIdAndClassroomId(Long studyPlanId, Long classroomId);

    boolean existsByClassroomId(Long classroomId);
}
