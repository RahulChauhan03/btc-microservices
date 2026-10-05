-- Phase 7: company travel policy. One policy applies to an expense date (effective periods never overlap);
-- it may cap single expenses per category and a trip's total spending. Amounts use the application's single
-- currency (expenses carry no currency), stored as decimal(12,2) like expense amounts.
-- Additive only: two new tables; existing expenses are untouched and not re-validated.

CREATE TABLE IF NOT EXISTS travel_policies (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(100) NOT NULL,
  currency varchar(3) NOT NULL,
  trip_limit decimal(12,2) DEFAULT NULL,
  effective_from date NOT NULL,
  effective_to date DEFAULT NULL,
  created_by bigint NOT NULL,
  updated_by bigint NOT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_travel_policies_effective (effective_from, effective_to)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS travel_policy_limits (
  policy_id bigint NOT NULL,
  category varchar(40) NOT NULL,
  limit_amount decimal(12,2) NOT NULL,
  PRIMARY KEY (policy_id, category),
  CONSTRAINT fk_travel_policy_limits_policy FOREIGN KEY (policy_id) REFERENCES travel_policies (id) ON DELETE CASCADE
) ENGINE=InnoDB;
