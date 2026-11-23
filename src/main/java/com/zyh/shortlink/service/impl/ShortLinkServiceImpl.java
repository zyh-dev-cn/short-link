package com.zyh.shortlink.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zyh.shortlink.entity.ShortLink;
import com.zyh.shortlink.mapper.ShortLinkMapper;
import com.zyh.shortlink.service.BloomFilterService;
import com.zyh.shortlink.service.ShortLinkService;
import com.zyh.shortlink.util.Base62;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ShortLinkServiceImpl implements ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkServiceImpl.class);

    private static final String CACHE_PREFIX = "sl:link:";
    private static final String CLICK_PREFIX = "sl:click:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final ShortLinkMapper mapper;
    private final StringRedisTemplate redis;
    private final BloomFilterService bloom;

    public ShortLinkServiceImpl(ShortLinkMapper mapper, StringRedisTemplate redis, BloomFilterService bloom) {
        this.mapper = mapper;
        this.redis = redis;
        this.bloom = bloom;
    }

    @Override
    public ShortLink create(String longUrl, Integer expireDays) {
        long id = IdWorker.getId();
        String code = Base62.encode(id);

        ShortLink link = new ShortLink();
        link.setId(id);
        link.setShortCode(code);
        link.setLongUrl(longUrl);
        link.setClickCount(0L);
        link.setCreateTime(LocalDateTime.now());
        if (expireDays != null) {
            link.setExpireTime(LocalDateTime.now().plusDays(expireDays));
        }
        mapper.insert(link);

        bloom.add(code);
        cache(code, longUrl, link.getExpireTime());
        return link;
    }

    @Override
    public String resolve(String shortCode) {
        if (!bloom.mightContain(shortCode)) {
            return null;
        }
        String cached = redis.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) {
            return cached;
        }

        ShortLink link = findValid(shortCode);
        if (link == null) {
            return null;
        }
        cache(shortCode, link.getLongUrl(), link.getExpireTime());
        return link.getLongUrl();
    }

    @Override
    public void recordClick(String shortCode) {
        redis.opsForValue().increment(CLICK_PREFIX + shortCode);
    }

    @Override
    public ShortLink detail(String shortCode) {
        ShortLink link = findValid(shortCode);
        if (link == null) {
            return null;
        }
        String pending = redis.opsForValue().get(CLICK_PREFIX + shortCode);
        if (pending != null) {
            link.setClickCount(link.getClickCount() + Long.parseLong(pending));
        }
        return link;
    }

    @Override
    public Page<ShortLink> page(int page, int size) {
        Page<ShortLink> result = mapper.selectPage(new Page<>(page, size),
                Wrappers.<ShortLink>lambdaQuery().orderByDesc(ShortLink::getCreateTime));
        result.getRecords().forEach(link -> {
            String pending = redis.opsForValue().get(CLICK_PREFIX + link.getShortCode());
            if (pending != null) {
                link.setClickCount(link.getClickCount() + Long.parseLong(pending));
            }
        });
        return result;
    }

    @Override
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void flushClicks() {
        int flushed = 0;
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(CLICK_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String code = key.substring(CLICK_PREFIX.length());
                String value = redis.opsForValue().get(key);
                if (value == null) {
                    continue;
                }
                long delta = Long.parseLong(value);
                if (delta <= 0) {
                    continue;
                }
                redis.opsForValue().decrement(key, delta);
                mapper.update(null, Wrappers.<ShortLink>lambdaUpdate()
                        .eq(ShortLink::getShortCode, code)
                        .setSql("click_count = click_count + " + delta));
                flushed++;
            }
        } catch (Exception e) {
            log.warn("刷新点击计数失败", e);
        }
        if (flushed > 0) {
            log.info("刷新点击计数完成，共 {} 个短链", flushed);
        }
    }

    private ShortLink findValid(String shortCode) {
        ShortLink link = mapper.selectOne(Wrappers.<ShortLink>lambdaQuery()
                .eq(ShortLink::getShortCode, shortCode));
        if (link == null) {
            return null;
        }
        if (link.getExpireTime() != null && link.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return link;
    }

    private void cache(String code, String longUrl, LocalDateTime expireTime) {
        Duration ttl = CACHE_TTL;
        if (expireTime != null) {
            Duration untilExpire = Duration.between(LocalDateTime.now(), expireTime);
            if (untilExpire.isNegative() || untilExpire.isZero()) {
                return;
            }
            if (untilExpire.compareTo(ttl) < 0) {
                ttl = untilExpire;
            }
        }
        redis.opsForValue().set(CACHE_PREFIX + code, longUrl, ttl);
    }
}
