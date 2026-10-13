package com.zmj.gbs_commerce_system.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class FaceRegisterRequest {
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "人脸图像不能为空")
    private String image;

    private String userInfo;
}
