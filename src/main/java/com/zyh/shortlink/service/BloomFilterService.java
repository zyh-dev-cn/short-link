package com.zyh.shortlink.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class BloomFilterService {

    private static final String KEY = "sl:bloom";
    private static final long BITS = 10_000_000L;
    private static final int HASHES = 7;

    private final StringRedisTemplate redis;

    public BloomFilterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void add(String code) {
        for (long offset : offsets(code)) {
            redis.opsForValue().setBit(KEY, offset, true);
        }
    }

    public boolean mightContain(String code) {
        for (long offset : offsets(code)) {
            Boolean bit = redis.opsForValue().getBit(KEY, offset);
            if (bit == null || !bit) {
                return false;
            }
        }
        return true;
    }

    private long[] offsets(String code) {
        byte[] digest = md5(code);
        long h1 = toLong(digest, 0);
        long h2 = toLong(digest, 8);
        long[] result = new long[HASHES];
        for (int i = 0; i < HASHES; i++) {
            result[i] = Math.floorMod(h1 + i * h2, BITS);
        }
        return result;
    }

    private byte[] md5(String code) {
        try {
            return MessageDigest.getInstance("MD5").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    private long toLong(byte[] bytes, int start) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[start + i] & 0xFF);
        }
        return value;
    }
}
