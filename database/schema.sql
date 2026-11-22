-- =====================================================================
-- Retail Store Sales & Inventory Management System - MySQL schema
-- Requires MySQL 8.0.16+ (CHECK constraints are enforced).
--
-- WARNING: running this script DROPS and recreates all tables in
-- `retail_pos`, deleting any existing data.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS retail_pos
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE retail_pos;

-- Drop in reverse dependency order so foreign keys don't block it.
DROP TABLE IF EXISTS report;
DROP TABLE IF EXISTS stock_movement;
DROP TABLE IF EXISTS stock_adjustment;
DROP TABLE IF EXISTS sale_return;
DROP TABLE IF EXISTS sales_receipt;
DROP TABLE IF EXISTS sale_item;
DROP TABLE IF EXISTS sale;
DROP TABLE IF EXISTS stock_receipt;
DROP TABLE IF EXISTS purchase_order_item;
DROP TABLE IF EXISTS purchase_order;
DROP TABLE IF EXISTS product_supplier;
DROP TABLE IF EXISTS supplier;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS users;

-- ---------------------------------------------------------------------
-- Users: one table for all roles (User is abstract; role = subclass).
-- Named `users` because USER is a MySQL keyword.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id       INT          NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(60)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_role CHECK (role IN ('MANAGER', 'CASHIER', 'STOREKEEPER'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------
CREATE TABLE category (
    category_id INT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    PRIMARY KEY (category_id),
    CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE product (
    product_code           VARCHAR(30)    NOT NULL,
    name                   VARCHAR(150)   NOT NULL,
    unit                   VARCHAR(20)    NOT NULL,
    selling_price          DECIMAL(12, 2) NOT NULL,
    reorder_level          INT            NOT NULL DEFAULT 0,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    current_stock_quantity INT            NOT NULL DEFAULT 0,
    category_id            INT            NOT NULL,
    PRIMARY KEY (product_code),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (category_id),
    CONSTRAINT chk_product_price CHECK (selling_price >= 0),
    CONSTRAINT chk_product_reorder CHECK (reorder_level >= 0),
    CONSTRAINT chk_product_stock CHECK (current_stock_quantity >= 0)
) ENGINE = InnoDB;

CREATE TABLE supplier (
    supplier_id  INT          NOT NULL AUTO_INCREMENT,
    name         VARCHAR(150) NOT NULL,
    address      VARCHAR(255),
    contact_info VARCHAR(100),
    PRIMARY KEY (supplier_id)
) ENGINE = InnoDB;

-- Many-to-many: which supplier sells which product, at what price.
CREATE TABLE product_supplier (
    product_code   VARCHAR(30)    NOT NULL,
    supplier_id    INT            NOT NULL,
    purchase_price DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (product_code, supplier_id),
    CONSTRAINT fk_ps_product  FOREIGN KEY (product_code) REFERENCES product (product_code),
    CONSTRAINT fk_ps_supplier FOREIGN KEY (supplier_id)  REFERENCES supplier (supplier_id),
    CONSTRAINT chk_ps_price CHECK (purchase_price >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Purchasing
-- ---------------------------------------------------------------------
CREATE TABLE purchase_order (
    po_number   INT            NOT NULL AUTO_INCREMENT,
    order_date  DATE           NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'PREPARED',
    total_value DECIMAL(12, 2) NOT NULL DEFAULT 0,
    supplier_id INT            NOT NULL,
    PRIMARY KEY (po_number),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (supplier_id),
    CONSTRAINT chk_po_status CHECK (status IN
        ('PREPARED', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT chk_po_total CHECK (total_value >= 0)
) ENGINE = InnoDB;

CREATE TABLE purchase_order_item (
    purchase_order_item_id INT            NOT NULL AUTO_INCREMENT,
    po_number              INT            NOT NULL,
    product_code           VARCHAR(30)    NOT NULL,
    ordered_quantity       INT            NOT NULL,
    purchase_price         DECIMAL(12, 2) NOT NULL,
    received_quantity      INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (purchase_order_item_id),
    CONSTRAINT uq_poi_order_product UNIQUE (po_number, product_code),
    CONSTRAINT fk_poi_order   FOREIGN KEY (po_number)    REFERENCES purchase_order (po_number)
        ON DELETE CASCADE,
    CONSTRAINT fk_poi_product FOREIGN KEY (product_code) REFERENCES product (product_code),
    CONSTRAINT chk_poi_ordered  CHECK (ordered_quantity > 0),
    CONSTRAINT chk_poi_price    CHECK (purchase_price >= 0),
    -- Over-receipt is rejected at the database level too.
    CONSTRAINT chk_poi_received CHECK (received_quantity BETWEEN 0 AND ordered_quantity)
) ENGINE = InnoDB;

CREATE TABLE stock_receipt (
    receipt_id             INT  NOT NULL AUTO_INCREMENT,
    receipt_date           DATE NOT NULL,
    quantity_received      INT  NOT NULL,
    purchase_order_item_id INT  NOT NULL,
    PRIMARY KEY (receipt_id),
    CONSTRAINT fk_receipt_poi FOREIGN KEY (purchase_order_item_id)
        REFERENCES purchase_order_item (purchase_order_item_id),
    CONSTRAINT chk_receipt_qty CHECK (quantity_received > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Sales
-- ---------------------------------------------------------------------
CREATE TABLE sale (
    sale_id      INT            NOT NULL AUTO_INCREMENT,
    date_time    DATETIME       NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    subtotal     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    final_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    cashier_id   INT            NOT NULL,
    PRIMARY KEY (sale_id),
    CONSTRAINT fk_sale_cashier FOREIGN KEY (cashier_id) REFERENCES users (user_id),
    CONSTRAINT chk_sale_status   CHECK (status IN ('DRAFT', 'COMPLETED')),
    CONSTRAINT chk_sale_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_sale_discount CHECK (discount >= 0),
    CONSTRAINT chk_sale_final    CHECK (final_amount >= 0)
) ENGINE = InnoDB;

CREATE TABLE sale_item (
    sale_item_id  INT            NOT NULL AUTO_INCREMENT,
    sale_id       INT            NOT NULL,
    product_code  VARCHAR(30)    NOT NULL,
    quantity      INT            NOT NULL,
    selling_price DECIMAL(12, 2) NOT NULL,
    line_total    DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (sale_item_id),
    CONSTRAINT uq_sale_item_product UNIQUE (sale_id, product_code),
    CONSTRAINT fk_sale_item_sale    FOREIGN KEY (sale_id)      REFERENCES sale (sale_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_code) REFERENCES product (product_code),
    CONSTRAINT chk_sale_item_qty   CHECK (quantity > 0),
    CONSTRAINT chk_sale_item_price CHECK (selling_price >= 0),
    CONSTRAINT chk_sale_item_total CHECK (line_total >= 0)
) ENGINE = InnoDB;

-- 0..1 receipt per sale (enforced by the UNIQUE on sale_id).
CREATE TABLE sales_receipt (
    receipt_id INT      NOT NULL AUTO_INCREMENT,
    issued_at  DATETIME NOT NULL,
    sale_id    INT      NOT NULL,
    PRIMARY KEY (receipt_id),
    CONSTRAINT uq_sales_receipt_sale UNIQUE (sale_id),
    CONSTRAINT fk_sales_receipt_sale FOREIGN KEY (sale_id) REFERENCES sale (sale_id)
) ENGINE = InnoDB;

-- Model class `Return`; table named `sale_return` because RETURN is reserved in MySQL.
CREATE TABLE sale_return (
    return_id    INT          NOT NULL AUTO_INCREMENT,
    return_date  DATE         NOT NULL,
    quantity     INT          NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    is_accepted  BOOLEAN      NOT NULL,
    is_reusable  BOOLEAN      NOT NULL,
    sale_item_id INT          NOT NULL,
    PRIMARY KEY (return_id),
    CONSTRAINT fk_return_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_item (sale_item_id),
    CONSTRAINT chk_return_qty CHECK (quantity > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Stock control
-- ---------------------------------------------------------------------
-- Model field `date` is stored as `adjustment_date`.
CREATE TABLE stock_adjustment (
    adjustment_id   INT          NOT NULL AUTO_INCREMENT,
    adjustment_type VARCHAR(20)  NOT NULL,
    quantity        INT          NOT NULL,
    adjustment_date DATE         NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    product_code    VARCHAR(30)  NOT NULL,
    recorded_by     INT          NOT NULL,
    PRIMARY KEY (adjustment_id),
    CONSTRAINT fk_adjustment_product FOREIGN KEY (product_code) REFERENCES product (product_code),
    CONSTRAINT fk_adjustment_user    FOREIGN KEY (recorded_by)  REFERENCES users (user_id),
    CONSTRAINT chk_adjustment_type CHECK (adjustment_type IN ('DAMAGED', 'EXPIRED', 'MISSING')),
    CONSTRAINT chk_adjustment_qty  CHECK (quantity > 0)
) ENGINE = InnoDB;

CREATE TABLE stock_movement (
    movement_id         INT         NOT NULL AUTO_INCREMENT,
    movement_type       VARCHAR(20) NOT NULL,
    quantity_change     INT         NOT NULL,
    date_time           DATETIME    NOT NULL,
    source_reference    VARCHAR(50) NOT NULL,
    balance_after       INT         NOT NULL,
    product_code        VARCHAR(30) NOT NULL,
    responsible_user_id INT         NOT NULL,
    PRIMARY KEY (movement_id),
    INDEX idx_movement_product (product_code, date_time),
    INDEX idx_movement_source (source_reference),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_code)        REFERENCES product (product_code),
    CONSTRAINT fk_movement_user    FOREIGN KEY (responsible_user_id) REFERENCES users (user_id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('RECEIPT', 'SALE', 'RETURN', 'ADJUSTMENT')),
    CONSTRAINT chk_movement_change  CHECK (quantity_change <> 0),
    CONSTRAINT chk_movement_balance CHECK (balance_after >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Reports
-- ---------------------------------------------------------------------
CREATE TABLE report (
    report_id    INT         NOT NULL AUTO_INCREMENT,
    report_type  VARCHAR(50) NOT NULL,
    generated_at DATETIME    NOT NULL,
    PRIMARY KEY (report_id)
) ENGINE = InnoDB;

-- =====================================================================
-- Seed data
-- =====================================================================
INSERT INTO category (name) VALUES
    ('Groceries'),
    ('Beverages'),
    ('Household');

-- All three users have the password "1234" (BCrypt-hashed via PasswordUtil.hash).
INSERT INTO users (username, password_hash, role) VALUES
    ('manager',     '$2a$10$LPIjVbx3LJAqeUfX3UQLoeb6PpJxPORsQIGxIUZrYDf.jLFEWnl9q', 'MANAGER'),
    ('cashier',     '$2a$10$9NeTy.EumtcgVU.Xpho5XeMrBfxQPse8RPDWYNrLXNVGQ4Z5/Q8UK', 'CASHIER'),
    ('storekeeper', '$2a$10$tJP8iezJoD8/xFAA.0zk1enDOClELTnogeZ3togGgLb9.G9gPNV.K', 'STOREKEEPER');
