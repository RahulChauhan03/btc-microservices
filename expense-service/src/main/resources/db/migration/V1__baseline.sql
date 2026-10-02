-- Baseline: the schema Hibernate (ddl-auto=update) created before Flyway was introduced, copied from a
-- MySQL replica built by the pre-Phase-3 code. Runs only on an EMPTY database. Existing databases are
-- baselined at version 1 instead (see docs/migrations/phase-4-flyway.md), so this script never runs there.

CREATE TABLE expenses (
  id bigint NOT NULL AUTO_INCREMENT,
  amount decimal(12,2) NOT NULL,
  category varchar(255) NOT NULL,
  created_at datetime(6) NOT NULL,
  description varchar(1000) DEFAULT NULL,
  expense_date date NOT NULL,
  title varchar(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;
