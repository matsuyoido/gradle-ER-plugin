-- user
create table user(
  id INT NOT NULL,
  public_id VARCHAR(8) NOT NULL,
  name VARCHAR(50) NOT NULL,
  blood_type CHAR(1) NOT NULL,
  birthdate DATE NOT NULL,
  nick_name VARCHAR(50),
  primary key (id)
);
-- shop
create table shop(
  id INT NOT NULL,
  user_id INT NOT NULL,
  primary key (id)
);
-- customer
create table customer(
  id INT NOT NULL,
  user_id INT NOT NULL,
  primary key (id)
);

alter table user add constraint uq_user_01 unique (public_id);
alter table user add constraint uq_user_02 unique (name, nick_name);

create index idx_user_01 on user (name);
create index idx_USER_02 on user (birthdate);

alter table shop add constraint fk_shop_01 foreign key(user_id) references user (id);
alter table customer add constraint fk_customer_01 foreign key(user_id) references user (id);




