-- =============================================================================
-- ConsentHub — Frozen Database Schema Contract (PostgreSQL 16)
-- File: docs/contracts/database-schema.sql
-- Gate 1 Status: FROZEN (Authoritative source for Flyway V1__init_schema.sql)
-- =============================================================================
-- Conventions:
--   1. All primary keys are UUIDs (PostgreSQL 16 built-in gen_random_uuid()).
--   2. All timestamps use TIMESTAMPTZ stored in UTC (truncated to milliseconds
--      in application layer when included in SHA-256 tamper-evident hashes).
--   3. `consent_history` and `audit_logs` are strictly APPEND-ONLY ledgers
--      enforced by PostgreSQL BEFORE UPDATE OR DELETE triggers.
--   4. Canonical region codes across SQL, GraphQL, REST, and SDUI:
--      'EU' (GDPR), 'US_CA' (CCPA), 'GLOBAL' (DEFAULT).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Trigger Helpers (Updated-At & Strict Append-Only Ledger Enforcement)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION prevent_append_only_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Table "%" is strictly append-only; % operations are prohibited for regulatory compliance.',
        TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

-- -----------------------------------------------------------------------------
-- 1. users — Authenticated End-Users & Governance Admins
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER'
                    CHECK (role IN ('USER', 'ADMIN')),
    region          VARCHAR(20)  NOT NULL DEFAULT 'GLOBAL'
                    CHECK (region IN ('EU', 'US_CA', 'GLOBAL')),
    deleted_at      TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_region ON users (region);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

-- -----------------------------------------------------------------------------
-- 2. consent_purposes — Granular Data Processing Purposes
-- -----------------------------------------------------------------------------
-- Seeded slugs: 'essential', 'analytics', 'personalization', 'marketing', 'ads'
CREATE TABLE consent_purposes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            VARCHAR(64)  UNIQUE NOT NULL
                    CHECK (slug ~ '^[a-z][a-z0-9_]{1,63}$'),
    name            VARCHAR(120) NOT NULL,
    description     TEXT         NOT NULL,
    legal_basis     VARCHAR(64)  NOT NULL
                    CHECK (legal_basis IN (
                        'STRICTLY_NECESSARY',
                        'CONSENT',
                        'LEGITIMATE_INTEREST_OPT_OUT'
                    )),
    is_essential    BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_essential_legal_basis
        CHECK (NOT is_essential OR legal_basis = 'STRICTLY_NECESSARY')
);

CREATE INDEX idx_consent_purposes_active_order
    ON consent_purposes (active, display_order ASC);

CREATE TRIGGER trg_consent_purposes_updated_at
    BEFORE UPDATE ON consent_purposes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

-- -----------------------------------------------------------------------------
-- 3. policy_versions — Versioned Privacy Policies & Re-Consent Triggers
-- -----------------------------------------------------------------------------
CREATE TABLE policy_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_number      INT          UNIQUE NOT NULL CHECK (version_number >= 1),
    version_label       VARCHAR(32)  UNIQUE NOT NULL,
    title               VARCHAR(200) NOT NULL,
    summary             TEXT         NOT NULL,
    content_markdown    TEXT         NOT NULL,
    requires_reconsent  BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active           BOOLEAN      NOT NULL DEFAULT FALSE,
    published_by        UUID         NULL REFERENCES users(id),
    published_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Enforce at most ONE active policy version globally at the database level
CREATE UNIQUE INDEX uq_policy_versions_single_active
    ON policy_versions (is_active)
    WHERE is_active = TRUE;

CREATE INDEX idx_policy_versions_number_desc
    ON policy_versions (version_number DESC);

