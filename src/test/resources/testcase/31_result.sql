-- user
CREATE TABLE user(
  id SERIAL NOT NULL,
  public_id VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  blood_type CHAR(1) NOT NULL,
  birthdate DATE NOT NULL,
  nick_name VARCHAR(50),
  PRIMARY KEY (id)
);
-- shop
CREATE TABLE shop(
  id SERIAL NOT NULL,
  user_id INT NOT NULL,
  PRIMARY KEY (id)
);
-- customer
CREATE TABLE customer(
  id SERIAL NOT NULL,
  user_id INT NOT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE user ADD CONSTRAINT uq_user_01 UNIQUE (public_id);
ALTER TABLE user ADD CONSTRAINT uq_user_02 UNIQUE (name, nick_name);

CREATE INDEX idx_user_01 ON user (name);
CREATE INDEX idx_USER_02 ON user (birthdate);

ALTER TABLE shop ADD CONSTRAINT fk_shop_01 FOREIGN KEY(user_id) REFERENCES user (id);
ALTER TABLE customer ADD CONSTRAINT fk_customer_01 FOREIGN KEY(user_id) REFERENCES user (id);




