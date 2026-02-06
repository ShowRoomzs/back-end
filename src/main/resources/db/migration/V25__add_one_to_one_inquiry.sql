    create table one_to_one_inquiry (
        answered_at datetime(6),
        created_at datetime(6),
        inquiry_id bigint not null auto_increment,
        modified_at datetime(6),
        user_id bigint not null,
        category varchar(50) not null,
        answer_content TEXT,
        content TEXT not null,
        status enum ('ANSWERED','WAITING') not null,
        type enum ('CANCEL_REFUND_EXCHANGE','DELIVERY','ORDER_PAYMENT','PRODUCT_CHECK','SERVICE','USER_INFO') not null,
        primary key (inquiry_id)
    ) engine=InnoDB;

    create table one_to_one_inquiry_images (
        inquiry_id bigint not null,
        image_url varchar(512)
    ) engine=InnoDB;

        alter table one_to_one_inquiry 
       add constraint FKk920q42lbivq7lp6jsk0nated 
       foreign key (user_id) 
       references users (user_id);

       
    alter table one_to_one_inquiry_images 
       add constraint FKcnd3xq8ha57fkot5hko6e8wkn 
       foreign key (inquiry_id) 
       references one_to_one_inquiry (inquiry_id);