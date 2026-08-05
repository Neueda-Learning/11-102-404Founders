-- ============================================================
-- Payment Processing System - Database Schema
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    user_id    BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(30),
    address VARCHAR(255),
    country VARCHAR(80),
    default_currency VARCHAR(3),
    daily_transaction_limit DECIMAL(15, 2) DEFAULT 5000.00
);

CREATE TABLE IF NOT EXISTS accounts (
    account_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id             BIGINT,
    account_holder_name VARCHAR(255) NOT NULL,
    account_number      VARCHAR(30) UNIQUE,
    bank_name           VARCHAR(120),
    bank_ifsc           VARCHAR(30),
    account_type        VARCHAR(80),
    currency_code       VARCHAR(3) NOT NULL,
    balance             DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    account_status      ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    is_bucket_account   BOOLEAN DEFAULT FALSE,
    max_daily_limit     DECIMAL(15, 2) NOT NULL DEFAULT 50000.00,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS crowdfunding_campaigns (
    campaign_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_name        VARCHAR(255) NOT NULL,
    description          TEXT,
    donation_category    VARCHAR(120),
    donation_options     TEXT,
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
    idempotency_key          VARCHAR(120) UNIQUE,
    source_account_id        BIGINT NOT NULL,
    destination_account_id   BIGINT NOT NULL,
    amount                   DECIMAL(15, 2) NOT NULL,
    converted_amount         DECIMAL(15, 2),
    forex_fee                DECIMAL(15, 2),
    currency_code            VARCHAR(3) NOT NULL,
    destination_currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_type             ENUM('NORMAL_PAYMENT', 'CROWDFUNDING_PAYMENT', 'REGULAR', 'CROWDFUNDING') NOT NULL DEFAULT 'NORMAL_PAYMENT',
    crowdfunding_campaign_id BIGINT,
    status                   ENUM('INITIATED', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'CREATED', 'VALIDATED', 'SENT', 'COMPLETED') NOT NULL DEFAULT 'INITIATED',
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
    status      VARCHAR(50),
    description TEXT,
    changed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE CASCADE,
    INDEX idx_audit_payment_id (payment_id)
);

CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_number      VARCHAR(50) UNIQUE NOT NULL,
    payment_id         BIGINT,
    account_id         BIGINT NOT NULL,
    user_id            BIGINT,
    title              VARCHAR(255) NOT NULL,
    description        TEXT NOT NULL,
    failure_reason     TEXT,
    ticket_type        ENUM('GENERAL', 'FAILED_PAYMENT', 'WRONG_RECIPIENT', 'DUPLICATE_PAYMENT', 'DAILY_LIMIT_EXCEEDED', 'INSUFFICIENT_FUNDS', 'CURRENCY_CONVERSION_ISSUE', 'OTHER', 'DISPUTE_SENDER', 'DISPUTE_RECEIVER') NOT NULL DEFAULT 'GENERAL',
    dispute_role       ENUM('NONE', 'SENDER', 'RECEIVER') NOT NULL DEFAULT 'NONE',
    recovery_requested BOOLEAN DEFAULT FALSE,
    priority           ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    status             ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    resolution_summary TEXT,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at        TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE SET NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_tickets_status (status),
    INDEX idx_tickets_payment_id (payment_id)
);

-- ============================================================
-- Backward-compatible migrations for existing databases
-- ============================================================

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'user_id'
        ),
        'SELECT 1',
        'ALTER TABLE accounts ADD COLUMN user_id BIGINT'
    )
);
PREPARE s1 FROM @stmt;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'account_number'
        ),
        'SELECT 1',
        'ALTER TABLE accounts ADD COLUMN account_number VARCHAR(30)'
    )
);
PREPARE s2 FROM @stmt;
EXECUTE s2;
DEALLOCATE PREPARE s2;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'bank_name'
        ),
        'SELECT 1',
        'ALTER TABLE accounts ADD COLUMN bank_name VARCHAR(120)'
    )
);
PREPARE s3 FROM @stmt;
EXECUTE s3;
DEALLOCATE PREPARE s3;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'bank_ifsc'
        ),
        'SELECT 1',
        'ALTER TABLE accounts ADD COLUMN bank_ifsc VARCHAR(30)'
    )
);
PREPARE s4 FROM @stmt;
EXECUTE s4;
DEALLOCATE PREPARE s4;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'crowdfunding_campaigns' AND column_name = 'description'
        ),
        'SELECT 1',
        'ALTER TABLE crowdfunding_campaigns ADD COLUMN description TEXT'
    )
);
PREPARE s5 FROM @stmt;
EXECUTE s5;
DEALLOCATE PREPARE s5;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'crowdfunding_campaigns' AND column_name = 'donation_category'
        ),
        'SELECT 1',
        'ALTER TABLE crowdfunding_campaigns ADD COLUMN donation_category VARCHAR(120)'
    )
);
PREPARE s6 FROM @stmt;
EXECUTE s6;
DEALLOCATE PREPARE s6;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'crowdfunding_campaigns' AND column_name = 'donation_options'
        ),
        'SELECT 1',
        'ALTER TABLE crowdfunding_campaigns ADD COLUMN donation_options TEXT'
    )
);
PREPARE s7 FROM @stmt;
EXECUTE s7;
DEALLOCATE PREPARE s7;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'payments' AND column_name = 'idempotency_key'
        ),
        'SELECT 1',
        'ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(120)'
    )
);
PREPARE s8 FROM @stmt;
EXECUTE s8;
DEALLOCATE PREPARE s8;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'payments' AND column_name = 'converted_amount'
        ),
        'SELECT 1',
        'ALTER TABLE payments ADD COLUMN converted_amount DECIMAL(15, 2)'
    )
);
PREPARE s9 FROM @stmt;
EXECUTE s9;
DEALLOCATE PREPARE s9;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'payments' AND column_name = 'forex_fee'
        ),
        'SELECT 1',
        'ALTER TABLE payments ADD COLUMN forex_fee DECIMAL(15, 2)'
    )
);
PREPARE s10 FROM @stmt;
EXECUTE s10;
DEALLOCATE PREPARE s10;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'payments' AND column_name = 'destination_currency_code'
        ),
        'SELECT 1',
        'ALTER TABLE payments ADD COLUMN destination_currency_code VARCHAR(3) NOT NULL DEFAULT ''INR'''
    )
);
PREPARE s11 FROM @stmt;
EXECUTE s11;
DEALLOCATE PREPARE s11;

