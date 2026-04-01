-- =========================================================
-- DDGo presentation demo extra seed for demo master account
-- Target account: user_id 9005
--
-- Purpose:
--   - remove previously added extra March demo records
--   - reinsert all extra records in clean Korean text
--   - keep 부담 집중 부위 labels in Korean only
--   - make several dates show 2 to 6 challenges in calendar detail
--   - add many DONE analysis results for presentation
--
-- Prerequisites:
--   1) Base schema is already loaded
--   2) climbing gym seed data is already loaded
--   3) presentation_demo_seed.sql has already been applied
--
-- Result after this script:
--   - old extra demo records from previous March extra seeds are removed
--   - 24 extra challenges are inserted for user 9005
--   - 73 extra DONE analysis results are inserted in March 2026
--   - base demo data remains untouched
-- =========================================================

USE `ddgo_db`;
SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------
-- Cleanup old extra records from the previous extra seed
-- ---------------------------------------------------------
DELETE FROM `attempt_feedbacks` WHERE `id` BETWEEN 9447 AND 9466;
DELETE FROM `attempt_metrics` WHERE `id` BETWEEN 9547 AND 9566;
DELETE FROM `attempt_stability_points` WHERE `attempt_id` BETWEEN 9352 AND 9371;
DELETE FROM `attempt_heart_rate_samples` WHERE `attempt_id` BETWEEN 9352 AND 9371;
DELETE FROM `attempt_video` WHERE `attempt_id` BETWEEN 9352 AND 9371;
DELETE FROM `attempts` WHERE `id` BETWEEN 9352 AND 9371;
DELETE FROM `challenge_summaries` WHERE `id` BETWEEN 9606 AND 9620;
DELETE FROM `challenge_attempt_counters` WHERE `challenge_id` BETWEEN 9211 AND 9225;
DELETE FROM `challenges` WHERE `id` BETWEEN 9211 AND 9225;

-- ---------------------------------------------------------
-- Cleanup this script range for reruns
-- ---------------------------------------------------------
DELETE FROM `attempt_feedbacks` WHERE `id` BETWEEN 11401 AND 11474;
DELETE FROM `attempt_metrics` WHERE `id` BETWEEN 10401 AND 10474;
DELETE FROM `attempt_stability_points` WHERE `attempt_id` BETWEEN 9401 AND 9474;
DELETE FROM `attempt_heart_rate_samples` WHERE `attempt_id` BETWEEN 9401 AND 9474;
DELETE FROM `attempt_video` WHERE `attempt_id` BETWEEN 9401 AND 9474;
DELETE FROM `attempts` WHERE `id` BETWEEN 9401 AND 9474;
DELETE FROM `challenge_summaries` WHERE `id` BETWEEN 12301 AND 12324;
DELETE FROM `challenge_attempt_counters` WHERE `challenge_id` BETWEEN 9301 AND 9324;
DELETE FROM `challenges` WHERE `id` BETWEEN 9301 AND 9324;

DROP TEMPORARY TABLE IF EXISTS `tmp_demo_challenge_seed`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_numbers`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_series_numbers`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_extra_attempts`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_extra_metrics`;

CREATE TEMPORARY TABLE `tmp_demo_challenge_seed` (
  `challenge_id` INT NOT NULL,
  `gym_id` INT NOT NULL,
  `gym_grade_id` INT NOT NULL,
  `gym_name_snapshot` VARCHAR(100) NOT NULL,
  `problem_color_snapshot` VARCHAR(30) NOT NULL,
  `sort_order_snapshot` INT NOT NULL,
  `started_at` DATETIME NOT NULL,
  `attempt_gap_days` INT NOT NULL,
  `total_attempts` INT NOT NULL,
  `success_attempt_no` INT NOT NULL,
  `focus_label_ko` VARCHAR(10) NOT NULL,
  `base_overall_score` INT NOT NULL,
  PRIMARY KEY (`challenge_id`)
) ENGINE=MEMORY;

