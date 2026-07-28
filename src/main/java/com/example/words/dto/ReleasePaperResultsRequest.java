package com.example.words.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.words.model.PaperResultVisibility;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleasePaperResultsRequest {

    @NotNull
    private PaperResultVisibility resultVisibility;
}
