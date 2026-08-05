-- Clear all runtime data on startup so the application begins empty.
DELETE FROM payment_status_audit;
DELETE FROM support_tickets;
DELETE FROM payments;
DELETE FROM crowdfunding_campaigns;
DELETE FROM accounts;
DELETE FROM users;