INSERT INTO `tmp_demo_challenge_seed` (
  `challenge_id`, `gym_id`, `gym_grade_id`, `gym_name_snapshot`,
  `problem_color_snapshot`, `sort_order_snapshot`, `started_at`,
  `attempt_gap_days`, `total_attempts`, `success_attempt_no`,
  `focus_label_ko`, `base_overall_score`
) VALUES
  (9301, 12,  97, '그래비티클라이밍 수원',   '빨강', 1, '2026-03-02 18:30:00', 1, 4, 4, '왼팔',   47),
  (9302, 20, 165, '클라임데이즈 범계점',     '주황', 2, '2026-03-02 18:50:00', 1, 4, 4, '오른다리', 49),
  (9303, 11,  93, '손상원 클라이밍짐 판교점', '핑크', 8, '2026-03-02 19:05:00', 1, 3, 3, '오른팔', 45),
  (9304, 12, 100, '그래비티클라이밍 수원',   '초록', 4, '2026-03-02 19:20:00', 2, 3, 3, '몸통',   52),
  (9305, 20, 170, '클라임데이즈 범계점',     '보라', 7, '2026-03-02 19:40:00', 1, 2, 2, '몸통',   55),
  (9306, 11,  91, '손상원 클라이밍짐 판교점', '파랑', 5, '2026-03-02 20:00:00', 2, 2, 0, '왼다리', 43),

  (9307, 20, 164, '클라임데이즈 범계점',     '빨강', 1, '2026-03-07 18:20:00', 1, 4, 4, '왼다리', 46),
  (9308, 12,  99, '그래비티클라이밍 수원',   '노랑', 3, '2026-03-07 18:45:00', 1, 3, 3, '오른다리', 50),
  (9309, 11,  94, '손상원 클라이밍짐 판교점', '흰색', 1, '2026-03-07 19:00:00', 2, 2, 0, '몸통',   42),
  (9310, 12, 104, '그래비티클라이밍 수원',   '핑크', 8, '2026-03-07 19:20:00', 1, 4, 4, '몸통',   56),
  (9311, 20, 169, '클라임데이즈 범계점',     '남색', 6, '2026-03-07 19:50:00', 2, 2, 2, '오른팔', 49),

  (9312, 11,  89, '손상원 클라이밍짐 판교점', '주황', 2, '2026-03-13 18:40:00', 1, 3, 3, '오른팔', 48),
  (9313, 20, 167, '클라임데이즈 범계점',     '초록', 4, '2026-03-13 19:00:00', 1, 3, 3, '왼다리', 52),
  (9314, 12, 101, '그래비티클라이밍 수원',   '파랑', 5, '2026-03-13 19:20:00', 1, 4, 0, '오른팔', 44),
  (9315, 11,  93, '손상원 클라이밍짐 판교점', '보라', 7, '2026-03-13 19:45:00', 2, 2, 2, '몸통',   55),

  (9316, 12,  97, '그래비티클라이밍 수원',   '빨강', 1, '2026-03-18 18:30:00', 1, 4, 4, '왼팔',   48),
  (9317, 11,  94, '손상원 클라이밍짐 판교점', '흰색', 1, '2026-03-18 19:00:00', 2, 3, 0, '몸통',   43),
  (9318, 11,  93, '손상원 클라이밍짐 판교점', '핑크', 8, '2026-03-18 19:40:00', 1, 2, 2, '오른다리', 57),

  (9319, 12,  98, '그래비티클라이밍 수원',   '주황', 2, '2026-03-23 18:40:00', 2, 4, 4, '몸통',   53),
  (9320, 20, 167, '클라임데이즈 범계점',     '초록', 4, '2026-03-23 19:15:00', 2, 3, 3, '왼다리', 54),

  (9321, 11,  89, '손상원 클라이밍짐 판교점', '노랑', 3, '2026-03-05 18:30:00', 1, 4, 4, '오른팔', 50),
  (9322, 20, 168, '클라임데이즈 범계점',     '파랑', 5, '2026-03-10 19:10:00', 2, 3, 0, '왼팔',   44),
  (9323, 12, 103, '그래비티클라이밍 수원',   '보라', 7, '2026-03-15 19:30:00', 1, 3, 3, '몸통',   56),
  (9324, 11,  87, '손상원 클라이밍짐 판교점', '빨강', 1, '2026-03-27 19:00:00', 1, 2, 2, '오른다리', 58);

