-- ============================================================
-- Migration: operation_logs 可读性与登录审计增强
-- 日期: 2026-10-06
-- 内容:
--   1) 新增 username(操作者快照) / module(模块) / status(成功失败) 三列
--   2) 删除 user_id 外键（保留索引）——用户删除后审计日志仍可追溯，
--      同时修复 deleteUser 因外键报错的隐性 bug
--   3) 回填历史数据的 username
-- 执行: docker exec -i blog_mysql mysql --default-character-set=utf8mb4 bioplatform < 本文件
-- ============================================================

-- 执行前留底
SELECT COUNT(*) AS total_logs FROM `operation_logs`;

ALTER TABLE `operation_logs`
  ADD COLUMN `username` VARCHAR(64) DEFAULT NULL COMMENT '操作者用户名快照' AFTER `user_id`,
  ADD COLUMN `module`   VARCHAR(64) DEFAULT NULL COMMENT '模块，如 用户管理/认证' AFTER `operation`,
  ADD COLUMN `status`   VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL' AFTER `result`,
  DROP FOREIGN KEY `fk_ol_user`,
  ADD INDEX `idx_ol_username` (`username`),
  ADD INDEX `idx_ol_status` (`status`);

-- 历史数据回填用户名
UPDATE `operation_logs` ol
  JOIN `users` u ON ol.`user_id` = u.`id`
   SET ol.`username` = u.`username`
 WHERE ol.`username` IS NULL;

-- 历史数据默认状态（老数据均为正常返回路径写入）
UPDATE `operation_logs` SET `status` = 'SUCCESS' WHERE `status` = '' OR `status` IS NULL;

SELECT COUNT(*) AS total_logs, SUM(username IS NOT NULL) AS with_username FROM `operation_logs`;
