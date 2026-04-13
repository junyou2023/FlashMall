-- ==============================================================
-- 支付模块第二阶段：Stripe Webhook 异步确认
--
-- 目标：
-- 1) 新增 webhook 事件去重表，保证重复推送不重复推进状态
-- 2) 为 payment_order 增加 webhook 查询所需索引
-- ============================================================== 

-- 第 1 步：创建 webhook 事件表
CREATE TABLE IF NOT EXISTS payment_webhook_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider VARCHAR(32) NOT NULL,
  provider_event_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  processed TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  processed_at DATETIME NULL,
  -- provider + eventId 唯一：这是 webhook 去重核心锚点
  UNIQUE KEY uk_payment_webhook_provider_event (provider, provider_event_id),
  KEY idx_payment_webhook_event_type (event_type),
  KEY idx_payment_webhook_processed (processed)
) ENGINE=InnoDB;

-- 第 2 步：补 webhook 常用检索索引
-- payment_intent.* 回调按 stripe_payment_intent_id 定位本地支付单
CREATE INDEX idx_payment_order_intent_id ON payment_order (stripe_payment_intent_id);

-- checkout.session.* 回调按 stripe_checkout_session_id 定位本地支付单
CREATE INDEX idx_payment_order_session_id ON payment_order (stripe_checkout_session_id);
