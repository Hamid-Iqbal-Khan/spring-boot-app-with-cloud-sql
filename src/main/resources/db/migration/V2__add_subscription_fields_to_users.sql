ALTER TABLE users
    ADD COLUMN plan             VARCHAR(50),
    ADD COLUMN subscribe_date   DATE,
    ADD COLUMN unsubscribe_date DATE;
