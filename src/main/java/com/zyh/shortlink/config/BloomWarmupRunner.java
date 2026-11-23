package com.zyh.shortlink.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zyh.shortlink.entity.ShortLink;
import com.zyh.shortlink.mapper.ShortLinkMapper;
import com.zyh.shortlink.service.BloomFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BloomWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BloomWarmupRunner.class);

    private final ShortLinkMapper mapper;
    private final BloomFilterService bloom;

    public BloomWarmupRunner(ShortLinkMapper mapper, BloomFilterService bloom) {
        this.mapper = mapper;
        this.bloom = bloom;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Object> codes = mapper.selectObjs(Wrappers.<ShortLink>lambdaQuery()
                .select(ShortLink::getShortCode));
        codes.forEach(code -> bloom.add(String.valueOf(code)));
        log.info("布隆过滤器预热完成，共加载 {} 个短码", codes.size());
    }
}
