-- Baseline: the schema Hibernate (ddl-auto=update) created before Flyway was introduced, copied from a
-- MySQL replica built by the pre-Phase-3 code. Runs only on an EMPTY database. Existing databases are
-- baselined at version 1 instead (see docs/migrations/phase-4-flyway.md), so this script never runs there.

CREATE TABLE users (
  id bigint NOT NULL AUTO_INCREMENT,
  created_at datetime(6) NOT NULL,
  email varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  password_hash varchar(255) DEFAULT NULL,
  phone varchar(255) NOT NULL,
  role varchar(255) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK6dotkott2kjsp8vw4d0m25fb7 (email)
) ENGINE=InnoDB;
