CREATE TABLE IF NOT EXISTS kaisya_challenges (
  challenge TEXT PRIMARY KEY,
  purpose TEXT NOT NULL CHECK (purpose IN ('register', 'login')),
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER
);

CREATE TABLE IF NOT EXISTS kaisya_credentials (
  credential_id TEXT PRIMARY KEY,
  public_key_b64 TEXT NOT NULL,
  sign_count INTEGER NOT NULL DEFAULT 0,
  owner INTEGER NOT NULL DEFAULT 0 CHECK (owner IN (0, 1)),
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS kaisya_sessions (
  token_hash TEXT PRIMARY KEY,
  credential_id TEXT NOT NULL REFERENCES kaisya_credentials(credential_id),
  expires_at INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS kaisya_sessions_credential
  ON kaisya_sessions (credential_id, expires_at);
