-- SattoLux DB Schema
-- 대상: SATTOLUX_DEV_DB (개발), SATTOLUX_DB (상용)

-- 인증/인가용 사용자
CREATE TABLE IF NOT EXISTS `app_user` (
    `user_seq`             BIGINT          NOT NULL AUTO_INCREMENT,
    `user_id`              VARCHAR(50)     NOT NULL,
    `password_hash`        VARCHAR(255)    NOT NULL,
    `email`                VARCHAR(100)    NOT NULL,
    `role_code`            VARCHAR(20)     NOT NULL DEFAULT 'USER',
    `otp_enabled`          TINYINT(1)      NOT NULL DEFAULT 0,
    `account_status`       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / LOCKED / DISABLED
    `failed_login_count`   TINYINT         NOT NULL DEFAULT 0,
    `locked_until`         DATETIME                 DEFAULT NULL,
    `last_login_at`        DATETIME                 DEFAULT NULL,
    `password_changed_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_seq`),
    UNIQUE KEY `uk_app_user_user_id` (`user_id`),
    UNIQUE KEY `uk_app_user_email` (`email`),
    INDEX `idx_app_user_status` (`account_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Refresh Token 해시 저장
CREATE TABLE IF NOT EXISTS `refresh_token` (
    `token_id`         BIGINT          NOT NULL AUTO_INCREMENT,
    `user_seq`         BIGINT          NOT NULL,
    `token_hash`       CHAR(64)        NOT NULL,
    `expires_at`       DATETIME        NOT NULL,
    `revoked_at`       DATETIME                 DEFAULT NULL,
    `issued_ip`        VARCHAR(45)              DEFAULT NULL,
    `user_agent`       VARCHAR(255)             DEFAULT NULL,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`token_id`),
    UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
    INDEX `idx_refresh_token_user_seq` (`user_seq`),
    CONSTRAINT `fk_refresh_token_user`
        FOREIGN KEY (`user_seq`) REFERENCES `app_user` (`user_seq`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- OTP 코드 해시 저장
CREATE TABLE IF NOT EXISTS `otp_code` (
    `otp_id`           BIGINT          NOT NULL AUTO_INCREMENT,
    `user_seq`         BIGINT          NOT NULL,
    `code_hash`        CHAR(64)        NOT NULL,
    `expires_at`       DATETIME        NOT NULL,
    `attempt_count`    TINYINT         NOT NULL DEFAULT 0,
    `used_yn`          CHAR(1)         NOT NULL DEFAULT 'N',
    `used_at`          DATETIME                 DEFAULT NULL,
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`otp_id`),
    INDEX `idx_otp_code_user_seq` (`user_seq`),
    INDEX `idx_otp_code_lookup` (`user_seq`, `used_yn`, `expires_at`),
    CONSTRAINT `fk_otp_code_user`
        FOREIGN KEY (`user_seq`) REFERENCES `app_user` (`user_seq`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 번호 생성 규칙 (row 단위)
CREATE TABLE IF NOT EXISTS `generation_rule` (
    `rule_id`               BIGINT          NOT NULL AUTO_INCREMENT,
    `user_seq`              BIGINT          NOT NULL,
    `day_of_week`           TINYINT         NOT NULL DEFAULT 4,    -- 1=월 ~ 5=금
    `method_code`           VARCHAR(20)     NOT NULL,              -- RANDOM / HOT_NUMBER / MIXED
    `generator_code`        VARCHAR(20)     NOT NULL,              -- LOCAL / CLAUDE
    `set_count`             TINYINT         NOT NULL,
    `analysis_draw_count`   INT                      DEFAULT NULL,
    `sort_order`            INT             NOT NULL DEFAULT 0,
    `use_yn`                CHAR(1)         NOT NULL DEFAULT 'Y',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`rule_id`),
    INDEX `idx_generation_rule_user_day` (`user_seq`, `day_of_week`, `use_yn`),
    CONSTRAINT `fk_generation_rule_user`
        FOREIGN KEY (`user_seq`) REFERENCES `app_user` (`user_seq`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 생성된 번호 세트
CREATE TABLE IF NOT EXISTS `satto_number_set` (
    `set_id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `user_seq`              BIGINT          NOT NULL,
    `rule_id`               BIGINT                   DEFAULT NULL,
    `target_year`           SMALLINT        NOT NULL,
    `target_month`          TINYINT         NOT NULL,
    `target_week_of_month`  TINYINT         NOT NULL,
    `draw_no`               INT                      DEFAULT NULL,
    `method_code`           VARCHAR(20)     NOT NULL,
    `generator_code`        VARCHAR(20)     NOT NULL,
    `no1`                   TINYINT         NOT NULL,
    `no2`                   TINYINT         NOT NULL,
    `no3`                   TINYINT         NOT NULL,
    `no4`                   TINYINT         NOT NULL,
    `no5`                   TINYINT         NOT NULL,
    `no6`                   TINYINT         NOT NULL,
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`set_id`),
    INDEX `idx_satto_number_set_scope` (`user_seq`, `target_year`, `target_month`, `target_week_of_month`),
    INDEX `idx_satto_number_set_draw_no` (`draw_no`),
    CONSTRAINT `fk_satto_number_set_user`
        FOREIGN KEY (`user_seq`) REFERENCES `app_user` (`user_seq`) ON DELETE CASCADE,
    CONSTRAINT `fk_satto_number_set_rule`
        FOREIGN KEY (`rule_id`) REFERENCES `generation_rule` (`rule_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 로또 추첨 결과
CREATE TABLE IF NOT EXISTS `satto_draw_result` (
    `result_id`         BIGINT          NOT NULL AUTO_INCREMENT,
    `draw_no`           INT             NOT NULL,
    `draw_date`         DATE            NOT NULL,
    `no1`               TINYINT         NOT NULL,
    `no2`               TINYINT         NOT NULL,
    `no3`               TINYINT         NOT NULL,
    `no4`               TINYINT         NOT NULL,
    `no5`               TINYINT         NOT NULL,
    `no6`               TINYINT         NOT NULL,
    `bonus_no`          TINYINT         NOT NULL,
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`result_id`),
    UNIQUE KEY `uk_satto_draw_result_draw_no` (`draw_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 생성 번호 vs 추첨 결과 비교
CREATE TABLE IF NOT EXISTS `satto_match_result` (
    `compare_id`        BIGINT          NOT NULL AUTO_INCREMENT,
    `set_id`            BIGINT          NOT NULL,
    `result_id`         BIGINT          NOT NULL,
    `match_count`       TINYINT         NOT NULL DEFAULT 0,
    `bonus_match`       TINYINT(1)      NOT NULL DEFAULT 0,
    `rank`              TINYINT                  DEFAULT NULL, -- 1~5등, NULL=미당첨
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`compare_id`),
    UNIQUE KEY `uk_satto_match_result_set_result` (`set_id`, `result_id`),
    CONSTRAINT `fk_satto_match_result_set`
        FOREIGN KEY (`set_id`) REFERENCES `satto_number_set` (`set_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_satto_match_result_result`
        FOREIGN KEY (`result_id`) REFERENCES `satto_draw_result` (`result_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사용자 알림
CREATE TABLE IF NOT EXISTS `app_notification` (
    `notification_id`       BIGINT          NOT NULL AUTO_INCREMENT,
    `user_seq`              BIGINT          NOT NULL,
    `type_code`             VARCHAR(30)     NOT NULL,
    `title`                 VARCHAR(100)    NOT NULL,
    `message`               VARCHAR(255)    NOT NULL,
    `target_year`           SMALLINT                 DEFAULT NULL,
    `target_month`          TINYINT                  DEFAULT NULL,
    `target_week_of_month`  TINYINT                  DEFAULT NULL,
    `draw_no`               INT                      DEFAULT NULL,
    `read_yn`               CHAR(1)         NOT NULL DEFAULT 'N',
    `read_at`               DATETIME                 DEFAULT NULL,
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`notification_id`),
    UNIQUE KEY `uk_app_notification_result_ready` (`user_seq`, `type_code`, `target_year`, `target_month`, `target_week_of_month`),
    INDEX `idx_app_notification_user_read` (`user_seq`, `read_yn`, `created_at`),
    CONSTRAINT `fk_app_notification_user`
        FOREIGN KEY (`user_seq`) REFERENCES `app_user` (`user_seq`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
