-- =========================================================
-- DDgo DDL (MariaDB + MinIO / Soft Delete )
-- =========================================================

-- 0) DB 생성/선택
CREATE DATABASE IF NOT EXISTS `DDgo`;
USE `DDgo`;

-- 1) Drop (자식 -> 부모)
DROP TABLE IF EXISTS `challenge_attempt_counters`;
DROP TABLE IF EXISTS `attempt_metrics`;
DROP TABLE IF EXISTS `attempt_video`;
DROP TABLE IF EXISTS `attempt_feedbacks`;
DROP TABLE IF EXISTS `attempts`;
DROP TABLE IF EXISTS `challenge_summaries`;
DROP TABLE IF EXISTS `challenges`;
DROP TABLE IF EXISTS `user_profiles`;
DROP TABLE IF EXISTS `users`;

-- =========================================================
-- 1) users
-- =========================================================
CREATE TABLE `users` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(30) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(30) NOT NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  KEY `ix_users_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 2) user_profiles
-- =========================================================
CREATE TABLE `user_profiles` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,

  `sex` VARCHAR(6) NULL,
  `height_cm` INT NULL,
  `weight_kg` INT NULL,
  `wingspan_cm` INT NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_profiles_user_id` (`user_id`),
  KEY `ix_user_profiles_user_id` (`user_id`),
  KEY `ix_user_profiles_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 3) challenges
-- =========================================================
CREATE TABLE `challenges` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '회원 기본 키',

  `gym_name` VARCHAR(50) NULL,
  `problem_color` VARCHAR(30) NOT NULL,
  `grade_label` VARCHAR(30) NULL,

  `challenge_status` ENUM('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  `challenge_result` ENUM('SUCCESS','FAIL','UNKNOWN') NULL,

  `started_at` DATETIME NULL,
  `ended_at` DATETIME NULL,

  `holds_json` JSON NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),

  KEY `ix_challenges_user_id` (`user_id`),

  -- 홈 목록(진행/종료)에서 가장 자주 쓰는 패턴:
  -- WHERE user_id=? AND deleted_at IS NULL AND challenge_status=? ORDER BY created_at DESC
  KEY `ix_challenges_user_deleted_status_created` (`user_id`, `deleted_at`, `challenge_status`, `created_at`),

  -- 유저의 전체 목록(상태 무관) 정렬:
  KEY `ix_challenges_user_deleted_created` (`user_id`, `deleted_at`, `created_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 4) challenge_summaries (Challenge 1개당 1개)
--   - max_crux_duration_ms = 해당 challenge의 attempt_metrics.crux_duration_ms 최댓값(집계 결과 저장)
-- =========================================================
CREATE TABLE `challenge_summaries` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `challenge_id` INT NOT NULL,

  `average_center_stability_ratio` DECIMAL(5,2) NULL,
  `most_crux_hold_no` INT NULL,
  `max_crux_duration_ms` INT NULL,

  `final_comment` VARCHAR(500) NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_challenge_summaries_challenge_id` (`challenge_id`),
  KEY `ix_challenge_summaries_challenge_id` (`challenge_id`),
  KEY `ix_challenge_summaries_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 5) challenge_attempt_counters (C: attempt_no 안전 발급용)
--  - challenge_id별 next_attempt_no를 원자적으로 증가시키기 위한 카운터
--  - 애플리케이션에서 다음 방식 권장:
--    1) UPDATE challenge_attempt_counters
--         SET next_attempt_no = LAST_INSERT_ID(next_attempt_no + 1)
--       WHERE challenge_id = ?;
--    2) SELECT LAST_INSERT_ID();  -- 방금 발급된 attempt_no
--    3) 그 attempt_no로 attempts INSERT
-- =========================================================
CREATE TABLE `challenge_attempt_counters` (
  `challenge_id` INT NOT NULL,
  `next_attempt_no` INT NOT NULL DEFAULT 0,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`challenge_id`)
) ENGINE=InnoDB;

