package com.zyh.shortlink.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateLinkResponse {

    private String shortCode;

    private String shortUrl;
}
