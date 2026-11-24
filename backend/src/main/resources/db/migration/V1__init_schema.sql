-- =============================================================================
-- ConsentHub — Frozen Database Schema Contract (PostgreSQL 16)
-- File: backend/src/main/resources/db/migration/V1__init_schema.sql
-- Source: docs/contracts/database-schema.sql (FROZEN)
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
