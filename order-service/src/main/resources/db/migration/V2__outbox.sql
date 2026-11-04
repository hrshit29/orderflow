-- Transactional outbox: events are written here in the same transaction as the business change,
-- then published to Kafka by OutboxRelay (common-outbox). A crash between DB commit and Kafka send
-- can no longer lose an event.
CREATE TABLE outbox_events (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  topic        VARCHAR(100) NOT NULL,
  message_key  VARCHAR(100) NOT NULL,
  event_type   VARCHAR(200) NOT NULL,
  payload      JSON         NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  published_at DATETIME(6)  NULL,
  attempts     INT          NOT NULL DEFAULT 0,
  last_error   VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_outbox_pending (published_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
