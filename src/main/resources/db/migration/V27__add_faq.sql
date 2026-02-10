    create table faq (
        is_visible bit,
        created_at datetime(6),
        faq_id bigint not null auto_increment,
        modified_at datetime(6),
        category varchar(50) not null,
        answer TEXT not null,
        question TEXT not null,
        type enum ('CANCEL_REFUND_EXCHANGE','DELIVERY','ORDER_PAYMENT','PRODUCT_CHECK','SERVICE','USER_INFO') not null,
        primary key (faq_id)
    ) engine=InnoDB;