ALTER TABLE payments
    MODIFY COLUMN payment_type ENUM('NORMAL_PAYMENT', 'CROWDFUNDING_PAYMENT', 'REGULAR', 'CROWDFUNDING') NOT NULL DEFAULT 'NORMAL_PAYMENT',
    MODIFY COLUMN status ENUM('INITIATED', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'CREATED', 'VALIDATED', 'SENT', 'COMPLETED') NOT NULL DEFAULT 'INITIATED';

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'payment_status_audit' AND column_name = 'status'
        ),
        'SELECT 1',
        'ALTER TABLE payment_status_audit ADD COLUMN status VARCHAR(50)'
    )
);
PREPARE s12 FROM @stmt;
EXECUTE s12;
DEALLOCATE PREPARE s12;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'payment_status_audit' AND column_name = 'description'
        ),
        'SELECT 1',
        'ALTER TABLE payment_status_audit ADD COLUMN description TEXT'
    )
);
PREPARE s13 FROM @stmt;
EXECUTE s13;
DEALLOCATE PREPARE s13;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'support_tickets' AND column_name = 'user_id'
        ),
        'SELECT 1',
        'ALTER TABLE support_tickets ADD COLUMN user_id BIGINT'
    )
);
PREPARE s14 FROM @stmt;
EXECUTE s14;
DEALLOCATE PREPARE s14;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'support_tickets' AND column_name = 'failure_reason'
        ),
        'SELECT 1',
        'ALTER TABLE support_tickets ADD COLUMN failure_reason TEXT'
    )
);
PREPARE s15 FROM @stmt;
EXECUTE s15;
DEALLOCATE PREPARE s15;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'support_tickets' AND column_name = 'ticket_type'
        ),
        'SELECT 1',
        'ALTER TABLE support_tickets ADD COLUMN ticket_type ENUM(''GENERAL'', ''FAILED_PAYMENT'', ''DISPUTE_SENDER'', ''DISPUTE_RECEIVER'') NOT NULL DEFAULT ''GENERAL'''
    )
);
PREPARE s16 FROM @stmt;
EXECUTE s16;
DEALLOCATE PREPARE s16;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'support_tickets' AND column_name = 'dispute_role'
        ),
        'SELECT 1',
        'ALTER TABLE support_tickets ADD COLUMN dispute_role ENUM(''NONE'', ''SENDER'', ''RECEIVER'') NOT NULL DEFAULT ''NONE'''
    )
);
PREPARE s17 FROM @stmt;
EXECUTE s17;
DEALLOCATE PREPARE s17;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'support_tickets' AND column_name = 'recovery_requested'
        ),
        'SELECT 1',
        'ALTER TABLE support_tickets ADD COLUMN recovery_requested BOOLEAN DEFAULT FALSE'
    )
);
PREPARE s18 FROM @stmt;
EXECUTE s18;
DEALLOCATE PREPARE s18;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'phone_number'
        ),
        'SELECT 1',
        'ALTER TABLE users ADD COLUMN phone_number VARCHAR(30)'
    )
);
PREPARE s19 FROM @stmt;
EXECUTE s19;
DEALLOCATE PREPARE s19;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'address'
        ),
        'SELECT 1',
        'ALTER TABLE users ADD COLUMN address VARCHAR(255)'
    )
);
PREPARE s20 FROM @stmt;
EXECUTE s20;
DEALLOCATE PREPARE s20;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'country'
        ),
        'SELECT 1',
        'ALTER TABLE users ADD COLUMN country VARCHAR(80)'
    )
);
PREPARE s21 FROM @stmt;
EXECUTE s21;
DEALLOCATE PREPARE s21;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'default_currency'
        ),
        'SELECT 1',
        'ALTER TABLE users ADD COLUMN default_currency VARCHAR(3)'
    )
);
PREPARE s22 FROM @stmt;
EXECUTE s22;
DEALLOCATE PREPARE s22;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'daily_transaction_limit'
        ),
        'SELECT 1',
        'ALTER TABLE users ADD COLUMN daily_transaction_limit DECIMAL(15, 2) DEFAULT 5000.00'
    )
);
PREPARE s23 FROM @stmt;
EXECUTE s23;
DEALLOCATE PREPARE s23;

SET @stmt = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'account_type'
        ),
        'SELECT 1',
        'ALTER TABLE accounts ADD COLUMN account_type VARCHAR(80)'
    )
);
PREPARE s24 FROM @stmt;
EXECUTE s24;
DEALLOCATE PREPARE s24;

ALTER TABLE support_tickets
    MODIFY COLUMN ticket_type ENUM('GENERAL', 'FAILED_PAYMENT', 'WRONG_RECIPIENT', 'DUPLICATE_PAYMENT', 'DAILY_LIMIT_EXCEEDED', 'INSUFFICIENT_FUNDS', 'CURRENCY_CONVERSION_ISSUE', 'OTHER', 'DISPUTE_SENDER', 'DISPUTE_RECEIVER') NOT NULL DEFAULT 'GENERAL';