-- -----------------------------------------------------------------------------
-- 4. region_rules — Per-Jurisdiction Consent Models & Banner Configurations
-- -----------------------------------------------------------------------------
CREATE TABLE region_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_code     VARCHAR(20) UNIQUE NOT NULL
                    CHECK (region_code IN ('EU', 'US_CA', 'GLOBAL')),
    regime_name     VARCHAR(64) NOT NULL
                    CHECK (regime_name IN ('GDPR', 'CCPA', 'DEFAULT')),
    consent_model   VARCHAR(24) NOT NULL
                    CHECK (consent_model IN ('OPT_IN', 'OPT_OUT', 'NOTICE_ONLY')),
    banner_style    VARCHAR(24) NOT NULL
                    CHECK (banner_style IN ('MODAL', 'BANNER', 'COMPACT')),
    show_reject_all BOOLEAN     NOT NULL DEFAULT TRUE,
    retention_days  INT         NOT NULL DEFAULT 365
                    CHECK (retention_days BETWEEN 30 AND 3650),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_region_rules_updated_at
    BEFORE UPDATE ON region_rules
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

-- -----------------------------------------------------------------------------
-- 5. experiments — Sticky A/B Experiments on Server-Driven UI Screens
-- -----------------------------------------------------------------------------
-- variants_json schema: JSON array of objects summing weight = 100:
--   [{
--     "key": "control",
--     "name": "Control — Neutral Transparency",
--     "weight": 50,
--     "headline": "...",
--     "body": "...",
--     "primaryCtaLabel": "Accept All",
--     "secondaryCtaLabel": "Reject Optional",
--     "layoutTone": "STANDARD"
--   }]
CREATE TABLE experiments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            VARCHAR(80)  UNIQUE NOT NULL
                    CHECK (slug ~ '^[a-z0-9][a-z0-9_-]{2,79}$'),
    name            VARCHAR(160) NOT NULL,
    description     TEXT         NOT NULL,
    target_screen   VARCHAR(64)  NOT NULL
                    CHECK (target_screen IN (
                        'CONSENT_BANNER',
                        'PREFERENCE_CENTER',
                        'PRIVACY_OVERVIEW'
                    )),
    target_region   VARCHAR(20)  NULL
                    CHECK (target_region IS NULL OR target_region IN ('EU', 'US_CA', 'GLOBAL')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'RUNNING', 'PAUSED', 'CONCLUDED')),
    variants_json   JSONB        NOT NULL
                    CHECK (jsonb_typeof(variants_json) = 'array' AND jsonb_array_length(variants_json) >= 2),
    created_by      UUID         NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_experiments_lookup
    ON experiments (status, target_screen, target_region);

CREATE TRIGGER trg_experiments_updated_at
    BEFORE UPDATE ON experiments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

-- -----------------------------------------------------------------------------
-- 6. consent_records — Current Effective Consent State per Subject & Purpose
-- -----------------------------------------------------------------------------
-- subject_key convention:
--   Authenticated user : 'user:<user_uuid>'
--   Anonymous visitor  : 'anon:<client_id>'
CREATE TABLE consent_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NULL REFERENCES users(id),
    subject_key         VARCHAR(128) NOT NULL
                        CHECK (subject_key ~ '^(user:[0-9a-fA-F-]{36}|anon:[a-zA-Z0-9_-]{8,112})$'),
    purpose_id          UUID         NOT NULL REFERENCES consent_purposes(id),
    granted             BOOLEAN      NOT NULL,
    policy_version_id   UUID         NOT NULL REFERENCES policy_versions(id),
    region_code         VARCHAR(20)  NOT NULL
                        CHECK (region_code IN ('EU', 'US_CA', 'GLOBAL')),
    source              VARCHAR(32)  NOT NULL
                        CHECK (source IN (
                            'BANNER',
                            'PREFERENCE_CENTER',
                            'SDK',
                            'ADMIN_SEED',
                            'SYSTEM_ERASURE'
                        )),
    experiment_id       UUID         NULL REFERENCES experiments(id),
    variant_key         VARCHAR(64)  NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consent_records_subject_purpose UNIQUE (subject_key, purpose_id)
);

