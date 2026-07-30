package com.example.words.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCategoryResponse {

    private Long id;
    private String name;
    private Long createdByUserId;
    private Long lastModifiedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
