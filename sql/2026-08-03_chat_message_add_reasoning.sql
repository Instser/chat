-- 推理思考内容独立列（仅展示/审计，不回喂模型）
-- MySQL 8.0
-- 北京时间：2026-08-03

ALTER TABLE t_chat_message
  ADD COLUMN reasoning MEDIUMTEXT NULL
  COMMENT '推理思考内容（仅展示/审计，不回喂模型）'
  AFTER content;
