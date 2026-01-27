    create table delivery_address (
        is_default bit not null,
        address_id bigint not null auto_increment,
        created_at datetime(6),
        modified_at datetime(6),
        user_id bigint not null,
        zip_code varchar(10) not null,
        phone_number varchar(20) not null,
        recipient_name varchar(64) not null,
        address varchar(255) not null,
        detail_address varchar(255) not null,
        primary key (address_id)
    ) engine=InnoDB;

        alter table delivery_address 
    add constraint FKlunoaq6lsl5d75tkbg3alrn6u 
    foreign key (user_id) 
    references users (user_id);