-- Baseline schema, taken from the tables Hibernate had generated (ddl-auto: update) before Flyway.
-- Existing databases skip this file (baseline-on-migrate marks them as version 1); new ones run it.
-- From here on every schema change is a new V<n>__description.sql. Never edit an applied migration.

CREATE TABLE products (
  id                 BIGINT        NOT NULL,
  name               VARCHAR(120)  NOT NULL,
  price              DECIMAL(12,2) NOT NULL,
  available_quantity INT           NOT NULL,
  reserved_quantity  INT           NOT NULL,
  version            BIGINT        NULL,
  created_at         DATETIME(6)   NOT NULL,
  updated_at         DATETIME(6)   NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_reservations (
  order_id   BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  status     ENUM('REJECTED','RELEASED','RESERVED') NOT NULL,
  reason     VARCHAR(255) NULL,
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NULL,
  PRIMARY KEY (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_reservation_lines (
  order_id   BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity   INT    NOT NULL,
  KEY fk_reservation_lines_reservation (order_id),
  CONSTRAINT fk_reservation_lines_reservation FOREIGN KEY (order_id) REFERENCES stock_reservations (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
