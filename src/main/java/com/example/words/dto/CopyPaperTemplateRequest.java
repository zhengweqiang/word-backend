package com.example.words.dto;

import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopyPaperTemplateRequest {

    @Size(max = 200)
    private String title;
}
