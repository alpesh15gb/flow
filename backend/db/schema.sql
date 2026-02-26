-- Flow Cashflow Database Schema

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users (username/password + optional device-based)
CREATE TABLE IF NOT EXISTS users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id     TEXT,
  username      TEXT UNIQUE,
  password_hash TEXT,
  name          TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Businesses (one per user for now, extensible)
CREATE TABLE IF NOT EXISTS businesses (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT NOT NULL DEFAULT 'My Business',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Transactions
CREATE TABLE IF NOT EXISTS transactions (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id   TEXT NOT NULL,
  amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
  type        TEXT NOT NULL CHECK (type IN ('sale', 'expense', 'purchase')),
  note        TEXT,
  date        DATE NOT NULL,
  category    TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- User-defined custom categories
CREATE TABLE IF NOT EXISTS categories (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  type        TEXT NOT NULL CHECK (type IN ('expense', 'sale', 'purchase')),
  is_default  BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for fast sync queries
CREATE INDEX IF NOT EXISTS idx_transactions_user_updated ON transactions(user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date    ON transactions(user_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_users_device              ON users(device_id) WHERE device_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_username            ON users(username) WHERE username IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_category     ON transactions(user_id, category);
CREATE INDEX IF NOT EXISTS idx_categories_user           ON categories(user_id);

-- Slip tracker: parties
CREATE TABLE IF NOT EXISTS parties (
  id          TEXT PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Slip tracker: slips (metadata only — images stay on-device)
CREATE TABLE IF NOT EXISTS slips (
  id           TEXT PRIMARY KEY,
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  party_id     TEXT NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
  amount       NUMERIC(12,2) NOT NULL,
  amount_paid  NUMERIC(12,2) NOT NULL DEFAULT 0,
  date         DATE NOT NULL,
  status       TEXT NOT NULL,
  linked_tx_id TEXT,
  note         TEXT,
  due_date     DATE,
  is_payable   BOOLEAN NOT NULL DEFAULT false,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Slip tracker: collection payments
CREATE TABLE IF NOT EXISTS slip_collections (
  id          TEXT PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  party_id    TEXT NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
  amount_paid NUMERIC(12,2) NOT NULL,
  date        DATE NOT NULL,
  note        TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_parties_user_updated       ON parties(user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_slips_user_updated         ON slips(user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_slips_party                ON slips(party_id);
CREATE INDEX IF NOT EXISTS idx_slips_due_date             ON slips(user_id, due_date) WHERE due_date IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_slips_status               ON slips(user_id, status);
CREATE INDEX IF NOT EXISTS idx_slip_collections_user      ON slip_collections(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_slip_collections_party     ON slip_collections(party_id);
