-- Baseline schema, taken from the tables Hibernate had generated (ddl-auto: update) before Flyway.
-- Existing databases skip this file (baseline-on-migrate marks them as version 1); new ones run it.
-- From here on every schema change is a new V<n>__description.sql. Never edit an applied migration.

CREATE TABLE orders (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  user_id             BIGINT        NOT NULL,
  status              ENUM('CANCELLED','CONFIRMED','PAID','PENDING') NOT NULL,
  total_amount        DECIMAL(12,2) NOT NULL,
  cancellation_reason VARCHAR(255)  NULL,
  version             BIGINT        NULL,
  created_at          DATETIME(6)   NOT NULL,
  updated_at          DATETIME(6)   NULL,
  PRIMARY KEY (id),
  KEY idx_orders_user (user_id),
  KEY idx_orders_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  order_id   BIGINT        NOT NULL,
  product_id BIGINT        NOT NULL,
  quantity   INT           NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (id),
  KEY fk_order_items_order (order_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
