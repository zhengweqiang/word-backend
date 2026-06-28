package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateClassroomGroupFeedTextMessageRequest {

    @NotBlank(message = "content is required")
    @Size(max = 2000, message = "content cannot exceed 2000 characters")
    private String content;
}
