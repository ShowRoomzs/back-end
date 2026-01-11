DROP TABLE IF EXISTS recent_search;

create table recent_search (
    created_at datetime(6) not null,
    recent_search_id bigint not null auto_increment,
    user_id bigint not null,
    term varchar(255) not null,
    primary key (recent_search_id)
) engine=InnoDB;

alter table recent_search 
add constraint FKjwtiy8gf03joqr0a7pn1ioy9j 
foreign key (user_id) 
references users (user_id);