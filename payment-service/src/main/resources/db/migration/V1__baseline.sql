-- Baseline schema, taken from the tables Hibernate had generated (ddl-auto: update) before Flyway.
-- Existing databases skip this file (baseline-on-migrate marks them as version 1); new ones run it.
-- From here on every schema change is a new V<n>__description.sql. Never edit an applied migration.

CREATE TABLE payments (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  order_id          BIGINT        NOT NULL,
  user_id           BIGINT        NOT NULL,
  amount            DECIMAL(12,2) NULL,
  status            ENUM('CANCELLED','FAILED','PENDING','REFUNDED','SUCCEEDED') NOT NULL,
  gateway_reference VARCHAR(64)   NULL,
  failure_reason    VARCHAR(255)  NULL,
  created_at        DATETIME(6)   NOT NULL,
  updated_at        DATETIME(6)   NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_order (order_id),
  KEY idx_payments_user (user_id),
  KEY idx_payments_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
