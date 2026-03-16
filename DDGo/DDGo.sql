-- =========================================================
-- DDgo DDL (MariaDB + MinIO / Soft Delete)
-- - climbing_brands 추가
-- - climbing_gyms에 지도 API 매칭/외부 장소 정보 컬럼 추가
-- - 암장 선택 필수
-- - 난이도 선택 필수
-- =========================================================

-- 0) DB 생성/선택
CREATE DATABASE IF NOT EXISTS `ddgo_db`;
USE `ddgo_db`;

-- 1) Drop (트리거 -> 자식 -> 부모)
DROP TRIGGER IF EXISTS `trg_challenges_init_attempt_counter`;

DROP TABLE IF EXISTS `challenge_attempt_counters`;
DROP TABLE IF EXISTS `attempt_metrics`;
DROP TABLE IF EXISTS `attempt_video`;
DROP TABLE IF EXISTS `attempt_feedbacks`;
DROP TABLE IF EXISTS `attempts`;
DROP TABLE IF EXISTS `challenge_summaries`;
DROP TABLE IF EXISTS `challenges`;
DROP TABLE IF EXISTS `climbing_gym_grades`;
DROP TABLE IF EXISTS `climbing_gyms`;
DROP TABLE IF EXISTS `climbing_brands`;
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
-- 3) climbing_brands
--   - 브랜드 마스터
--   - 브랜드 로고는 MinIO 참조 정보로 관리
-- =========================================================
CREATE TABLE `climbing_brands` (
  `id` INT NOT NULL AUTO_INCREMENT,

  `name` VARCHAR(50) NOT NULL COMMENT '예: 더클라임, 몽키즈클라이밍',
  `display_name` VARCHAR(80) NULL COMMENT '표시용 브랜드명',

  `logo_bucket` VARCHAR(100) NULL COMMENT '브랜드 로고 bucket',
  `logo_object_key` VARCHAR(1024) NULL COMMENT '브랜드 로고 object key',
  `logo_content_type` VARCHAR(100) NULL COMMENT '예: image/png',
  `logo_etag` VARCHAR(64) NULL COMMENT 'MinIO etag',

  `source_note` VARCHAR(255) NULL COMMENT '출처/비고',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_climbing_brands_name` (`name`),
  KEY `ix_climbing_brands_is_active_deleted` (`is_active`, `deleted_at`),
  KEY `ix_climbing_brands_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 4) climbing_gyms
--   - 사용자가 challenge 생성 시 실제로 선택하는 클라이밍장
--   - 브랜드와 FK로 연결
--   - 지도 API 외부 장소 정보 / 매칭 상태 관리
--   - 지점 전용 로고가 필요하면 브랜드 로고 override 가능
-- =========================================================
CREATE TABLE `climbing_gyms` (
  `id` INT NOT NULL AUTO_INCREMENT,

  `brand_id` INT NULL COMMENT '브랜드 FK (미매칭 시 NULL 가능)',
  `map_provider` ENUM('KAKAO','NAVER','GOOGLE','MANUAL') NULL,
  `external_place_id` VARCHAR(120) NULL,
  `place_name_raw` VARCHAR(150) NULL,

  `name` VARCHAR(100) NOT NULL COMMENT '예: 몽키즈클라이밍 고양화정점',
  `branch_name` VARCHAR(50) NULL COMMENT '예: 고양화정점',
  `display_name` VARCHAR(120) NOT NULL COMMENT '예: 몽키즈클라이밍 · 고양화정점',
  `region` VARCHAR(50) NULL COMMENT '예: 경기 고양',

  `address_name` VARCHAR(255) NULL,
  `road_address_name` VARCHAR(255) NULL,
  `latitude` DECIMAL(10,7) NULL,
  `longitude` DECIMAL(10,7) NULL,

  `logo_bucket` VARCHAR(100) NULL COMMENT '지점 전용 로고 bucket (없으면 브랜드 로고 사용)',
  `logo_object_key` VARCHAR(1024) NULL COMMENT '지점 전용 로고 object key',
  `logo_content_type` VARCHAR(100) NULL COMMENT '예: image/png',
  `logo_etag` VARCHAR(64) NULL COMMENT 'MinIO etag',

  `source_note` VARCHAR(255) NULL COMMENT '데이터 출처/비고',
  `gym_source` ENUM('CURATED','MAP_IMPORTED') NOT NULL DEFAULT 'CURATED',
  `grade_source` ENUM('CURATED','STANDARD_FALLBACK') NOT NULL DEFAULT 'CURATED',
  `match_status` ENUM('VERIFIED','AUTO_MATCHED','UNMATCHED_IMPORTED') NOT NULL DEFAULT 'VERIFIED',
  `needs_review` TINYINT(1) NOT NULL DEFAULT 0,
  `is_active` TINYINT(1) NOT NULL DEFAULT 1,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_climbing_gyms_display_name` (`display_name`),
  UNIQUE KEY `uk_climbing_gyms_provider_place` (`map_provider`, `external_place_id`),
  KEY `ix_climbing_gyms_brand_id` (`brand_id`),
  KEY `ix_climbing_gyms_name` (`name`),
  KEY `ix_climbing_gyms_region` (`region`),
  KEY `ix_climbing_gyms_lat_lng` (`latitude`, `longitude`),
  KEY `ix_climbing_gyms_is_active_deleted` (`is_active`, `deleted_at`),
  KEY `ix_climbing_gyms_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 5) climbing_gym_grades
