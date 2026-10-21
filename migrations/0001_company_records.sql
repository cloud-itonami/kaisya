-- One company snapshot and its audit events live in the same versioned row.
-- Writers use optimistic compare-and-swap; a stale writer must retry from the
-- latest state instead of silently overwriting another person's posting.
CREATE TABLE IF NOT EXISTS kaisya_company_records (
  organization_id TEXT PRIMARY KEY,
  version INTEGER NOT NULL CHECK (version > 0),
  record_edn TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
