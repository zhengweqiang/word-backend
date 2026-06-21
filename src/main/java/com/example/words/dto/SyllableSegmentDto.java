package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyllableSegmentDto {

    private String text;
    private String ukPhonetic;
    private String usPhonetic;
    private String ukAudioUrl;
    private String usAudioUrl;
}
