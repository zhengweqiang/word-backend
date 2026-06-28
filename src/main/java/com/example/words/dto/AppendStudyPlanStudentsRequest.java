package com.example.words.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppendStudyPlanStudentsRequest {

    @NotEmpty(message = "studentIds cannot be empty")
    private List<Long> studentIds;
}
