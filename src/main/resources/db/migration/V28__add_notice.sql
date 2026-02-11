create table notice (
    notice_id bigint not null auto_increment,
    title varchar(255) not null,
    content text not null,
    is_visible bit,
    created_at datetime(6),
    modified_at datetime(6),
    primary key (notice_id)
) engine=InnoDB;
