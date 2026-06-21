package com.example.words.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyllableBackfillResponse {

    private Integer attempted;
    private Integer updated;
    private Integer skipped;
    private List<SyllableBackfillFailureResponse> failures;
}
