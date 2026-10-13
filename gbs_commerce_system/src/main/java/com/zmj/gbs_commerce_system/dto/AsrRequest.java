package com.zmj.gbs_commerce_system.dto;

import lombok.Data;

@Data
public class AsrRequest {
    private String audioBase64;
    private String audioFormat = "pcm16k16bit";
    
    public String getAudioFormat() {
        if (audioFormat == null || audioFormat.trim().isEmpty()) {
            return "pcm16k16bit";
        }
        if ("wav".equalsIgnoreCase(audioFormat) || "pcm".equalsIgnoreCase(audioFormat)) {
            return "pcm16k16bit";
        }
        return audioFormat;
    }
    private String property = "chinese_16k_common";
}