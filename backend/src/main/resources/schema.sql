-- ============================================================
-- Payment Processing System - Database Schema
-- ============================================================

CREATE TABLE IF NOT EXISTS accounts (
    account_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_holder_name VARCHAR(255) NOT NULL,
    currency_code       VARCHAR(3) NOT NULL,
    balance             DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    account_status      ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    is_bucket_account   BOOLEAN DEFAULT FALSE,
    max_daily_limit     DECIMAL(15, 2) NOT NULL DEFAULT 50000.00
);

CREATE TABLE IF NOT EXISTS crowdfunding_campaigns (
    campaign_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_name        VARCHAR(255) NOT NULL,
    bucket_account_id    BIGINT NOT NULL,
    target_amount        DECIMAL(15, 2) NOT NULL,
    target_currency      VARCHAR(3) NOT NULL,
    current_amount       DECIMAL(15, 2) DEFAULT 0.00,
    threshold_percentage INT DEFAULT 100,
    status               ENUM('ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    campaign_end_date    DATE,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bucket_account_id) REFERENCES accounts(account_id)
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_reference        VARCHAR(100) UNIQUE NOT NULL,
    source_account_id        BIGINT NOT NULL,
    destination_account_id   BIGINT NOT NULL,
    amount                   DECIMAL(15, 2) NOT NULL,
    currency_code            VARCHAR(3) NOT NULL,
    payment_type             ENUM('REGULAR', 'CROWDFUNDING') NOT NULL DEFAULT 'REGULAR',
    crowdfunding_campaign_id BIGINT,
    status                   ENUM('CREATED', 'VALIDATED', 'SENT', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'CREATED',
    error_code               VARCHAR(50),
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at             TIMESTAMP,
    FOREIGN KEY (source_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (destination_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (crowdfunding_campaign_id) REFERENCES crowdfunding_campaigns(campaign_id),
    INDEX idx_payments_status (status),
    INDEX idx_payments_created_at (created_at),
    INDEX idx_payments_source (source_account_id),
    INDEX idx_payments_type (payment_type)
);

CREATE TABLE IF NOT EXISTS payment_status_audit (
    audit_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id  BIGINT NOT NULL,
    from_status VARCHAR(50),
    to_status   VARCHAR(50) NOT NULL,
    changed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE CASCADE,
    INDEX idx_audit_payment_id (payment_id)
);

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_number      VARCHAR(50) UNIQUE NOT NULL,
    payment_id         BIGINT,
    account_id         BIGINT NOT NULL,
    title              VARCHAR(255) NOT NULL,
    description        TEXT NOT NULL,
    priority           ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    status             ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    resolution_summary TEXT,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at        TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE SET NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    INDEX idx_tickets_status (status),
    INDEX idx_tickets_payment_id (payment_id)
);
