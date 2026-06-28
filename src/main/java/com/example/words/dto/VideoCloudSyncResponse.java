package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoCloudSyncResponse {

    private int scanned;
    private int imported;
    private int updated;
}
