-- user
create table user(
  id INT NOT NULL,
  public_id VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  blood_type CHAR(1) NOT NULL,
  birthdate DATE NOT NULL,
  nick_name VARCHAR(50)
);
-- test
create table test(
  id INT AUTO_INCREMENT NOT NULL
);
