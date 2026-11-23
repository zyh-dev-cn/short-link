package com.zyh.shortlink.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zyh.shortlink.entity.ShortLink;

public interface ShortLinkService {

    ShortLink create(String longUrl, Integer expireDays);

    String resolve(String shortCode);

    void recordClick(String shortCode);

    ShortLink detail(String shortCode);

    Page<ShortLink> page(int page, int size);

    void flushClicks();
}
