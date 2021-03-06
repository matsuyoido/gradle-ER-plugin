-- user
CREATE TABLE test1.user(
  id INT NOT NULL,
  public_id VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  blood_type CHAR(1) NOT NULL,
  birthdate DATE NOT NULL,
  nick_name VARCHAR(50)
);
ALTER TABLE test1.user ADD CONSTRAINT user_PK PRIMARY KEY (id);
-- shop
CREATE TABLE test1.shop(
  id INT NOT NULL,
  user_id INT NOT NULL
);
ALTER TABLE test1.shop ADD CONSTRAINT shop_PK PRIMARY KEY (id);
-- customer
CREATE TABLE test1.customer(
  id INT NOT NULL,
  user_id INT NOT NULL
);
ALTER TABLE test1.customer ADD CONSTRAINT customer_PK PRIMARY KEY (id);

ALTER TABLE test1.user ADD CONSTRAINT uq_user_01 UNIQUE (public_id);
ALTER TABLE test1.user ADD CONSTRAINT uq_user_02 UNIQUE (name, nick_name);

CREATE INDEX idx_user_01 ON test1.user (name);
CREATE INDEX idx_USER_02 ON test1.user (birthdate);

ALTER TABLE test1.shop ADD CONSTRAINT fk_shop_01 FOREIGN KEY(user_id) REFERENCES test1.user (id);
ALTER TABLE test1.customer ADD CONSTRAINT fk_customer_01 FOREIGN KEY(user_id) REFERENCES test1.user (id);




