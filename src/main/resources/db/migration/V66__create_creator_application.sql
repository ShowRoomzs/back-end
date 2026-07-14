 create table creator_application (
        follower_count integer not null,
        created_at datetime(6),
        creator_application_id bigint not null auto_increment,
        modified_at datetime(6),
        processed_at datetime(6),
        user_id bigint not null,
        reject_reason varchar(500),
        business_email varchar(512) not null,
        channel_url varchar(512) not null,
        sns_type enum ('INSTAGRAM','TIKTOK','X','YOUTUBE') not null,
        status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (creator_application_id)
    ) engine=InnoDB;

    create table creator_application_history (
        created_at datetime(6),
        creator_application_history_id bigint not null auto_increment,
        creator_application_id bigint not null,
        modified_at datetime(6),
        reason varchar(500),
        new_status enum ('APPROVED','PENDING','REJECTED') not null,
        previous_status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (creator_application_history_id)
    ) engine=InnoDB;


     alter table creator_application 
       add constraint FKlys339t86oo10akg7x6ycvq16 
       foreign key (user_id) 
       references users (user_id);

    alter table creator_application_history 
       add constraint FK28cu19e7okeyqbarcorsrl6vd 
       foreign key (creator_application_id) 
       references creator_application (creator_application_id);