ALTER TABLE payments ADD COLUMN refunded_amount DECIMAL(12, 2);
ALTER TABLE payments ADD COLUMN refund_transaction_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN refunded_at TIMESTAMP;
