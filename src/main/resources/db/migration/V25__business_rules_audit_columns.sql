-- V10 created business_rules with created_at/updated_at/version but omitted created_by/
-- updated_by that AuditableEntity expects (the sets/versions tables already had them). Add
-- the two missing metadata columns so Hibernate schema validation passes for the JPA
-- entity introduced in Phase 4G. No new tables; no data migration.
ALTER TABLE business_rules
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);
