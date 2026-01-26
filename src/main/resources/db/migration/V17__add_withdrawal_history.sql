    create table withdrawal_history (
        agree_consent bit not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        modified_at datetime(6),
        user_id bigint,
        custom_reason varchar(1000),
        reason enum ('DIFFICULT_SEARCH','ETC','INCONVENIENT_USE') not null,
        primary key (id)
    ) engine=InnoDB;