CREATE INDEX idx_consent_records_user_id ON consent_records (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_consent_records_subject_key ON consent_records (subject_key);
CREATE INDEX idx_consent_records_region_purpose ON consent_records (region_code, purpose_id, granted);

-- -----------------------------------------------------------------------------
-- 7. consent_history — Strictly APPEND-ONLY Immutable Consent Proof Ledger
-- -----------------------------------------------------------------------------
-- Stores regulatory proof under GDPR Art. 7(1) & CCPA opt-out verification.
-- `proof_hash` is SHA-256 hex of:
--   subject_key || '|' || purpose_slug || '|' || new_granted || '|' ||
--   policy_version_label || '|' || region_code || '|' || source || '|' ||
--   extract(epoch from occurred_at)*1000
CREATE TABLE consent_history (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         NULL REFERENCES users(id),
    subject_key             VARCHAR(128) NOT NULL,
    purpose_slug            VARCHAR(64)  NOT NULL,
    purpose_name            VARCHAR(120) NOT NULL,
    previous_granted        BOOLEAN      NULL,
    new_granted             BOOLEAN      NOT NULL,
    policy_version_label    VARCHAR(32)  NOT NULL,
    region_code             VARCHAR(20)  NOT NULL
                            CHECK (region_code IN ('EU', 'US_CA', 'GLOBAL')),
    source                  VARCHAR(32)  NOT NULL
                            CHECK (source IN (
                                'BANNER',
                                'PREFERENCE_CENTER',
                                'SDK',
                                'ADMIN_SEED',
                                'SYSTEM_ERASURE'
                            )),
    ip_hash                 VARCHAR(64)  NULL,
    user_agent_summary      VARCHAR(120) NULL,
    experiment_variant      VARCHAR(64)  NULL,
    proof_hash              VARCHAR(64)  NOT NULL,
    occurred_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consent_history_subject_time
    ON consent_history (subject_key, occurred_at DESC);
CREATE INDEX idx_consent_history_user_time
    ON consent_history (user_id, occurred_at DESC)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_consent_history_occurred_at
    ON consent_history (occurred_at DESC);
CREATE INDEX idx_consent_history_region_purpose
    ON consent_history (region_code, purpose_slug, new_granted);

CREATE TRIGGER trg_consent_history_append_only
    BEFORE UPDATE OR DELETE ON consent_history
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

-- -----------------------------------------------------------------------------
-- 8. experiment_assignments — Sticky Bucketing & Conversion Attribution
-- -----------------------------------------------------------------------------
-- Deterministic bucket: SHA-256(experiment_id || ':' || subject_key) mod 100.
-- `converted` = TRUE when the assigned subject subsequently grants at least
-- one non-essential (`is_essential = FALSE`) consent purpose.
CREATE TABLE experiment_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id   UUID         NOT NULL REFERENCES experiments(id),
    subject_key     VARCHAR(128) NOT NULL,
    user_id         UUID         NULL REFERENCES users(id),
    variant_key     VARCHAR(64)  NOT NULL,
    converted       BOOLEAN      NOT NULL DEFAULT FALSE,
    converted_at    TIMESTAMPTZ  NULL,
    assigned_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_experiment_subject UNIQUE (experiment_id, subject_key)
);

CREATE INDEX idx_experiment_assignments_metrics
    ON experiment_assignments (experiment_id, variant_key, converted);
CREATE INDEX idx_experiment_assignments_user
    ON experiment_assignments (user_id)
    WHERE user_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 9. data_requests — DSAR Fulfillment Queue (Export & Erasure Lifecycle)
-- -----------------------------------------------------------------------------
CREATE TABLE data_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id),
    request_type        VARCHAR(20) NOT NULL
                        CHECK (request_type IN ('EXPORT', 'DELETE')),
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN (
                            'PENDING',
                            'IN_REVIEW',
                            'APPROVED',
                            'COMPLETED',
                            'REJECTED'
                        )),
    reason              TEXT        NULL,
    admin_notes         TEXT        NULL,
    processed_by        UUID        NULL REFERENCES users(id),
    export_payload_json JSONB       NULL,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ NULL
);