INSERT INTO `challenges` (
  `id`, `user_id`, `gym_id`, `gym_grade_id`,
  `gym_name_snapshot`, `problem_color_snapshot`, `grade_label_snapshot`, `sort_order_snapshot`,
  `challenge_status`, `challenge_result`, `started_at`, `ended_at`, `holds_json`,
  `created_at`, `updated_at`
)
SELECT
  `challenge_id`,
  9005,
  `gym_id`,
  `gym_grade_id`,
  `gym_name_snapshot`,
  `problem_color_snapshot`,
  NULL,
  `sort_order_snapshot`,
  'CLOSED',
  CASE
    WHEN `success_attempt_no` > 0 THEN 'SUCCESS'
    ELSE 'FAIL'
  END,
  `started_at`,
  DATE_ADD(`started_at`, INTERVAL (((`total_attempts` - 1) * `attempt_gap_days` * 24 * 60) + 45) MINUTE),
  NULL,
  `started_at`,
  DATE_ADD(`started_at`, INTERVAL (((`total_attempts` - 1) * `attempt_gap_days` * 24 * 60) + 45) MINUTE)
FROM `tmp_demo_challenge_seed`
ORDER BY `challenge_id`;

INSERT INTO `challenge_attempt_counters` (
  `challenge_id`, `next_attempt_no`, `created_at`, `updated_at`
)
SELECT
  `challenge_id`,
  `total_attempts` + 1,
  `started_at`,
  DATE_ADD(`started_at`, INTERVAL (((`total_attempts` - 1) * `attempt_gap_days` * 24 * 60) + 45) MINUTE)
FROM `tmp_demo_challenge_seed`
ORDER BY `challenge_id`
ON DUPLICATE KEY UPDATE
  `next_attempt_no` = VALUES(`next_attempt_no`),
  `updated_at` = VALUES(`updated_at`);

CREATE TEMPORARY TABLE `tmp_demo_numbers` (
  `n` INT NOT NULL,
  PRIMARY KEY (`n`)
) ENGINE=MEMORY;

INSERT INTO `tmp_demo_numbers` (`n`) VALUES (1), (2), (3), (4);

CREATE TEMPORARY TABLE `tmp_demo_series_numbers` (
  `n` INT NOT NULL,
  PRIMARY KEY (`n`)
) ENGINE=MEMORY;

INSERT INTO `tmp_demo_series_numbers` (`n`) VALUES (0), (1), (2), (3), (4), (5);

CREATE TEMPORARY TABLE `tmp_demo_extra_attempts` AS
SELECT
  9400 + ROW_NUMBER() OVER (ORDER BY c.`challenge_id`, n.`n`) AS `attempt_id`,
  c.`challenge_id`,
  n.`n` AS `attempt_no`,
  CASE
    WHEN c.`success_attempt_no` > 0 AND n.`n` = c.`success_attempt_no` THEN 'SUCCESS'
    ELSE 'FAIL'
  END AS `attempt_result`,
  DATE_ADD(c.`started_at`, INTERVAL ((((n.`n` - 1) * c.`attempt_gap_days`) * 24 * 60) + (n.`n` * 11)) MINUTE) AS `analysis_started_at`,
  DATE_ADD(c.`started_at`, INTERVAL ((((n.`n` - 1) * c.`attempt_gap_days`) * 24 * 60) + (n.`n` * 11) + 1) MINUTE) AS `analysis_ended_at`,
  17000 + (n.`n` * 1900) + (c.`sort_order_snapshot` * 450) AS `duration_ms`,
  LEAST(10, 2 + n.`n` + FLOOR(c.`sort_order_snapshot` / 2)) AS `max_hold_no`,
  c.`problem_color_snapshot`,
  c.`focus_label_ko`,
  c.`base_overall_score`,
  c.`sort_order_snapshot`,
  c.`total_attempts`,
  c.`success_attempt_no`
FROM `tmp_demo_challenge_seed` c
JOIN `tmp_demo_numbers` n
  ON n.`n` <= c.`total_attempts`;

INSERT INTO `attempts` (
  `id`, `challenge_id`, `attempt_no`, `attempt_status`, `attempt_result`,
  `analysis_started_at`, `analysis_ended_at`, `duration_ms`, `max_hold_no`,
  `created_at`, `updated_at`
)
SELECT
  `attempt_id`,
  `challenge_id`,
  `attempt_no`,
  'DONE',
  `attempt_result`,
  `analysis_started_at`,
  `analysis_ended_at`,
  `duration_ms`,
  `max_hold_no`,
  `analysis_started_at`,
  `analysis_ended_at`
FROM `tmp_demo_extra_attempts`
ORDER BY `attempt_id`;

