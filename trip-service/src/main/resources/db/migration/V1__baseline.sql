-- Baseline: the schema Hibernate (ddl-auto=update) created before Flyway was introduced, copied from a
-- MySQL replica built by the pre-Phase-3 code. Runs only on an EMPTY database. Existing databases are
-- baselined at version 1 instead (see docs/migrations/phase-4-flyway.md), so this script never runs there.

CREATE TABLE trips (
  id bigint NOT NULL AUTO_INCREMENT,
  budget decimal(12,2) NOT NULL,
  destination varchar(255) NOT NULL,
  end_date date NOT NULL,
  start_date date NOT NULL,
  status varchar(255) NOT NULL,
  trip_code varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UKsp0nm9rujn2bx6kexw6uv35ks (trip_code)
) ENGINE=InnoDB;
