CREATE TABLE IF NOT EXISTS short_link (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    short_code  VARCHAR(16)  NOT NULL COMMENT '短码',
    long_url    VARCHAR(2048) NOT NULL COMMENT '原始长链接',
    click_count BIGINT       NOT NULL DEFAULT 0 COMMENT '累计点击数',
    create_time DATETIME     NOT NULL COMMENT '创建时间',
    expire_time DATETIME     NULL COMMENT '过期时间，null 表示永久',
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_code (short_code),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '短链映射表';