CREATE TEMPORARY TABLE `tmp_demo_extra_metrics` AS
SELECT
  a.`attempt_id`,
  a.`challenge_id`,
  ROUND(LEAST(0.89, ((a.`base_overall_score` - 6 + a.`attempt_no` * 5) / 100)), 2) AS `center_stability_ratio`,
  LEAST(90, a.`base_overall_score` - 4 + a.`attempt_no` * 5) AS `stability_recovery_score`,
  ROUND(LEAST(0.86, ((a.`base_overall_score` - 10 + a.`attempt_no` * 5) / 100)), 2) AS `stable_contact_ratio`,
  LEAST(92, a.`base_overall_score` - 2 + a.`attempt_no` * 6) AS `lower_body_drive_score`,
  CASE
    WHEN a.`attempt_result` = 'SUCCESS' THEN LEAST(95, a.`base_overall_score` + a.`attempt_no` * 7)
    ELSE LEAST(88, a.`base_overall_score` + a.`attempt_no` * 4)
  END AS `overall_movement_score`,
  LEAST(10, 3 + FLOOR(a.`sort_order_snapshot` / 2) + FLOOR(a.`attempt_no` / 2)) AS `crux_hold_no`,
  GREATEST(1200, 4300 - (a.`attempt_no` * 380)) AS `crux_duration_ms`,
  CASE
    WHEN a.`attempt_result` = 'SUCCESS' THEN GREATEST(0, a.`total_attempts` - a.`attempt_no`)
    ELSE GREATEST(1, a.`total_attempts` - a.`attempt_no` + 1)
  END AS `danger_event_count`,
  a.`focus_label_ko` AS `load_focus_label`,
  a.`analysis_ended_at` AS `metric_time`
FROM `tmp_demo_extra_attempts` a;

INSERT INTO `attempt_metrics` (
  `id`, `attempt_id`, `center_stability_ratio`, `stability_recovery_score`,
  `stable_contact_ratio`, `lower_body_drive_score`, `overall_movement_score`,
  `crux_hold_no`, `crux_duration_ms`, `danger_event_count`, `load_focus_label`,
  `created_at`, `updated_at`
)
SELECT
  `attempt_id` + 1000,
  `attempt_id`,
  `center_stability_ratio`,
  `stability_recovery_score`,
  `stable_contact_ratio`,
  `lower_body_drive_score`,
  `overall_movement_score`,
  `crux_hold_no`,
  `crux_duration_ms`,
  `danger_event_count`,
  `load_focus_label`,
  `metric_time`,
  `metric_time`
FROM `tmp_demo_extra_metrics`
ORDER BY `attempt_id`;

INSERT INTO `attempt_stability_points` (
  `attempt_id`, `point_order`, `timestamp_ms`, `stability_score`, `created_at`
)
SELECT
  a.`attempt_id`,
  s.`n`,
  ROUND((a.`duration_ms` * s.`n`) / 5, 0),
  ROUND(
    LEAST(
      0.95,
      GREATEST(
        0.24,
        (
          (a.`base_overall_score` - 16)
          + (a.`attempt_no` * 4)
          + (s.`n` * 5)
          + CASE
              WHEN a.`attempt_result` = 'SUCCESS' AND s.`n` = 5 THEN 4
              ELSE 0
            END
        ) / 100
      )
    ),
    2
  ) AS `stability_score`,
  a.`analysis_ended_at`
FROM `tmp_demo_extra_attempts` a
JOIN `tmp_demo_series_numbers` s
ORDER BY a.`attempt_id`, s.`n`;

INSERT INTO `attempt_heart_rate_samples` (
  `attempt_id`, `sample_order`, `timestamp_ms`, `bpm`, `created_at`
)
SELECT
  a.`attempt_id`,
  s.`n`,
  ROUND((a.`duration_ms` * s.`n`) / 5, 0),
  LEAST(
    188,
    GREATEST(
      104,
      104
      + (a.`sort_order_snapshot` * 2)
      + (a.`attempt_no` * 5)
      + (s.`n` * 6)
      + CASE
          WHEN a.`attempt_result` = 'FAIL' AND s.`n` >= 4 THEN 4
          WHEN a.`attempt_result` = 'SUCCESS' AND s.`n` = 5 THEN -2
          ELSE 0
        END
    )
  ) AS `bpm`,
  a.`analysis_ended_at`
FROM `tmp_demo_extra_attempts` a
JOIN `tmp_demo_series_numbers` s
ORDER BY a.`attempt_id`, s.`n`;