-- =========================================================
-- 6) attempts
--   B) 시간 기준 확정:
--     - analysis_started_at / analysis_ended_at : 분석 파이프라인 시간
--     - duration_ms : "영상 길이(ms)" (메타데이터 기반, 도메인 값)
--   C) attempt_no는 카운터 테이블로 안전 발급
--   A) soft delete + 정렬/조회 패턴 인덱스 강화
-- =========================================================
CREATE TABLE `attempts` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `challenge_id` INT NOT NULL,
  `attempt_no` INT NOT NULL,

  `attempt_status` ENUM('UPLOADING','UPLOAD_FAILED','PROCESSING','ANALYSIS_FAILED','DONE')
    NOT NULL DEFAULT 'UPLOADING',
  `attempt_result` ENUM('SUCCESS','FAIL','UNKNOWN') NULL,

  -- 분석 파이프라인 기준 시간 (명확히!)
  `analysis_started_at` DATETIME NULL,
  `analysis_ended_at` DATETIME NULL,

  -- 영상 메타 기준(영상 길이)
  `duration_ms` INT NULL,

  `max_hold_no` INT NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempts_challenge_attempt_no` (`challenge_id`, `attempt_no`),

  -- challenge 상세: WHERE challenge_id=? AND deleted_at IS NULL ORDER BY attempt_no ASC(or created_at ASC)
  KEY `ix_attempts_challenge_deleted_attemptno` (`challenge_id`, `deleted_at`, `attempt_no`),

  -- challenge 내 최신순 목록/필터:
  KEY `ix_attempts_challenge_deleted_created` (`challenge_id`, `deleted_at`, `created_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 7) attempt_feedbacks (Attempt 1개당 1개)
-- =========================================================
CREATE TABLE `attempt_feedbacks` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `attempt_id` INT NOT NULL,

  `failure_reason` VARCHAR(200) NULL,
  `risk_alert` VARCHAR(200) NULL,
  `next_mission` VARCHAR(200) NULL,

  -- E: 버전/추적용
  `model_version` VARCHAR(50) NULL,
  `prompt_version` VARCHAR(50) NULL,
  `generated_at` DATETIME NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_feedbacks_attempt_id` (`attempt_id`),
  KEY `ix_attempt_feedbacks_attempt_id` (`attempt_id`),
  KEY `ix_attempt_feedbacks_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 8) attempt_video (Attempt 1개당 1개 / MinIO 참조)
-- =========================================================
CREATE TABLE `attempt_video` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `attempt_id` INT NOT NULL,

  `original_file_name` VARCHAR(255) NULL,

  `bucket` VARCHAR(100) NULL,
  `object_key` VARCHAR(1024) NULL,
  `content_type` VARCHAR(100) NULL,
  `file_size` BIGINT NULL,
  `etag` VARCHAR(64) NULL,

  `is_uploaded` TINYINT(1) NOT NULL DEFAULT 0,
  `uploaded_at` DATETIME NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_video_attempt_id` (`attempt_id`),
  KEY `ix_attempt_video_attempt_id` (`attempt_id`),
  KEY `ix_attempt_video_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 9) attempt_metrics (Attempt 1개당 1개)
-- =========================================================
CREATE TABLE `attempt_metrics` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `attempt_id` INT NOT NULL,

  `center_stability_ratio` DECIMAL(5,2) NULL,
  `crux_hold_no` INT NULL,
  `crux_duration_ms` INT NULL,
  `danger_event_count` INT NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempt_metrics_attempt_id` (`attempt_id`),
  KEY `ix_attempt_metrics_attempt_id` (`attempt_id`),
  KEY `ix_attempt_metrics_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- FK 설정 (Soft delete 운영 전제: ON DELETE CASCADE 사용 X)
-- =========================================================
ALTER TABLE `user_profiles`
  ADD CONSTRAINT `fk_user_profiles_users`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `challenges`
  ADD CONSTRAINT `fk_challenges_users`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `challenge_summaries`
  ADD CONSTRAINT `fk_challenge_summaries_challenges`
  FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `challenge_attempt_counters`
  ADD CONSTRAINT `fk_challenge_attempt_counters_challenges`
  FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `attempts`
  ADD CONSTRAINT `fk_attempts_challenges`
  FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `attempt_feedbacks`
  ADD CONSTRAINT `fk_attempt_feedbacks_attempts`
  FOREIGN KEY (`attempt_id`) REFERENCES `attempts` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `attempt_video`
  ADD CONSTRAINT `fk_attempt_video_attempts`
  FOREIGN KEY (`attempt_id`) REFERENCES `attempts` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `attempt_metrics`
  ADD CONSTRAINT `fk_attempt_metrics_attempts`
  FOREIGN KEY (`attempt_id`) REFERENCES `attempts` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

-- =========================================================
-- C) 카운터 자동 초기화 트리거
--  - challenge 생성 시 counters row 자동 생성
-- =========================================================
DELIMITER //

CREATE TRIGGER `trg_challenges_init_attempt_counter`
AFTER INSERT ON `challenges`
FOR EACH ROW
BEGIN
  INSERT INTO `challenge_attempt_counters` (`challenge_id`, `next_attempt_no`)
  VALUES (NEW.`id`, 0);
END//

DELIMITER ;