CREATE INDEX idx_data_requests_user_time
    ON data_requests (user_id, requested_at DESC);
CREATE INDEX idx_data_requests_status_time
    ON data_requests (status, requested_at ASC);

CREATE TRIGGER trg_data_requests_updated_at
    BEFORE UPDATE ON data_requests
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

-- -----------------------------------------------------------------------------
-- 10. api_keys — Third-Party Embeddable SDK (`consent.js`) & Public REST Keys
-- -----------------------------------------------------------------------------
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    key_prefix      VARCHAR(24)  NOT NULL,
    key_hash        VARCHAR(64)  UNIQUE NOT NULL,
    allowed_origins TEXT         NOT NULL DEFAULT '*',
    scopes          VARCHAR(120) NOT NULL DEFAULT 'sdk:read,sdk:write',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      UUID         NULL REFERENCES users(id),
    last_used_at    TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_active ON api_keys (active, created_at DESC);

-- -----------------------------------------------------------------------------
-- 11. audit_logs — Strictly APPEND-ONLY Tamper-Evident SHA-256 Hash Chain
-- -----------------------------------------------------------------------------
-- Hash Chain Specification (enforced identically on write and verification):
--   1. Genesis `prev_hash` (first row) =
--      '0000000000000000000000000000000000000000000000000000000000000000'
--   2. `created_at` MUST be truncated to milliseconds (`ChronoUnit.MILLIS`)
--      prior to hashing and insert so Java `Instant` <-> Postgres `TIMESTAMPTZ`
--      round-tripping is bit-for-bit deterministic.
--   3. `details_json` MUST be serialized with alphabetically sorted keys and
--      compact formatting when computing `entry_hash` on both write and read.
--   4. Canonical pre-image string:
--      prev_hash || '|' || id || '|' || COALESCE(actor_id::text, 'ANONYMOUS')
--      || '|' || actor_email || '|' || actor_role || '|' || action || '|'
--      || entity_type || '|' || entity_id || '|' || epoch_millis(created_at)
--      || '|' || canonical_sorted_json(details_json)
--   5. `entry_hash` = lowercase hex SHA-256(UTF-8 bytes of canonical pre-image).
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_num    BIGSERIAL    UNIQUE NOT NULL,
    actor_id        UUID         NULL,
    actor_email     VARCHAR(255) NOT NULL,
    actor_role      VARCHAR(32)  NOT NULL,
    action          VARCHAR(80)  NOT NULL,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       VARCHAR(128) NOT NULL,
    details_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    prev_hash       VARCHAR(64)  NOT NULL,
    entry_hash      VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_sequence_desc ON audit_logs (sequence_num DESC);
CREATE INDEX idx_audit_logs_action_seq ON audit_logs (action, sequence_num DESC);

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_mutation();

