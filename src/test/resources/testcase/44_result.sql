truncate table if exists user;
truncate table if exists test;
truncate table if exists temp;

drop table if exists user;
drop table if exists test;
drop table if exists temp;

-- temp
create table temp(
  id INT AUTO_INCREMENT NOT NULL,
  user_id INT NOT NULL
);
-- test
create table test(
  id INT AUTO_INCREMENT NOT NULL,
  user_id INT NOT NULL
);
-- user
create table user(
  id INT NOT NULL
);

alter table temp add constraint fk_temp_01 foreign key(user_id) references test (id);
alter table test add constraint fk_test_01 foreign key(user_id) references user (id);
