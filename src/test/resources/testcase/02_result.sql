-- user
CREATE TABLE test1.user(
  id SERIAL NOT NULL,
  public_id VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  blood_type CHAR(1) NOT NULL,
  birthdate DATE NOT NULL,
  nick_name VARCHAR(50),
  registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE,
  PRIMARY KEY (id)
);
-- shop : 販売者
CREATE TABLE test1.shop(
  id SERIAL NOT NULL COMMENT "ID:サロゲートキー",
  user_id INT NOT NULL,
  registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE,
  PRIMARY KEY (id),
  COMMENT "販売者としての登録情報"
);
-- customer : 購入者
CREATE TABLE test1.customer(
  id SERIAL NOT NULL COMMENT "サロゲートキー",
  user_id INT NOT NULL,
  registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE,
  PRIMARY KEY (id)
);

ALTER TABLE test1.user ADD CONSTRAINT uq_user_01 UNIQUE (public_id);
ALTER TABLE test1.user ADD CONSTRAINT uq_user_02 UNIQUE (name, nick_name);

CREATE INDEX idx_user_01 ON test1.user (name);
CREATE INDEX idx_USER_02 ON test1.user (birthdate);

ALTER TABLE test1.shop ADD CONSTRAINT fk_shop_01 FOREIGN KEY(user_id) REFERENCES test1.user (id);

ALTER TABLE test1.customer ADD CONSTRAINT fk_customer_01 FOREIGN KEY(user_id) REFERENCES test1.user (id);
