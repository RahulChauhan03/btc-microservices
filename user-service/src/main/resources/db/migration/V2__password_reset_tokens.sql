-- Self-service password reset. Stores only the SHA-256 hash of each emailed token (never the token itself),
-- with its expiry and single-use / revocation state. Additive only: creates one new table; existing users and
-- data are untouched. Deleting a user removes their reset tokens.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL,
  token_hash varchar(64) NOT NULL,
  expires_at datetime(6) NOT NULL,
  created_at datetime(6) NOT NULL,
  used_at datetime(6) DEFAULT NULL,
  revoked_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_tokens_token_hash (token_hash),
  KEY idx_password_reset_tokens_user_id (user_id),
  CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;
