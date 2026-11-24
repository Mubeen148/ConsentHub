-- =============================================================================
-- ConsentHub — Foundational Governance & Demo Seed Data
-- File: backend/src/main/resources/db/migration/V2__seed_demo_data.sql
-- Note: Startup DataSeeder verifies/refreshes BCrypt(12) user hashes and appends
-- rolling 14-day multi-region consent_history, experiment_assignments, and
-- ADR-010 deterministic SHA-256 hash-chained audit_logs entries.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Seeded Users (Verified & BCrypt(12)-enforced on startup by DataSeeder)
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, role, region, created_at, updated_at)
VALUES
    (
        '00000000-0000-4000-8000-000000000001',
        'admin@consenthub.io',
        '$2a$12$SEED_BOOTSTRAP_ADMIN_VERIFY_ON_STARTUP',
        'Elena Vance (Privacy Governance)',
        'ADMIN',
        'GLOBAL',
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '25 days'
    ),
    (
        '00000000-0000-4000-8000-000000000002',
        'user@consenthub.io',
        '$2a$12$SEED_BOOTSTRAP_USER_VERIFY_ON_STARTUP',
        'Marcus Lindqvist',
        'USER',
        'EU',
        NOW() - INTERVAL '21 days',
        NOW() - INTERVAL '2 days'
    ),
    (
        '00000000-0000-4000-8000-000000000003',
        'alex.rivera@example.com',
        '$2a$12$SEED_BOOTSTRAP_ALEX_VERIFY_ON_STARTUP',
        'Alex Rivera',
        'USER',
        'US_CA',
        NOW() - INTERVAL '18 days',
        NOW() - INTERVAL '3 days'
    )
ON CONFLICT (email) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. Seeded Consent Purposes (5 Granular Purposes Ordered 1..5)
-- -----------------------------------------------------------------------------
INSERT INTO consent_purposes (id, slug, name, description, legal_basis, is_essential, active, display_order, created_at, updated_at)
VALUES
    (
        '11111111-1111-4111-8111-111111111101',
        'essential',
        'Essential Security & Session',
        'Strictly necessary for authentication cookies, CSRF attack mitigation, regional consent storage, and core security integrity.',
        'STRICTLY_NECESSARY',
        TRUE,
        TRUE,
        1,
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '25 days'
    ),
    (
        '11111111-1111-4111-8111-111111111102',
        'analytics',
        'Product Analytics & Telemetry',
        'Measures page load latency, feature reliability, and aggregated interaction patterns without cross-site behavioral profiling.',
        'CONSENT',
        FALSE,
        TRUE,
        2,
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '25 days'
    ),
    (
        '11111111-1111-4111-8111-111111111103',
        'personalization',
        'Experience Personalization',
        'Remembers workspace layout preferences, saved regional filters, and tailored product recommendations across sessions.',
        'CONSENT',
        FALSE,
        TRUE,
        3,
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '25 days'
    ),
    (
        '11111111-1111-4111-8111-111111111104',
        'marketing',
        'Lifecycle Communications',
        'Delivers optional privacy digest updates, new feature announcements, and educational compliance guides.',
        'CONSENT',
        FALSE,
        TRUE,
        4,
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '25 days'
    ),
    (
        '11111111-1111-4111-8111-111111111105',
        'ads',
        'Cross-Context Targeted Ads',
        'Controls partner campaign attribution and cross-context advertising identifiers subject to GDPR Opt-In and CCPA Do-Not-Sell/Share.',
        'LEGITIMATE_INTEREST_OPT_OUT',
        FALSE,
        TRUE,
        5,
        NOW() - INTERVAL '25 days',
        NOW() - INTERVAL '25 days'
    )
