package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.words.model.PaperTemplateStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaperTemplateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private String instructions;

    @NotNull
    private Boolean shuffleQuestions;

    @NotNull
    private Boolean shuffleOptions;

    @NotNull
    private PaperTemplateStatus status;
}
