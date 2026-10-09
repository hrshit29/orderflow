-- One schema per service: services never read each other's tables.
CREATE DATABASE IF NOT EXISTS user_db;
CREATE DATABASE IF NOT EXISTS order_db;
CREATE DATABASE IF NOT EXISTS notification_db;

GRANT ALL PRIVILEGES ON user_db.* TO 'orderflow'@'%';
GRANT ALL PRIVILEGES ON order_db.* TO 'orderflow'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'orderflow'@'%';
FLUSH PRIVILEGES;
