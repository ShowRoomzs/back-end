create table login_history (
id bigint not null auto_increment,
login_at datetime(6),
user_id bigint,
city varchar(255),
client_ip varchar(255),
country varchar(255),
user_agent varchar(255),
status enum ('ABNORMAL','SUCCESS'),
primary key (id)
) engine=InnoDB;

alter table login_history 
add constraint FK20v0mimmdegh2afs39uixlxpm 
foreign key (user_id) 
references users (user_id);