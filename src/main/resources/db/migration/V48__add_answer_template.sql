    create table answer_template (
    is_active bit not null,
    answer_template_id bigint not null auto_increment,
    created_at datetime(6),
    modified_at datetime(6),
    seller_id bigint not null,
    title varchar(30) not null,
    content varchar(1000) not null,
    category enum ('CANCEL_REFUND_EXCHANGE','DEFECT_AS','DELIVERY','ORDER_PAYMENT','PRODUCT','SIZE','STOCK') not null,
    primary key (answer_template_id)
) engine=InnoDB;

alter table answer_template 
    add constraint FK6y0o2v17d0fxur25nponx3t7 
    foreign key (seller_id) 
    references seller (seller_id);
