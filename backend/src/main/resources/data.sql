-- ============================================================
-- Dummy Data for Payment Processing System
-- INSERT IGNORE skips rows that already exist on restart
-- ============================================================

-- ── USERS ────────────────────────────────────────────────────
INSERT IGNORE INTO users (user_id, full_name, email) VALUES
(1, 'Alice Johnson', 'alice@example.com'),
(2, 'Bob Smith', 'bob@example.com'),
(3, 'Priya Sharma', 'priya@example.com'),
(4, 'Ravi Kumar', 'ravi@example.com'),
(5, 'Campaign System', 'campaign@example.com');

-- ── ACCOUNTS ─────────────────────────────────────────────────
INSERT IGNORE INTO accounts (account_id, user_id, account_holder_name, account_number, bank_name, bank_ifsc, currency_code, balance, account_status, is_bucket_account, max_daily_limit) VALUES
(1, 1, 'Alice Johnson',   'ACC001USD', 'Axis Bank', 'AXIS0001', 'USD', 50000.00,  'ACTIVE', FALSE, 10000.00),
(2, 2, 'Bob Smith',       'ACC002USD', 'HDFC Bank', 'HDFC0002', 'USD', 20000.00,  'ACTIVE', FALSE, 5000.00),
(3, 3, 'Priya Sharma',    'ACC003INR', 'SBI Bank',  'SBIN0003', 'INR', 800000.00, 'ACTIVE', FALSE, 100000.00),
(4, 4, 'Ravi Kumar',      'ACC004INR', 'ICICI Bank','ICIC0004', 'INR', 150000.00, 'ACTIVE', FALSE, 50000.00),
(5, 5, 'Crowdfund Bucket','ACC005USD', 'Yes Bank',  'YESB0005', 'USD', 0.00,      'ACTIVE', TRUE,  999999.00);

-- ── CROWDFUNDING_CAMPAIGNS ───────────────────────────────────
INSERT IGNORE INTO crowdfunding_campaigns (campaign_id, campaign_name, description, donation_category, donation_options, bucket_account_id, target_amount, target_currency, current_amount, threshold_percentage, status, campaign_end_date) VALUES
(1, 'Help Build a School', 'Community school infrastructure campaign.', 'Education', '100,500,1000', 5, 10000.00, 'USD', 3500.00, 80,  'ACTIVE',    '2026-12-31'),
(2, 'Medical Aid Fund',    'Emergency health support fundraiser.',     'Healthcare', '250,750,1500', 5, 5000.00,  'USD', 5000.00, 100, 'COMPLETED', '2026-07-01');

-- ── PAYMENTS ─────────────────────────────────────────────────
INSERT IGNORE INTO payments (payment_id, payment_reference, idempotency_key, source_account_id, destination_account_id, amount, converted_amount, forex_fee, currency_code, destination_currency_code, payment_type, crowdfunding_campaign_id, status, error_code, created_at, completed_at) VALUES
(1, 'PAY-20260803-0001', 'idem-1', 1, 2, 500.00, 500.00, 0.00,  'USD', 'USD', 'REGULAR',      NULL, 'COMPLETED', NULL,                 '2026-08-03 09:00:00', '2026-08-03 09:00:45'),
(2, 'PAY-20260803-0002', 'idem-2', 3, 4, 8000.00, 8000.00, 0.00,'INR', 'INR', 'REGULAR',      NULL, 'COMPLETED', NULL,                 '2026-08-03 10:15:00', '2026-08-03 10:15:30'),
(3, 'PAY-20260803-0003', 'idem-3', 2, 1, 200.00, 200.00, 0.00,  'USD', 'USD', 'REGULAR',      NULL, 'FAILED',    'INSUFFICIENT_FUNDS', '2026-08-03 11:00:00', NULL),
(4, 'PAY-20260803-0004', 'idem-4', 1, 5, 1000.00, 1000.00, 0.00,'USD', 'USD', 'CROWDFUNDING', 1,    'COMPLETED', NULL,                 '2026-08-03 12:00:00', '2026-08-03 12:00:20'),
(5, 'PAY-20260803-0005', 'idem-5', 2, 5, 2500.00, 2500.00, 0.00,'USD', 'USD', 'CROWDFUNDING', 1,    'COMPLETED', NULL,                 '2026-08-03 13:00:00', '2026-08-03 13:00:15'),
(6, 'PAY-20260803-0006', 'idem-6', 4, 3, 5000.00, 5000.00, 0.00,'INR', 'INR', 'REGULAR',      NULL, 'VALIDATED', NULL,                 '2026-08-03 14:00:00', NULL),
(7, 'PAY-20260803-0007', 'idem-7', 1, 2, 300.00, 300.00, 0.00,  'USD', 'USD', 'REGULAR',      NULL, 'CREATED',   NULL,                 '2026-08-03 15:00:00', NULL);

