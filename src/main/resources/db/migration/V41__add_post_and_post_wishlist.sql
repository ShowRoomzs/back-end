
create table post (
    is_display bit not null,
    created_at datetime(6),
    market_id bigint not null,
    modified_at datetime(6),
    post_id bigint not null auto_increment,
    view_count bigint not null,
    wishlist_count bigint not null,
    title varchar(200) not null,
    content TEXT,
    primary key (post_id)
) engine=InnoDB;

create table post_images (
    post_id bigint not null,
    image_url varchar(512)
) engine=InnoDB;

create table post_product (
    post_id bigint not null,
    post_product_id bigint not null auto_increment,
    product_id bigint not null,
    primary key (post_product_id)
) engine=InnoDB;

create table post_wishlist (
    created_at datetime(6),
    post_id bigint not null,
    post_wishlist_id bigint not null auto_increment,
    user_id bigint not null,
    primary key (post_wishlist_id)
) engine=InnoDB;

alter table post_wishlist 
    add constraint post_wishlist_uk unique (user_id, post_id);


alter table post 
    add constraint FKl8xkjvcym3fq9lervw5tm6do9 
    foreign key (market_id) 
    references market (market_id);

alter table post_images 
    add constraint FK4436mqgshkhub17yvq5ku91f7 
    foreign key (post_id) 
    references post (post_id);

alter table post_product 
    add constraint FKamrwvf18xpi2alsfvww5usjed 
    foreign key (post_id) 
    references post (post_id);

alter table post_product 
    add constraint FKq3iw3rc3cjqp9tgel6bmycdie 
    foreign key (product_id) 
    references product (product_id);

alter table post_wishlist 
    add constraint FKv14tlisj3jqd6ejyrryd7nl8 
    foreign key (post_id) 
    references post (post_id);

alter table post_wishlist 
    add constraint FKj5pl9ls0txnfcsdfnsi3852q5 
    foreign key (user_id) 
    references users (user_id);