-- Baseline: the schema Hibernate (ddl-auto=update) created before Flyway was introduced, copied from a
-- MySQL replica built by the pre-Phase-3 code. Runs only on an EMPTY database. Existing databases are
-- baselined at version 1 instead (see docs/migrations/phase-4-flyway.md), so this script never runs there.

CREATE TABLE claims (
  id bigint NOT NULL AUTO_INCREMENT,
  claim_amount decimal(12,2) NOT NULL,
  claim_number varchar(255) NOT NULL,
  description varchar(1000) DEFAULT NULL,
  status varchar(255) NOT NULL,
  submitted_at datetime(6) NOT NULL,
  title varchar(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY UK8prfn2h4t4bpdy5s6lonblk7m (claim_number)
) ENGINE=InnoDB;
