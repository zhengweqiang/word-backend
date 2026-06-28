package com.example.words.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomConversationResponse {

    private Long classroomId;

    private String classroomName;

    private String lastMessageSummary;

    private LocalDateTime lastMessageAt;
}
