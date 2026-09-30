CREATE TABLE ai_credit_wallet (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    balance NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    reserved NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (reserved >= 0 AND reserved <= balance),
    paused BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE ai_credit_budget (
    day DATE PRIMARY KEY,
    spent NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (spent >= 0),
    reserved NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (reserved >= 0)
);
CREATE TABLE ai_credit_request (
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    fingerprint TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','REVIEW')),
    response TEXT,
    error_code TEXT,
    error_message TEXT,
    error_status INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id,id)
);
CREATE INDEX ai_credit_request_recent ON ai_credit_request(user_id,created_at DESC);
CREATE UNIQUE INDEX ai_credit_request_one_active ON ai_credit_request(user_id) WHERE status IN ('RUNNING','REVIEW');
CREATE TABLE ai_credit_call (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_id UUID NOT NULL,
    budget_day DATE NOT NULL REFERENCES ai_credit_budget(day),
    model TEXT NOT NULL,
    input_rate NUMERIC(20,6) NOT NULL,
    cached_rate NUMERIC(20,6) NOT NULL,
    output_rate NUMERIC(20,6) NOT NULL,
    reserved NUMERIC(20,6) NOT NULL CHECK (reserved > 0),
    charged NUMERIC(20,6) CHECK (charged >= 0 AND charged <= reserved),
    input_tokens BIGINT,
    cached_tokens BIGINT,
    output_tokens BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED','SETTLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id,request_id) REFERENCES ai_credit_request(user_id,id) ON DELETE CASCADE
);
CREATE INDEX ai_credit_call_request ON ai_credit_call(user_id,request_id);
CREATE TABLE ai_credit_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    amount NUMERIC(20,6) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    actor_id BIGINT REFERENCES app_user(id),
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id,operation_id)
);
