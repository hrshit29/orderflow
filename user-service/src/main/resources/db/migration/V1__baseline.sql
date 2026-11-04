-- Baseline schema, taken from the tables Hibernate had generated (ddl-auto: update) before Flyway.
-- Existing databases skip this file (baseline-on-migrate marks them as version 1); new ones run it.
-- From here on every schema change is a new V<n>__description.sql. Never edit an applied migration.

CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(150) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  phone         VARCHAR(20)  NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('ADMIN','CUSTOMER') NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
