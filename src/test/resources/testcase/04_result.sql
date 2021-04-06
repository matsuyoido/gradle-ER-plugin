-- user
create table user(
  id SERIAL NOT NULL,
  public_id VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  blood_type CHAR(1) NOT NULL,
  birthdate DATE NOT NULL,
  nick_name VARCHAR(50),
  registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);
alter table user add constraint user_PK primary key (id);
-- shop : 販売者
create table shop(
  id SERIAL NOT NULL comment "ID:サロゲートキー",
  user_id INT NOT NULL,
  registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE,
  comment "販売者としての登録情報"
);
alter table shop add constraint shop_PK primary key (id);
-- customer : 購入者
create table customer(
  id SERIAL NOT NULL comment "サロゲートキー",
  user_id INT NOT NULL,
  registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE
);
alter table customer add constraint customer_PK primary key (id);

alter table user add constraint uq_user_01 unique (public_id);
alter table user add constraint uq_user_02 unique (name, nick_name);

create index idx_user_01 on user (name);
create index idx_USER_02 on user (birthdate);

alter table shop add constraint fk_shop_01 foreign key(user_id) references user (id);

alter table customer add constraint fk_customer_01 foreign key(user_id) references user (id);
