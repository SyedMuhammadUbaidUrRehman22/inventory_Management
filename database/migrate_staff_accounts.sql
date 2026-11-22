-- =====================================================================
-- Upgrades an existing retail_pos database for staff accounts WITHOUT deleting data.
-- Only needed if your database was created from the Phase 1 schema.sql
-- (before the users.created_by and users.active columns existed).
-- Fresh installs: just run schema.sql.
--
-- Existing Cashier and Storekeeper accounts are given to the first Manager.
-- Run it once; it fails harmlessly ("Duplicate column name") if already applied.
-- =====================================================================
USE retail_pos;

ALTER TABLE users
    ADD COLUMN created_by INT     NULL                AFTER role,
    ADD COLUMN active     BOOLEAN NOT NULL DEFAULT TRUE AFTER created_by;

SET @first_manager_id = (SELECT MIN(user_id) FROM users WHERE role = 'MANAGER');
UPDATE users SET created_by = @first_manager_id WHERE role <> 'MANAGER';

ALTER TABLE users
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (user_id),
    ADD CONSTRAINT chk_users_owner CHECK ((role = 'MANAGER') = (created_by IS NULL));