ON CONFLICT (slug) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. Seeded Privacy Policy Versions (v1.0 Superseded, v2.0 Active)
-- -----------------------------------------------------------------------------
INSERT INTO policy_versions (id, version_number, version_label, title, summary, content_markdown, requires_reconsent, is_active, published_by, published_at, created_at)
VALUES
    (
        '22222222-2222-4222-8222-222222222201',
        1,
        'v1.0',
        'Foundational Privacy & Cookie Notice',
        'Initial transparency framework establishing granular purpose controls, zero raw IP persistence, and self-service JSON data export.',
        E'# ConsentHub Foundational Privacy Notice (v1.0)\n\n## 1. Data Minimization\nWe collect only account email, display name, and your chosen regional privacy regime. Client IP addresses are salted and SHA-256 hashed prior to ledger persistence.\n\n## 2. Lawful Basis & Choice\nStrictly necessary security controls remain active to protect your account, while optional analytics, personalization, marketing, and advertising purposes remain under your direct control.\n\n## 3. Your Privacy Rights\nYou may inspect your cryptographic consent ledger, export a machine-readable JSON archive, or request account erasure at any time.',
        TRUE,
        FALSE,
        '00000000-0000-4000-8000-000000000001',
        NOW() - INTERVAL '24 days',
        NOW() - INTERVAL '24 days'
    ),
    (
        '22222222-2222-4222-8222-222222222202',
        2,
        'v2.0',
        '2026 Global Privacy & AI Telemetry Policy',
        'Updated transparency around product telemetry, symmetric first-layer opt-out controls, cryptographic consent receipts, and regional retention bounds.',
        E'# ConsentHub Global Privacy & AI Telemetry Policy (v2.0)\n\n## 1. Symmetric Zero-Dark-Pattern Consent\nEvery consent prompt presents **Accept All** and **Reject Optional** with identical visual weight and single-click symmetry in compliance with GDPR Art. 7(3), EDPB Guidelines 03/2022, and CPRA 11 CCR §7004.\n\n## 2. Zero AI Training on Personal Data\nOptional product telemetry is used strictly for latency and reliability measurement and is never sold or used to train external foundation models.\n\n## 3. Cryptographic Proofs & Retention\nEvery consent transition records an append-only SHA-256 receipt (`proofHash`). Regional retention bounds are enforced at 365 days (`EU`/`GLOBAL`) and 730 days (`US_CA`).',
        TRUE,
        TRUE,
        '00000000-0000-4000-8000-000000000001',
        NOW() - INTERVAL '10 days',
        NOW() - INTERVAL '10 days'
    )
ON CONFLICT (version_number) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Seeded Regional Regulatory Rules (EU/GDPR, US_CA/CCPA, GLOBAL/DEFAULT)
-- -----------------------------------------------------------------------------
INSERT INTO region_rules (id, region_code, regime_name, consent_model, banner_style, show_reject_all, retention_days, updated_at)
VALUES
    (
        '33333333-3333-4333-8333-333333333301',
        'EU',
        'GDPR',
        'OPT_IN',
        'MODAL',
        TRUE,
        365,
        NOW() - INTERVAL '20 days'
    ),
    (
        '33333333-3333-4333-8333-333333333302',
        'US_CA',
        'CCPA',
        'OPT_OUT',
        'BANNER',
        TRUE,
        730,
        NOW() - INTERVAL '20 days'
    ),
    (
        '33333333-3333-4333-8333-333333333303',
        'GLOBAL',
        'DEFAULT',
        'NOTICE_ONLY',
        'COMPACT',
        TRUE,
        365,
        NOW() - INTERVAL '20 days'
    )
