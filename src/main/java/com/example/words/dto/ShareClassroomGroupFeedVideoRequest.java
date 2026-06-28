package com.example.words.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareClassroomGroupFeedVideoRequest {

    @NotNull(message = "videoId is required")
    private Long videoId;
}
