package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaperTemplateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private String instructions;

    @NotNull
    private Boolean shuffleQuestions = false;

    @NotNull
    private Boolean shuffleOptions = false;
}
