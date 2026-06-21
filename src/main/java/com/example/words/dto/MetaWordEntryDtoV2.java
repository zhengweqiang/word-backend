package com.example.words.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaWordEntryDtoV2 {
    
    @NotBlank(message = "单词不能为空")
    @Size(min = 1, max = 100, message = "单词长度必须在1到100个字符之间")
    private String word;
    
    private PhoneticDto phonetic;

    private SyllableDetailDto syllableDetail;
    
    private List<PartOfSpeechDto> partOfSpeech;
    
    @Min(value = 1, message = "难度最小值为1")
    @Max(value = 5, message = "难度最大值为5")
    private Integer difficulty = 2;
}
