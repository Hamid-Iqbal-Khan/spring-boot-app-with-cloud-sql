INSERT INTO users (name, plan, subscribe_date, unsubscribe_date)
VALUES
    -- PREMIUM + over 1 year = 30% rebate
    ('Alice Johnson',   'PREMIUM', '2022-03-15', NULL),
    ('Bob Smith',       'PREMIUM', '2021-11-01', NULL),

    -- BASIC + over 1 year = 10% rebate
    ('Carol White',     'BASIC',   '2023-01-20', NULL),
    ('David Brown',     'BASIC',   '2022-08-05', NULL),

    -- PREMIUM + under 1 year = 20% rebate
    ('Emma Davis',      'PREMIUM', '2025-12-01', NULL),
    ('Frank Miller',    'PREMIUM', '2026-02-14', NULL),

    -- BASIC + under 1 year = 0% rebate
    ('Grace Wilson',    'BASIC',   '2026-04-10', NULL),
    ('Henry Moore',     'BASIC',   '2026-01-30', NULL),

    -- No subscribe date = 0% rebate
    ('Isabella Taylor', 'BASIC',   NULL,         NULL),

    -- Unsubscribed user
    ('James Anderson',  'PREMIUM', '2021-06-01', '2024-12-31');