-- ── PAYMENT_STATUS_AUDIT ─────────────────────────────────────
INSERT IGNORE INTO payment_status_audit (audit_id, payment_id, from_status, to_status, status, description, changed_at) VALUES
(1,  1, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 09:00:00'),
(2,  1, 'CREATED',   'VALIDATED', 'VALIDATED', 'Validation complete',             '2026-08-03 09:00:10'),
(3,  1, 'VALIDATED', 'SENT',      'SENT',      'Sent to gateway',                 '2026-08-03 09:00:30'),
(4,  1, 'SENT',      'COMPLETED', 'COMPLETED', 'Completed successfully',          '2026-08-03 09:00:45'),
(5,  2, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 10:15:00'),
(6,  2, 'CREATED',   'VALIDATED', 'VALIDATED', 'Validation complete',             '2026-08-03 10:15:10'),
(7,  2, 'VALIDATED', 'SENT',      'SENT',      'Sent to gateway',                 '2026-08-03 10:15:20'),
(8,  2, 'SENT',      'COMPLETED', 'COMPLETED', 'Completed successfully',          '2026-08-03 10:15:30'),
(9,  3, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 11:00:00'),
(10, 3, 'CREATED',   'FAILED',    'FAILED',    'Insufficient funds',              '2026-08-03 11:00:05'),
(11, 4, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 12:00:00'),
(12, 4, 'CREATED',   'VALIDATED', 'VALIDATED', 'Validation complete',             '2026-08-03 12:00:05'),
(13, 4, 'VALIDATED', 'SENT',      'SENT',      'Sent to gateway',                 '2026-08-03 12:00:10'),
(14, 4, 'SENT',      'COMPLETED', 'COMPLETED', 'Completed successfully',          '2026-08-03 12:00:20'),
(15, 5, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 13:00:00'),
(16, 5, 'CREATED',   'VALIDATED', 'VALIDATED', 'Validation complete',             '2026-08-03 13:00:05'),
(17, 5, 'VALIDATED', 'SENT',      'SENT',      'Sent to gateway',                 '2026-08-03 13:00:10'),
(18, 5, 'SENT',      'COMPLETED', 'COMPLETED', 'Completed successfully',          '2026-08-03 13:00:15'),
(19, 6, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 14:00:00'),
(20, 6, 'CREATED',   'VALIDATED', 'VALIDATED', 'Validation complete',             '2026-08-03 14:00:10'),
(21, 7, NULL,        'CREATED',   'CREATED',   'Payment created',                 '2026-08-03 15:00:00');

-- ── SUPPORT_TICKETS ──────────────────────────────────────────
INSERT IGNORE INTO support_tickets (ticket_id, ticket_number, payment_id, account_id, user_id, title, description, failure_reason, ticket_type, dispute_role, recovery_requested, priority, status, resolution_summary, created_at, resolved_at) VALUES
(1, 'TKT-20260803-0001', 3, 2, 2, 'Payment Failed - Insufficient Funds', 'Payment PAY-20260803-0003 failed due to insufficient funds.', 'INSUFFICIENT_FUNDS', 'FAILED_PAYMENT', 'NONE', FALSE, 'HIGH', 'OPEN',     NULL,                          '2026-08-03 11:01:00', NULL),
(2, 'TKT-20260803-0002', NULL, 1, 1, 'General Account Query',            'Need help understanding my transaction history.',              NULL,                 'GENERAL',       'NONE', FALSE, 'LOW',  'RESOLVED', 'Explained transaction history.','2026-08-03 08:00:00', '2026-08-03 09:30:00');