--   - 클라이밍장별 난이도 색상 체계
--   - 같은 color_name이라도 gym마다 의미가 다름
-- =========================================================
CREATE TABLE `climbing_gym_grades` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `gym_id` INT NOT NULL,

  `color_name` VARCHAR(30) NOT NULL COMMENT '예: 빨강, 주황, 노랑, 초록',
  `sort_order` INT NOT NULL COMMENT '해당 gym 내부 난이도 순서(작을수록 쉬움)',
  `color_hex` VARCHAR(20) NULL COMMENT '예: #FF0000',
  `grade_label` VARCHAR(30) NULL COMMENT '예: 입문, 초급',
  `is_enabled` TINYINT(1) NOT NULL DEFAULT 1,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_climbing_gym_grades_gym_color` (`gym_id`, `color_name`),
  UNIQUE KEY `uk_climbing_gym_grades_gym_sort_order` (`gym_id`, `sort_order`),
  UNIQUE KEY `uk_climbing_gym_grades_id_gym` (`id`, `gym_id`),

  KEY `ix_climbing_gym_grades_gym_id` (`gym_id`),
  KEY `ix_climbing_gym_grades_gym_enabled_deleted` (`gym_id`, `is_enabled`, `deleted_at`),
  KEY `ix_climbing_gym_grades_deleted_at` (`deleted_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 6) challenges
--   - gym_id 필수
--   - gym_grade_id 필수
--   - snapshot 컬럼은 생성 시점 표시 안정성 보존용
-- =========================================================
CREATE TABLE `challenges` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL COMMENT '회원 기본 키',

  `gym_id` INT NOT NULL COMMENT '선택한 클라이밍장 ID',
  `gym_grade_id` INT NOT NULL COMMENT '선택한 클라이밍장 내부 난이도 ID',

  `gym_name_snapshot` VARCHAR(100) NOT NULL COMMENT '생성 시점 gym 표시명 snapshot',
  `problem_color_snapshot` VARCHAR(30) NOT NULL COMMENT '생성 시점 색상명 snapshot',
  `grade_label_snapshot` VARCHAR(30) NULL COMMENT '생성 시점 grade label snapshot',
  `sort_order_snapshot` INT NOT NULL COMMENT '생성 시점 난이도 순서 snapshot',

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
  KEY `ix_challenges_gym_id` (`gym_id`),
  KEY `ix_challenges_gym_grade_id` (`gym_grade_id`),
  KEY `ix_challenges_gym_grade_gym` (`gym_grade_id`, `gym_id`),

  KEY `ix_challenges_user_deleted_status_created` (`user_id`, `deleted_at`, `challenge_status`, `created_at`),
  KEY `ix_challenges_user_deleted_created` (`user_id`, `deleted_at`, `created_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 7) challenge_summaries (Challenge 1개당 1개)
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
-- 8) challenge_attempt_counters
-- =========================================================
CREATE TABLE `challenge_attempt_counters` (
  `challenge_id` INT NOT NULL,
  `next_attempt_no` INT NOT NULL DEFAULT 0,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`challenge_id`)
) ENGINE=InnoDB;

-- =========================================================
-- 9) attempts
-- =========================================================
CREATE TABLE `attempts` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `challenge_id` INT NOT NULL,
  `attempt_no` INT NOT NULL,

  `attempt_status` ENUM('UPLOADING','UPLOAD_FAILED','PROCESSING','ANALYSIS_FAILED','DONE')
    NOT NULL DEFAULT 'UPLOADING',
  `attempt_result` ENUM('SUCCESS','FAIL','UNKNOWN') NULL,

  `analysis_started_at` DATETIME NULL,
  `analysis_ended_at` DATETIME NULL,

  `duration_ms` INT NULL,
  `max_hold_no` INT NULL,

  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attempts_challenge_attempt_no` (`challenge_id`, `attempt_no`),

  KEY `ix_attempts_challenge_deleted_attemptno` (`challenge_id`, `deleted_at`, `attempt_no`),
  KEY `ix_attempts_challenge_deleted_created` (`challenge_id`, `deleted_at`, `created_at`)
) ENGINE=InnoDB;

-- =========================================================
-- 10) attempt_feedbacks
-- =========================================================
CREATE TABLE `attempt_feedbacks` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `attempt_id` INT NOT NULL,

  `failure_reason` VARCHAR(200) NULL,
  `risk_alert` VARCHAR(200) NULL,
  `next_mission` VARCHAR(200) NULL,

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
-- 11) attempt_video
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
-- 12) attempt_metrics
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

ALTER TABLE `climbing_gyms`
  ADD CONSTRAINT `fk_climbing_gyms_climbing_brands`
  FOREIGN KEY (`brand_id`) REFERENCES `climbing_brands` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `climbing_gym_grades`
  ADD CONSTRAINT `fk_climbing_gym_grades_climbing_gyms`
  FOREIGN KEY (`gym_id`) REFERENCES `climbing_gyms` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `challenges`
  ADD CONSTRAINT `fk_challenges_users`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_challenges_climbing_gyms`
  FOREIGN KEY (`gym_id`) REFERENCES `climbing_gyms` (`id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_challenges_climbing_gym_grades`
  FOREIGN KEY (`gym_grade_id`, `gym_id`) REFERENCES `climbing_gym_grades` (`id`, `gym_id`)
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
-- Trigger
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