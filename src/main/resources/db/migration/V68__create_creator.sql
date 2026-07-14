create table creator (
    follower_count integer not null,
    created_at datetime(6),
    modified_at datetime(6),
    creator_id bigint not null auto_increment,
    user_id bigint not null,
    business_email varchar(512) not null,
    channel_url varchar(512) not null,
    sns_type enum ('INSTAGRAM','TIKTOK','X','YOUTUBE') not null,
    primary key (creator_id)
) engine=InnoDB;

alter table creator
    add constraint UK_creator_user_id unique (user_id);

alter table creator
    add constraint FK_creator_user_id
    foreign key (user_id)
    references users (user_id);
