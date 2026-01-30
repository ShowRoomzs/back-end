create table cart (
    quantity integer not null,
    cart_id bigint not null auto_increment,
    created_at datetime(6),
    modified_at datetime(6),
    user_id bigint not null,
    variant_id bigint not null,
    primary key (cart_id),
    unique (user_id, variant_id)
) engine=InnoDB;

alter table cart
    add constraint FKg9xh2o0x5a3r7b2p6j4c8c5u
    foreign key (user_id)
    references users (user_id);

alter table cart
    add constraint FK5utvkh4t0b91x8d3h8f9e4n0
    foreign key (variant_id)
    references product_variant (variant_id);
