package com.example.words.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyllableSegment {

    private String text;
    private String ukPhonetic;
    private String usPhonetic;
    private String ukAudioUrl;
    private String usAudioUrl;
}