-- =============================================================================
-- REQUIRED SEED DATA SPECIFICATION (Implemented in Flyway V2 / Seed Initializer)
-- =============================================================================
-- 1. Seeded Users (BCrypt strength = 12):
--    - Admin:
--        id        = '00000000-0000-4000-8000-000000000001'
--        email     = 'admin@consenthub.io'
--        password  = 'AdminPass123!'
--        full_name = 'Elena Vance (Privacy Governance)'
--        role      = 'ADMIN'
--        region    = 'EU'
--    - Regular End-User:
--        id        = '00000000-0000-4000-8000-000000000002'
--        email     = 'user@consenthub.io'
--        password  = 'UserPass123!'
--        full_name = 'Marcus Lindqvist'
--        role      = 'USER'
--        region    = 'EU'
--
-- 2. Seeded Consent Purposes (5 rows, ordered 1..5):
--    1. slug='essential',       name='Essential Security & Session',    legal_basis='STRICTLY_NECESSARY',          is_essential=TRUE,  display_order=1
--    2. slug='analytics',       name='Product Analytics & Telemetry',   legal_basis='CONSENT',                     is_essential=FALSE, display_order=2
--    3. slug='personalization', name='Experience Personalization',      legal_basis='CONSENT',                     is_essential=FALSE, display_order=3
--    4. slug='marketing',       name='Lifecycle Communications',        legal_basis='CONSENT',                     is_essential=FALSE, display_order=4
--    5. slug='ads',             name='Cross-Context Targeted Ads',      legal_basis='LEGITIMATE_INTEREST_OPT_OUT', is_essential=FALSE, display_order=5
--
-- 3. Seeded Policy Versions (2 rows):
--    - version_number=1, version_label='v1.0', title='Foundational Privacy & Cookie Notice', requires_reconsent=TRUE, is_active=FALSE
--    - version_number=2, version_label='v2.0', title='2026 Global Privacy & AI Telemetry Policy', requires_reconsent=TRUE, is_active=TRUE
--
-- 4. Seeded Region Rules (3 rows):
--    - region_code='EU',     regime_name='GDPR',    consent_model='OPT_IN',      banner_style='MODAL',   show_reject_all=TRUE, retention_days=365
--    - region_code='US_CA',  regime_name='CCPA',    consent_model='OPT_OUT',     banner_style='BANNER',  show_reject_all=TRUE, retention_days=730
--    - region_code='GLOBAL', regime_name='DEFAULT', consent_model='NOTICE_ONLY', banner_style='COMPACT', show_reject_all=TRUE, retention_days=365
--
-- 5. Seeded Active A/B Experiment (1 RUNNING experiment + realistic exposure/conversion rows):
--    - slug='eu-banner-clarity-q4', target_screen='CONSENT_BANNER', target_region=NULL, status='RUNNING'
--    - variants_json: 'control' (50% weight) vs 'concise_trust' (50% weight)
--    - ~40-80 seeded `experiment_assignments` rows across both variants so Admin
--      Experimentation Console displays realistic exposures, conversions, and lift immediately.
--
-- 6. Seeded Consent Records & Multi-Day Consent History:
--    - `user@consenthub.io` (`subject_key = 'user:00000000-0000-4000-8000-000000000002'`)
--      has `v1.0` -> `v2.0` consent history entries + active `v2.0` consent_records
--      (`essential=true, analytics=true, personalization=true, marketing=false, ads=false`).
--    - Additional realistic pseudonymous subjects (`anon:*`) spread across the past 14-30 days
--      and across `EU`, `US_CA`, and `GLOBAL` so Admin Analytics time-series & regional charts
--      render rich multi-series metrics out-of-the-box.
--
-- 7. Seeded Sample DSAR Tickets (`data_requests`):
--    - Completed EXPORT ticket for `user@consenthub.io` + Pending/In-Review sample tickets
--      (with additional seeded demo users if needed for PENDING/IN_REVIEW DELETE tickets)
--      so both End-User DSAR tracker and Admin DSAR Queue show realistic lifecycle states.
--
-- 8. Seeded Demo Public SDK API Key (`api_keys`):
--    - Raw Demo Key (documented in `.env.example`, `sdk-demo.html`, and `README.md`):
--        plainTextKey = 'ch_live_demo_public_sdk_key_2026'
--    - Stored columns:
--        name            = 'ConsentHub Demo Storefront SDK Key'
--        key_prefix      = 'ch_live_demo_'
--        key_hash        = '37f0f4a4131b49bae8a6ffcdfbc8c585b7972459cfeb3d5d8a65d37b407abb1d'
--        allowed_origins = '*'
--        scopes          = 'sdk:read,sdk:write'
--        active          = TRUE
-- =============================================================================
