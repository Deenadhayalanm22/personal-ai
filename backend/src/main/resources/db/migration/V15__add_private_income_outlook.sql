CREATE TABLE user_income_profile (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id),
    salary_visibility VARCHAR(20) NOT NULL,
    salary_range VARCHAR(30),
    exact_monthly_salary NUMERIC(19,2),
    salary_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_income_visibility CHECK (salary_visibility IN ('RANGE', 'EXACT', 'SKIPPED')),
    CONSTRAINT ck_income_range CHECK (salary_range IN ('UNDER_25000', 'FROM_25000_TO_50000', 'FROM_50000_TO_100000', 'FROM_100000_TO_200000', 'OVER_200000') OR salary_range IS NULL),
    CONSTRAINT ck_income_exact CHECK (exact_monthly_salary IS NULL OR exact_monthly_salary > 0),
    CONSTRAINT ck_income_frequency CHECK (salary_frequency IN ('MONTHLY', 'TWICE_MONTHLY', 'WEEKLY', 'IRREGULAR'))
);
