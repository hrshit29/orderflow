-- Baseline schema, taken from the tables Hibernate had generated (ddl-auto: update) before Flyway.
-- Existing databases skip this file (baseline-on-migrate marks them as version 1); new ones run it.
-- From here on every schema change is a new V<n>__description.sql. Never edit an applied migration.

CREATE TABLE user_contacts (
  user_id   BIGINT       NOT NULL,
  email     VARCHAR(150) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_log (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  event_key      VARCHAR(100)  NOT NULL,
  event_type     VARCHAR(50)   NOT NULL,
  user_id        BIGINT        NOT NULL,
  channel        ENUM('EMAIL','PUSH','SMS') NOT NULL,
  recipient      VARCHAR(150)  NULL,
  subject        VARCHAR(200)  NOT NULL,
  body           VARCHAR(2000) NOT NULL,
  status         ENUM('FAILED','SENT','SKIPPED') NOT NULL,
  failure_reason VARCHAR(500)  NULL,
  created_at     DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_event_key (event_key),
  KEY idx_notification_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
