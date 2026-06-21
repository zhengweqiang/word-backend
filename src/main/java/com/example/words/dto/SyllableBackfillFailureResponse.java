package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyllableBackfillFailureResponse {

    private Long metaWordId;
    private String word;
    private String reason;
}
