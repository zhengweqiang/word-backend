package com.example.words.dto;

import com.example.words.model.Phonetic;
import com.example.words.model.StudyTaskType;
import com.example.words.model.SyllableDetail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardTaskItemResponse {

    private Long studentStudyPlanId;
    private Long studyDayTaskItemId;
    private Long studyPlanId;
    private String planName;
    private LocalDateTime planPublishedAt;
    private Long metaWordId;
    private String word;
    private String definition;
    private String translation;
    private String partOfSpeech;
    private String exampleSentence;
    private String phonetic;
    private Phonetic phoneticDetail;
    private SyllableDetail syllableDetail;
    private StudyTaskType taskType;
    private Integer phase;
    private LocalDate dueDate;
    private Integer attemptCount;
}