ON CONFLICT (region_code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Seeded Active A/B Experiment on CONSENT_BANNER
-- -----------------------------------------------------------------------------
INSERT INTO experiments (id, slug, name, description, target_screen, target_region, status, variants_json, created_by, created_at, updated_at)
VALUES
    (
        '44444444-4444-4444-8444-444444444444',
        'eu-banner-clarity-q4',
        'Banner Copy Clarity & Trust Experiment',
        'Evaluates whether plain-language data minimization copy improves informed consent engagement while preserving symmetric 1-click Reject Optional parity.',
        'CONSENT_BANNER',
        NULL,
        'RUNNING',
        '[
          {
            "key": "control",
            "name": "Control — Neutral Transparency",
            "weight": 50,
            "headline": "Your Privacy Choices",
            "body": "We use strictly necessary cookies to operate ConsentHub securely, and optional analytics or personalization only according to your explicit preferences.",
            "primaryCtaLabel": "Accept All",
            "secondaryCtaLabel": "Reject Optional",
            "layoutTone": "STANDARD"
          },
          {
            "key": "concise_trust",
            "name": "Variant B — Plain-Language Data Minimization",
            "weight": 50,
            "headline": "Transparent Data Control, Zero Dark Patterns",
            "body": "Essential security stays on to protect your session. Optional telemetry and personalization are 100% up to you and revocable in one click.",
            "primaryCtaLabel": "Accept All Purposes",
            "secondaryCtaLabel": "Essential Only",
            "layoutTone": "EDITORIAL"
          }
        ]'::jsonb,
        '00000000-0000-4000-8000-000000000001',
        NOW() - INTERVAL '14 days',
        NOW() - INTERVAL '14 days'
    )
ON CONFLICT (slug) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. Seeded Sample DSAR Tickets (COMPLETED Export, PENDING Export, IN_REVIEW Delete)
-- -----------------------------------------------------------------------------
INSERT INTO data_requests (id, user_id, request_type, status, reason, admin_notes, processed_by, export_payload_json, requested_at, updated_at, completed_at)
VALUES
    (
        '55555555-5555-4555-8555-555555555501',
        '00000000-0000-4000-8000-000000000002',
        'EXPORT',
        'COMPLETED',
        'Annual personal GDPR Art. 20 portability archive review.',
        'Self-service machine-readable JSON portability archive generated and verified.',
        '00000000-0000-4000-8000-000000000001',
        '{"schemaVersion":"1.0","platform":"ConsentHub","regulatoryBasis":["GDPR_ARTICLE_15_20","CCPA_RIGHT_TO_KNOW"]}'::jsonb,
        NOW() - INTERVAL '6 days',
        NOW() - INTERVAL '6 days',
        NOW() - INTERVAL '6 days'
    ),
    (
        '55555555-5555-4555-8555-555555555502',
        '00000000-0000-4000-8000-000000000002',
        'EXPORT',
        'PENDING',
        'Requesting updated consent ledger receipt snapshot following v2.0 policy update.',
        NULL,
        NULL,
        NULL,
        NOW() - INTERVAL '1 day',
        NOW() - INTERVAL '1 day',
        NULL
    ),
    (
        '55555555-5555-4555-8555-555555555503',
        '00000000-0000-4000-8000-000000000003',
        'DELETE',
        'IN_REVIEW',
        'Closing test workspace account under CCPA Right to Delete (§1798.105).',
        'Identity verified; queued for PII pseudonymization and optional consent revocation.',
        '00000000-0000-4000-8000-000000000001',
        NULL,
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '12 hours',
        NULL
    )
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Seeded Demo Public SDK API Key (`ch_live_demo_public_sdk_key_2026`)
-- SHA-256('ch_live_demo_public_sdk_key_2026') = 37f0f4a4131b49bae8a6ffcdfbc8c585b7972459cfeb3d5d8a65d37b407abb1d
-- -----------------------------------------------------------------------------
INSERT INTO api_keys (id, name, key_prefix, key_hash, allowed_origins, scopes, active, created_by, last_used_at, created_at)
VALUES
    (
        '66666666-6666-4666-8666-666666666601',
        'ConsentHub Demo Storefront SDK Key',
        'ch_live_demo_',
        '37f0f4a4131b49bae8a6ffcdfbc8c585b7972459cfeb3d5d8a65d37b407abb1d',
        '*',
        'sdk:read,sdk:write',
        TRUE,
        '00000000-0000-4000-8000-000000000001',
        NOW() - INTERVAL '2 hours',
        NOW() - INTERVAL '20 days'
    )
ON CONFLICT (key_hash) DO NOTHING;
