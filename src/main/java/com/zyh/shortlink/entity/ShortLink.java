package com.zyh.shortlink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("short_link")
public class ShortLink {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String shortCode;

    private String longUrl;

    private Long clickCount;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;
}