INSERT INTO `attempt_feedbacks` (
  `id`, `attempt_id`, `failure_reason`, `risk_alert`, `next_mission`,
  `model_version`, `prompt_version`, `generated_at`, `created_at`, `updated_at`
)
SELECT
  a.`attempt_id` + 2000,
  a.`attempt_id`,
  CASE
    WHEN a.`attempt_result` = 'SUCCESS' THEN CONCAT(a.`problem_color_snapshot`, ' 문제에서 리듬이 좋아지며 완등까지 자연스럽게 이어졌어요.')
    ELSE CONCAT(a.`problem_color_snapshot`, ' 문제에서 ', a.`focus_label_ko`, ' 부담이 커지며 동작이 끊겼어요.')
  END,
  CASE
    WHEN a.`attempt_result` = 'SUCCESS' THEN CONCAT(a.`focus_label_ko`, ' 긴장만 조금 더 줄이면 더 안정적이에요.')
    ELSE CONCAT(a.`focus_label_ko`, '에 부담이 집중됐어요.')
  END,
  CASE
    WHEN a.`attempt_result` = 'SUCCESS' THEN CONCAT('비슷한 ', a.`problem_color_snapshot`, ' 난이도에서 같은 흐름을 다시 재현해 보세요.')
    WHEN a.`focus_label_ko` = '몸통' THEN '다음 시도에서는 중심부터 먼저 고정하고 이어 가보세요.'
    WHEN a.`focus_label_ko` = '왼팔' THEN '다음 시도에서는 왼팔보다 발을 먼저 쓰고 이어 가보세요.'
    WHEN a.`focus_label_ko` = '오른팔' THEN '다음 시도에서는 오른팔보다 하체를 먼저 밀어 보세요.'
    WHEN a.`focus_label_ko` = '왼다리' THEN '다음 시도에서는 왼발 지지를 먼저 만들고 손을 이어 가보세요.'
    WHEN a.`focus_label_ko` = '오른다리' THEN '다음 시도에서는 오른발 지지를 먼저 만들고 손을 이어 가보세요.'
    ELSE '다음 시도에서는 중심을 먼저 잡고 이어 가보세요.'
  END,
  'demo-model-v2',
  'demo-prompt-v2',
  a.`analysis_ended_at`,
  a.`analysis_ended_at`,
  a.`analysis_ended_at`
FROM `tmp_demo_extra_attempts` a
ORDER BY a.`attempt_id`;

INSERT INTO `challenge_summaries` (
  `id`, `challenge_id`, `average_center_stability_ratio`, `most_crux_hold_no`,
  `max_crux_duration_ms`, `final_comment`, `created_at`, `updated_at`
)
SELECT
  c.`challenge_id` + 3000,
  c.`challenge_id`,
  ROUND(AVG(m.`center_stability_ratio`), 2),
  MAX(m.`crux_hold_no`),
  MAX(m.`crux_duration_ms`),
  CASE
    WHEN c.`success_attempt_no` > 0 THEN CONCAT(
      c.`problem_color_snapshot`,
      ' 문제는 시도할수록 ',
      c.`focus_label_ko`,
      ' 부담이 줄고 동작 연결이 안정되는 성장 흐름이 잘 보입니다.'
    )
    ELSE CONCAT(
      c.`problem_color_snapshot`,
      ' 문제는 ',
      c.`focus_label_ko`,
      ' 부담 관리와 크럭스 대응이 다음 과제로 남아 있습니다.'
    )
  END,
  DATE_ADD(c.`started_at`, INTERVAL (((c.`total_attempts` - 1) * c.`attempt_gap_days` * 24 * 60) + 45) MINUTE),
  DATE_ADD(c.`started_at`, INTERVAL (((c.`total_attempts` - 1) * c.`attempt_gap_days` * 24 * 60) + 45) MINUTE)
FROM `tmp_demo_challenge_seed` c
JOIN `tmp_demo_extra_metrics` m
  ON m.`challenge_id` = c.`challenge_id`
GROUP BY
  c.`challenge_id`,
  c.`problem_color_snapshot`,
  c.`focus_label_ko`,
  c.`success_attempt_no`,
  c.`started_at`,
  c.`total_attempts`,
  c.`attempt_gap_days`
ORDER BY c.`challenge_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_demo_extra_metrics`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_extra_attempts`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_series_numbers`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_numbers`;
DROP TEMPORARY TABLE IF EXISTS `tmp_demo_challenge_seed`;

COMMIT;
