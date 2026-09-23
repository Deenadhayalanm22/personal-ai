ALTER TABLE user_investment ADD COLUMN sip_frequency VARCHAR(12) NOT NULL DEFAULT 'MONTHLY';
ALTER TABLE user_investment ADD COLUMN sip_anchor_month DATE;
ALTER TABLE user_investment ADD COLUMN current_nav_override NUMERIC(19,6);
UPDATE user_investment SET sip_anchor_month = sip_start_month WHERE asset_type = 'MUTUAL_FUND';
