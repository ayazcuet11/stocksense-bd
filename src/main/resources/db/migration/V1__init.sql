CREATE TABLE tenants (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB;

CREATE TABLE branches (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT       NOT NULL,
    name      VARCHAR(255) NOT NULL,
    address   TEXT,
    city      VARCHAR(100),
    phone     VARCHAR(20),
    CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB;

CREATE TABLE app_users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM ('OWNER','MANAGER','STAFF') NOT NULL,
    branch_id     BIGINT,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_user_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB;

CREATE TABLE categories (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT       NOT NULL,
    name      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_category_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB;

CREATE TABLE products (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    sku         VARCHAR(100)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    category_id BIGINT,
    unit        VARCHAR(50)   NOT NULL,
    vat_rate    DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT uq_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT fk_product_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB;

CREATE TABLE branch_stocks (
    id                BIGINT    PRIMARY KEY AUTO_INCREMENT,
    branch_id         BIGINT    NOT NULL,
    product_id        BIGINT    NOT NULL,
    quantity          INT       NOT NULL DEFAULT 0,
    reorder_threshold INT       NOT NULL DEFAULT 10,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT fk_stock_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;

CREATE TABLE suppliers (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id      BIGINT       NOT NULL,
    name           VARCHAR(255) NOT NULL,
    phone          VARCHAR(20),
    lead_time_days INT          NOT NULL DEFAULT 3,
    CONSTRAINT fk_supplier_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB;

CREATE TABLE product_suppliers (
    product_id  BIGINT        NOT NULL,
    supplier_id BIGINT        NOT NULL,
    unit_price  DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (product_id, supplier_id),
    CONSTRAINT fk_ps_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_ps_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
) ENGINE = InnoDB;

CREATE TABLE sales (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    branch_id    BIGINT        NOT NULL,
    sold_at      TIMESTAMP     NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    vat_amount   DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_sale_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
) ENGINE = InnoDB;

CREATE TABLE sale_lines (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    sale_id    BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_saleline_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_saleline_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;

CREATE TABLE purchase_orders (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id        BIGINT    NOT NULL,
    branch_id        BIGINT    NOT NULL,
    supplier_id      BIGINT    NOT NULL,
    status           ENUM ('DRAFT','PENDING_APPROVAL','APPROVED','SENT','RECEIVED') NOT NULL DEFAULT 'DRAFT',
    created_by_agent TINYINT(1) NOT NULL DEFAULT 0,
    approved_by      BIGINT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_po_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_po_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_po_approver FOREIGN KEY (approved_by) REFERENCES app_users (id)
) ENGINE = InnoDB;

CREATE TABLE purchase_order_lines (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    po_id      BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_pol_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_pol_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB;

CREATE TABLE festival_events (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(255) NOT NULL,
    type           VARCHAR(100) NOT NULL,
    gregorian_date DATE         NOT NULL,
    hijri_date     VARCHAR(20)
) ENGINE = InnoDB;

CREATE TABLE agent_decisions (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_type     VARCHAR(100) NOT NULL,
    input_summary  TEXT,
    output_summary TEXT,
    reasoning      TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved       TINYINT(1)
) ENGINE = InnoDB;
