----------------------------------------
-- SCRIPT START
----------------------------------------

GRANT ALL PRIVILEGES ON DATABASE postgres TO usr;
--
CREATE SCHEMA exposed AUTHORIZATION usr;
----------------------------------------
-- DB_USER
----------------------------------------
GRANT USAGE ON SCHEMA exposed TO usr;
----------------------------------------
-- GRANTS
----------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA exposed TO usr;
----------------------------------------
-- SCRIPT END
----------------------------------------

CREATE TABLE exposed.exposed_test
(
    id        INT NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    full_name varchar(50)
);

CREATE UNIQUE INDEX full_name_uix ON exposed.exposed_test(full_name);
ALTER TABLE exposed.exposed_test
    ADD CONSTRAINT unique_full_name
        UNIQUE USING INDEX full_name_uix;