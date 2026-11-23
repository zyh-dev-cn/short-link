package com.zyh.shortlink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLinkRequest {

    @NotBlank(message = "长链接不能为空")
    @Size(max = 2000, message = "长链接过长")
    private String longUrl;

    @Min(value = 1, message = "有效期至少 1 天")
    @Max(value = 3650, message = "有效期最长 3650 天")
    private Integer expireDays;
}
