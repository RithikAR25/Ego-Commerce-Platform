# Auth Schema — EGO Platform

> **Source:** `schema_v2.sql` (pre-existing, referenced by JPA entities)
> **DDL strategy:** `ddl-auto=update` (JPA manages schema; Flyway in Phase 14)

---

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────┐
│  users                                              │
│  ─────────────────────────────────────────────────  │
│  PK  id              BIGINT UNSIGNED AUTO_INCREMENT  │
│  UQ  email           VARCHAR(254)                   │
│      password_hash   VARCHAR(255)  -- BCrypt-12      │
│      phone           VARCHAR(20)   NULLABLE          │
│      first_name      VARCHAR(100)                   │
│      last_name       VARCHAR(100)                   │
│      role            ENUM('customer','admin')        │
│      is_active       TINYINT(1) DEFAULT 1            │
│      is_email_verified TINYINT(1) DEFAULT 0          │
│      is_deleted      TINYINT(1) DEFAULT 0            │
│      password_changed_at DATETIME(3) NULLABLE        │ ← JWT invalidation guard
│      last_login_at   DATETIME(3) NULLABLE            │
│      version         INT UNSIGNED DEFAULT 1          │ ← Optimistic lock
│      created_at      DATETIME(3)                    │
│      updated_at      DATETIME(3) ON UPDATE           │
└───────────────────────────┬─────────────────────────┘
                            │ 1:N
                            ▼
┌─────────────────────────────────────────────────────┐
│  refresh_tokens                                     │
│  ─────────────────────────────────────────────────  │
│  PK  id              BIGINT UNSIGNED AUTO_INCREMENT  │
│  FK  user_id         → users.id  ON DELETE CASCADE   │
│  UQ  token_hash      VARCHAR(255) -- SHA-256 of raw  │
│      family_id       CHAR(36)     -- UUID            │ ← Rotation group
│      device_hint     VARCHAR(255) NULLABLE           │
│      ip_address      VARCHAR(45)  NULLABLE           │
│      expires_at      DATETIME(3)                    │
│      revoked_at      DATETIME(3)  NULLABLE           │ ← NULL = valid
│      created_at      DATETIME(3)                    │
└─────────────────────────────────────────────────────┘
```

---

## Table: `users`

### Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `PRIMARY` | `id` | Row lookup |
| `uq_users_email` | `email` | Unique registration |
| `idx_users_phone` | `phone` | Phone lookup |
| `idx_users_role` | `role, is_active, is_deleted` | Admin dashboard filter |

### Important Columns

**`password_hash`**
- BCrypt hash, strength 12. Never the raw password.
- Never logged, never returned in any API response.

**`password_changed_at`**
- Set to `NOW()` on every successful password change or reset.
- The JWT filter rejects any token with `iat < password_changed_at`.
- This provides instant token invalidation without a Redis blocklist.

**`version`**
- JPA `@Version` optimistic lock.
- Prevents concurrent user profile updates from silently overwriting each other.
- DB default = 1 to match Java entity default.

**`is_deleted`**
- Soft delete — users are never hard-deleted (audit requirements).
- All queries append `AND is_deleted = 0` via repository method naming.

---

## Table: `refresh_tokens`

### Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `PRIMARY` | `id` | Row lookup |
| `uq_refresh_hash` | `token_hash` | Token lookup on each refresh/logout |
| `idx_refresh_user` | `user_id, expires_at` | List user's active sessions |
| `idx_refresh_family` | `family_id` | Family-wide revocation (bulk UPDATE) |

### Token Lifecycle

```
State Machine:

  CREATED (revokedAt=NULL, expiresAt=future)
      │
      ├── Normal refresh ──► ROTATED (revokedAt=NOW)  → new token created in same family
      │
      ├── Logout ──────────► REVOKED (revokedAt=NOW)
      │
      ├── Expired (expiresAt < NOW) ──► client must login again
      │
      └── Reuse detected ──► ENTIRE FAMILY REVOKED
                             (all tokens in family: revokedAt=NOW)
```

### family_id Pattern

```
Login session 1:
  family_id = "abc-123"
  token_1 (hash=aaa) → token_2 (hash=bbb) → token_3 (hash=ccc)
  All share family_id = "abc-123"

Login session 2 (new login):
  family_id = "xyz-789"
  token_4 (hash=ddd) → token_5 (hash=eee)
  Different family — independent revocation chain

Attack scenario:
  Attacker steals token_2 and uses it after token_3 was already issued.
  token_2.revokedAt IS NOT NULL → REUSE DETECTED
  → revokeAllByFamilyId("abc-123") → ALL of session 1 terminated
  → Legitimate user must log in again (they will notice)
```

---

## Planned Tables (Phase 7 — SendGrid)

```sql
-- Email verification tokens
CREATE TABLE email_verification_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(255)    NOT NULL UNIQUE,  -- SHA-256
    expires_at  DATETIME(3)     NOT NULL,          -- 24 hours
    used_at     DATETIME(3)     DEFAULT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(255)    NOT NULL UNIQUE,  -- SHA-256
    expires_at  DATETIME(3)     NOT NULL,          -- 15 minutes
    used_at     DATETIME(3)     DEFAULT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
```

---

## Maintenance Notes

**Stale token cleanup** (Phase 14 — scheduled job):
```sql
-- Run nightly via @Scheduled or cron job
DELETE FROM refresh_tokens
 WHERE expires_at < NOW()                    -- expired
    OR revoked_at < DATE_SUB(NOW(), INTERVAL 7 DAY); -- revoked > 7 days ago
```

**Monitoring queries:**
```sql
-- Active sessions per user
SELECT u.email, COUNT(*) active_sessions
  FROM refresh_tokens rt
  JOIN users u ON u.id = rt.user_id
 WHERE rt.revoked_at IS NULL AND rt.expires_at > NOW()
 GROUP BY u.email
 ORDER BY active_sessions DESC;

-- Suspicious reuse events (high revoked_at rate per family)
SELECT family_id, COUNT(*) revocations
  FROM refresh_tokens
 WHERE revoked_at IS NOT NULL
 GROUP BY family_id
HAVING revocations > 3
 ORDER BY revocations DESC;
```
