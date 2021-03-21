TRUNCATE TABLE IF EXISTS schema_test.user;
TRUNCATE TABLE IF EXISTS schema_test.test;
TRUNCATE TABLE IF EXISTS schema_test.temp;

DROP TABLE IF EXISTS schema_test.user;
DROP TABLE IF EXISTS schema_test.test;
DROP TABLE IF EXISTS schema_test.temp;

-- temp
CREATE TABLE schema_test.temp(
  id INT AUTO_INCREMENT NOT NULL,
  user_id INT NOT NULL
);
-- test
CREATE TABLE schema_test.test(
  id INT AUTO_INCREMENT NOT NULL,
  user_id INT NOT NULL
);
-- user
CREATE TABLE schema_test.user(
  id INT NOT NULL
);

ALTER TABLE schema_test.temp ADD CONSTRAINT fk_temp_01 FOREIGN KEY(user_id) REFERENCES schema_test.test (id);
ALTER TABLE schema_test.test ADD CONSTRAINT fk_test_01 FOREIGN KEY(user_id) REFERENCES schema_test.user (id);
