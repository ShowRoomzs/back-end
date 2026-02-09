    create table product_inquiry (
        is_secret bit,
        answered_at datetime(6),
        created_at datetime(6),
        modified_at datetime(6),
        product_id bigint not null,
        product_inquiry_id bigint not null auto_increment,
        user_id bigint not null,
        category varchar(50) not null,
        answer_content TEXT,
        content TEXT not null,
        status enum ('ANSWERED','WAITING') not null,
        type enum ('CANCEL_REFUND_EXCHANGE','DELIVERY','ORDER_PAYMENT','PRODUCT_CHECK','SERVICE','USER_INFO') not null,
        primary key (product_inquiry_id)
    ) engine=InnoDB;

    
    alter table product_inquiry 
       add constraint FK9pxrymiu3j0vgfloft4dssepu 
       foreign key (product_id) 
       references product (product_id);

    alter table product_inquiry 
       add constraint FK8c25iir6femhjwwygpfq51rx9 
       foreign key (user_id) 
       references users (user_id);
