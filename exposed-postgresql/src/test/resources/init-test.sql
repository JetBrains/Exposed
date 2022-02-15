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

CREATE TABLE exposed.anime_character
(
    id        INT NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    full_name varchar(50) NOT NULL,
    anime     varchar(50)
);

CREATE UNIQUE INDEX full_name_uix ON exposed.anime_character (full_name);
ALTER TABLE exposed.anime_character
    ADD CONSTRAINT unique_full_name
        UNIQUE USING INDEX full_name_uix;