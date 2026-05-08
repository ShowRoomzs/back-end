    create table user_status_history (
        created_at datetime(6),
        modified_at datetime(6),
        user_id bigint not null,
        user_status_history_id bigint not null auto_increment,
        reason varchar(500),
        new_status enum ('DORMANT','NORMAL','SUSPENDED','WITHDRAWN') not null,
        previous_status enum ('DORMANT','NORMAL','SUSPENDED','WITHDRAWN') not null,
        primary key (user_status_history_id)
    ) engine=InnoDB;

    alter table user_status_history 
       add constraint FKa0m43tddfjbom8e7cnt1cdmjl 
       foreign key (user_id) 
       references users (user_id);