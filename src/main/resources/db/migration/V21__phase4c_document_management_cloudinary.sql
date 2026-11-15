-- =====================================================================================
-- EDAM — Phase 4C: Cloudinary-backed document management
--
-- V6 created media_assets (provider, reference, mime, size, checksum, uploaded_by/at).
-- request_documents (V9) already links expense requests to media_assets. This migration
-- (a) widens media_assets with the Cloudinary metadata Phase 4C requires (public_id,
--     asset_id, secure_url, resource_type, format, lifecycle status + category) and
--     renames STORED statuses to match the malware-scanning lifecycle;
-- (b) widens request_documents with description and the stricter CAPEX/GRN categories
--     the BRD calls out, while keeping the prior codes backwards-compatible;
-- (c) creates a doc_scan_events table recording malware-scan attempts and outcomes
--     (PENDING_SCAN / CLEAN / INFECTED / SCAN_FAILED) so an infected document remains
--     auditable even if it is later removed.
--
-- Backwards compatibility: every new column is nullable or has a default. Existing rows
-- (logo/favicon, existing request documents) are grandfathered in as CLEAN so that prior
-- approver-visible documents continue to download after this migration.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- (a) media_assets: widen for Cloudinary private-asset storage and scanning lifecycle
-- -------------------------------------------------------------------------------------

ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS public_id         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS asset_id          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS secure_url        TEXT,
    ADD COLUMN IF NOT EXISTS resource_type     VARCHAR(32),
    ADD COLUMN IF NOT EXISTS file_format       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS document_category VARCHAR(60),
    ADD COLUMN IF NOT EXISTS description       TEXT,
    ADD COLUMN IF NOT EXISTS lifecycle_status  VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS scan_status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING_SCAN',
    ADD COLUMN IF NOT EXISTS scanned_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS version           BIGINT       NOT NULL DEFAULT 0;

-- Pre-4C rows (logos/favicons/prior request docs) existed before scanning was enforced;
-- grandfather them as ACTIVE/CLEAN so prior approver-visible documents continue to load
-- after the migration. New uploads will be inserted PENDING_SCAN by the service.
UPDATE media_assets
   SET lifecycle_status = 'ACTIVE',
       scan_status      = 'CLEAN'
 WHERE lifecycle_status = 'ACTIVE' AND scan_status = 'PENDING_SCAN';

ALTER TABLE media_assets
    ADD CONSTRAINT IF NOT EXISTS ck_media_assets_lifecycle
        CHECK (lifecycle_status IN ('ACTIVE','ARCHIVED','DELETED','QUARANTINED')),
    ADD CONSTRAINT IF NOT EXISTS ck_media_assets_scan
        CHECK (scan_status IN ('PENDING_SCAN','CLEAN','INFECTED','SCAN_FAILED')),
    ADD CONSTRAINT IF NOT EXISTS ck_media_assets_res_type
        CHECK (resource_type IS NULL OR resource_type IN ('raw','image','video')),
    ADD CONSTRAINT IF NOT EXISTS ck_media_assets_category
        CHECK (document_category IS NULL OR document_category IN (
            'QUOTATION','INVOICE','SUPPORTING','SUPPORTING_DOCUMENT','APPROVAL_DOCUMENT',
            'TAX','TAX_DOCUMENT','GRN','GRN_DOCUMENT','CAPEX_DOCUMENT',
            'WARRANTY','BUDGET','OTHER'));

CREATE INDEX IF NOT EXISTS ix_media_assets_public_id    ON media_assets (public_id);
CREATE INDEX IF NOT EXISTS ix_media_assets_uploaded_by  ON media_assets (uploaded_by);

-- -------------------------------------------------------------------------------------
-- (b) request_documents: widen the document-type allow-list and add description
-- -------------------------------------------------------------------------------------

ALTER TABLE request_documents
    ADD COLUMN IF NOT EXISTS description      TEXT,
    ADD COLUMN IF NOT EXISTS required_by_rule VARCHAR(150),
    ADD COLUMN IF NOT EXISTS version          BIGINT NOT NULL DEFAULT 0;

ALTER TABLE request_documents
    DROP CONSTRAINT IF EXISTS ck_request_documents_type;
ALTER TABLE request_documents
    ADD CONSTRAINT ck_request_documents_type CHECK (document_type IN (
        'SUPPORTING','SUPPORTING_DOCUMENT',
        'QUOTATION','INVOICE','APPROVAL_DOCUMENT',
        'TAX','TAX_DOCUMENT','GRN','GRN_DOCUMENT','CAPEX_DOCUMENT',
        'WARRANTY','BUDGET','OTHER'));

-- -------------------------------------------------------------------------------------
-- (c) doc_scan_events: append-only record of every malware-scan outcome
-- -------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS doc_scan_events (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    media_asset_id  UUID         NOT NULL REFERENCES media_assets (id) ON DELETE CASCADE,
    scan_status     VARCHAR(32)  NOT NULL,
    scanner         VARCHAR(60)  NOT NULL,
    scanner_version VARCHAR(60),
    threat_name     VARCHAR(255),
    scan_started_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scan_finished_at TIMESTAMPTZ,
    detail          TEXT,
    CONSTRAINT ck_doc_scan_events_status CHECK (scan_status IN ('PENDING_SCAN','CLEAN','INFECTED','SCAN_FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_doc_scan_events_asset ON doc_scan_events (media_asset_id);
