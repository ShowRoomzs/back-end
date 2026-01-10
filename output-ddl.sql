
    create table admin_refresh_token (
        refresh_token_seq bigint not null auto_increment,
        refresh_token varchar(256) not null,
        admin_email varchar(512) not null,
        primary key (refresh_token_seq)
    ) engine=InnoDB;

    create table category (
        `order` integer,
        category_id bigint not null auto_increment,
        parent_id bigint,
        icon_url varchar(2048),
        name varchar(255) not null,
        primary key (category_id)
    ) engine=InnoDB;

    create table market (
        market_id bigint not null auto_increment,
        seller_id bigint not null,
        main_category varchar(100),
        market_image_url varchar(512),
        market_url varchar(512),
        sns_link_1 varchar(512),
        sns_link_2 varchar(512),
        sns_link_3 varchar(512),
        market_description varchar(1000),
        cs_number varchar(255) not null,
        market_name varchar(255) not null,
        market_image_status enum ('APPROVED','REJECTED','UNDER_REVIEW'),
        primary key (market_id)
    ) engine=InnoDB;

    create table market_follow (
        created_at datetime(6) not null,
        follow_id bigint not null auto_increment,
        market_id bigint not null,
        user_id bigint not null,
        primary key (follow_id)
    ) engine=InnoDB;

    create table product (
        delivery_estimated_days integer,
        delivery_fee integer,
        delivery_free_threshold integer,
        is_display bit not null,
        is_out_of_stock_forced bit not null,
        is_recommended bit not null,
        purchase_price integer,
        regular_price integer not null,
        sale_price integer not null,
        category_id bigint not null,
        created_at datetime(6) not null,
        market_id bigint not null,
        product_id bigint not null auto_increment,
        product_number varchar(50),
        delivery_type varchar(100),
        seller_product_code varchar(100),
        thumbnail_url varchar(2048),
        description text,
        name varchar(255) not null,
        product_notice json,
        tags json,
        primary key (product_id)
    ) engine=InnoDB;

    create table product_image (
        `order` integer not null,
        image_id bigint not null auto_increment,
        product_id bigint not null,
        url varchar(2048) not null,
        primary key (image_id)
    ) engine=InnoDB;

    create table product_option (
        option_group_id bigint not null,
        option_id bigint not null auto_increment,
        name varchar(255) not null,
        primary key (option_id)
    ) engine=InnoDB;

    create table product_option_group (
        option_group_id bigint not null auto_increment,
        product_id bigint not null,
        name varchar(255) not null,
        primary key (option_group_id)
    ) engine=InnoDB;

    create table product_variant (
        is_representative bit not null,
        regular_price integer not null,
        sale_price integer not null,
        stock integer not null,
        product_id bigint not null,
        variant_id bigint not null auto_increment,
        name varchar(255),
        primary key (variant_id)
    ) engine=InnoDB;

    create table recent_search (
        searched_at datetime(6) not null,
        user_id bigint not null,
        id binary(16) not null,
        term varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table seller (
        created_at datetime(6) not null,
        modified_at datetime(6) not null,
        seller_id bigint not null auto_increment,
        phone_number varchar(20),
        name varchar(64) not null,
        password varchar(128) not null,
        rejection_reason varchar(500),
        email varchar(512) not null,
        role_type enum ('ADMIN','GUEST','SELLER','USER') not null,
        status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (seller_id)
    ) engine=InnoDB;

    create table user_refresh_token (
        refresh_token_seq bigint not null auto_increment,
        user_id varchar(64) not null,
        refresh_token varchar(256) not null,
        primary key (refresh_token_seq)
    ) engine=InnoDB;

    create table users (
        email_verified_yn varchar(1) not null,
        marketing_agree bit,
        privacy_agree bit,
        service_agree bit,
        created_at datetime(6) not null,
        modified_at datetime(6) not null,
        user_id bigint not null auto_increment,
        birthday varchar(10),
        gender varchar(10),
        phone_number varchar(20),
        name varchar(64),
        username varchar(64) not null,
        nickname varchar(100) not null,
        password varchar(128) not null,
        email varchar(512) not null,
        profile_image_url varchar(512),
        provider_type enum ('APPLE','FACEBOOK','GOOGLE','KAKAO','LOCAL','NAVER') not null,
        role_type enum ('ADMIN','GUEST','SELLER','USER') not null,
        primary key (user_id)
    ) engine=InnoDB;

    create table variant_option_map (
        option_id bigint not null,
        variant_id bigint not null
    ) engine=InnoDB;

    alter table admin_refresh_token 
       add constraint UKlob6xik9nhf2v8qbso6ft7sec unique (admin_email);

    alter table market 
       add constraint UKagtyaitfan2mngy8ocdu9tle5 unique (seller_id);

    alter table market 
       add constraint UKqb8gnd8e5hl8gkmv4m9nxude3 unique (market_name);

    alter table market_follow 
       add constraint UK_MARKET_FOLLOW unique (user_id, market_id);

    alter table product 
       add constraint UKo9lr7abbchek72c2xu8x0g884 unique (product_number);

    alter table seller 
       add constraint UKcrgbovyy4gvgsum2yyb3fbfn7 unique (email);

    alter table user_refresh_token 
       add constraint UKqca3mjxv5a1egwmn4wnbplfkt unique (user_id);

    alter table users 
       add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

    alter table users 
       add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table category 
       add constraint FK2y94svpmqttx80mshyny85wqr 
       foreign key (parent_id) 
       references category (category_id);

    alter table market 
       add constraint FKkqb83ae8c9vy3aut4sjdouhhf 
       foreign key (seller_id) 
       references seller (seller_id);

    alter table market_follow 
       add constraint FKp6mcjw6xqvldkjfdj913wjgjk 
       foreign key (market_id) 
       references market (market_id);

    alter table market_follow 
       add constraint FKeopukl7njb1axvoktdbqrlpqk 
       foreign key (user_id) 
       references users (user_id);

    alter table product 
       add constraint FK1mtsbur82frn64de7balymq9s 
       foreign key (category_id) 
       references category (category_id);

    alter table product 
       add constraint FKnd0xf8hu7ixgw6u0do43xp2fb 
       foreign key (market_id) 
       references market (market_id);

    alter table product_image 
       add constraint FK6oo0cvcdtb6qmwsga468uuukk 
       foreign key (product_id) 
       references product (product_id);

    alter table product_option 
       add constraint FK1chbk3kb4ib2qwwitild434g4 
       foreign key (option_group_id) 
       references product_option_group (option_group_id);

    alter table product_option_group 
       add constraint FKsifetwvwtdfqltwegvv0ijt28 
       foreign key (product_id) 
       references product (product_id);

    alter table product_variant 
       add constraint FKgrbbs9t374m9gg43l6tq1xwdj 
       foreign key (product_id) 
       references product (product_id);

    alter table recent_search 
       add constraint FKjwtiy8gf03joqr0a7pn1ioy9j 
       foreign key (user_id) 
       references users (user_id);

    alter table variant_option_map 
       add constraint FKipnrov51jqfx8emcvoiv7lwfj 
       foreign key (option_id) 
       references product_option (option_id);

    alter table variant_option_map 
       add constraint FK9jg4lc8rq7ys8gkfs08lreccy 
       foreign key (variant_id) 
       references product_variant (variant_id);

    create table admin_refresh_token (
        refresh_token_seq bigint not null auto_increment,
        refresh_token varchar(256) not null,
        admin_email varchar(512) not null,
        primary key (refresh_token_seq)
    ) engine=InnoDB;

    create table category (
        `order` integer,
        category_id bigint not null auto_increment,
        parent_id bigint,
        icon_url varchar(2048),
        name varchar(255) not null,
        primary key (category_id)
    ) engine=InnoDB;

    create table market (
        market_id bigint not null auto_increment,
        seller_id bigint not null,
        main_category varchar(100),
        market_image_url varchar(512),
        market_url varchar(512),
        sns_link_1 varchar(512),
        sns_link_2 varchar(512),
        sns_link_3 varchar(512),
        market_description varchar(1000),
        cs_number varchar(255) not null,
        market_name varchar(255) not null,
        market_image_status enum ('APPROVED','REJECTED','UNDER_REVIEW'),
        primary key (market_id)
    ) engine=InnoDB;

    create table market_follow (
        created_at datetime(6) not null,
        follow_id bigint not null auto_increment,
        market_id bigint not null,
        user_id bigint not null,
        primary key (follow_id)
    ) engine=InnoDB;

    create table product (
        delivery_estimated_days integer,
        delivery_fee integer,
        delivery_free_threshold integer,
        is_display bit not null,
        is_out_of_stock_forced bit not null,
        is_recommended bit not null,
        purchase_price integer,
        regular_price integer not null,
        sale_price integer not null,
        category_id bigint not null,
        created_at datetime(6) not null,
        market_id bigint not null,
        product_id bigint not null auto_increment,
        product_number varchar(50),
        delivery_type varchar(100),
        seller_product_code varchar(100),
        thumbnail_url varchar(2048),
        description text,
        name varchar(255) not null,
        product_notice json,
        tags json,
        primary key (product_id)
    ) engine=InnoDB;

    create table product_image (
        `order` integer not null,
        image_id bigint not null auto_increment,
        product_id bigint not null,
        url varchar(2048) not null,
        primary key (image_id)
    ) engine=InnoDB;

    create table product_option (
        option_group_id bigint not null,
        option_id bigint not null auto_increment,
        name varchar(255) not null,
        primary key (option_id)
    ) engine=InnoDB;

    create table product_option_group (
        option_group_id bigint not null auto_increment,
        product_id bigint not null,
        name varchar(255) not null,
        primary key (option_group_id)
    ) engine=InnoDB;

    create table product_variant (
        is_representative bit not null,
        regular_price integer not null,
        sale_price integer not null,
        stock integer not null,
        product_id bigint not null,
        variant_id bigint not null auto_increment,
        name varchar(255),
        primary key (variant_id)
    ) engine=InnoDB;

    create table recent_search (
        searched_at datetime(6) not null,
        user_id bigint not null,
        id binary(16) not null,
        term varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table seller (
        created_at datetime(6) not null,
        modified_at datetime(6) not null,
        seller_id bigint not null auto_increment,
        phone_number varchar(20),
        name varchar(64) not null,
        password varchar(128) not null,
        rejection_reason varchar(500),
        email varchar(512) not null,
        role_type enum ('ADMIN','GUEST','SELLER','USER') not null,
        status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (seller_id)
    ) engine=InnoDB;

    create table user_refresh_token (
        refresh_token_seq bigint not null auto_increment,
        user_id varchar(64) not null,
        refresh_token varchar(256) not null,
        primary key (refresh_token_seq)
    ) engine=InnoDB;

    create table users (
        email_verified_yn varchar(1) not null,
        marketing_agree bit,
        privacy_agree bit,
        service_agree bit,
        created_at datetime(6) not null,
        modified_at datetime(6) not null,
        user_id bigint not null auto_increment,
        birthday varchar(10),
        gender varchar(10),
        phone_number varchar(20),
        name varchar(64),
        username varchar(64) not null,
        nickname varchar(100) not null,
        password varchar(128) not null,
        email varchar(512) not null,
        profile_image_url varchar(512),
        provider_type enum ('APPLE','FACEBOOK','GOOGLE','KAKAO','LOCAL','NAVER') not null,
        role_type enum ('ADMIN','GUEST','SELLER','USER') not null,
        primary key (user_id)
    ) engine=InnoDB;

    create table variant_option_map (
        option_id bigint not null,
        variant_id bigint not null
    ) engine=InnoDB;

    alter table admin_refresh_token 
       add constraint UKlob6xik9nhf2v8qbso6ft7sec unique (admin_email);

    alter table market 
       add constraint UKagtyaitfan2mngy8ocdu9tle5 unique (seller_id);

    alter table market 
       add constraint UKqb8gnd8e5hl8gkmv4m9nxude3 unique (market_name);

    alter table market_follow 
       add constraint UK_MARKET_FOLLOW unique (user_id, market_id);

    alter table product 
       add constraint UKo9lr7abbchek72c2xu8x0g884 unique (product_number);

    alter table seller 
       add constraint UKcrgbovyy4gvgsum2yyb3fbfn7 unique (email);

    alter table user_refresh_token 
       add constraint UKqca3mjxv5a1egwmn4wnbplfkt unique (user_id);

    alter table users 
       add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

    alter table users 
       add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table category 
       add constraint FK2y94svpmqttx80mshyny85wqr 
       foreign key (parent_id) 
       references category (category_id);

    alter table market 
       add constraint FKkqb83ae8c9vy3aut4sjdouhhf 
       foreign key (seller_id) 
       references seller (seller_id);

    alter table market_follow 
       add constraint FKp6mcjw6xqvldkjfdj913wjgjk 
       foreign key (market_id) 
       references market (market_id);

    alter table market_follow 
       add constraint FKeopukl7njb1axvoktdbqrlpqk 
       foreign key (user_id) 
       references users (user_id);

    alter table product 
       add constraint FK1mtsbur82frn64de7balymq9s 
       foreign key (category_id) 
       references category (category_id);

    alter table product 
       add constraint FKnd0xf8hu7ixgw6u0do43xp2fb 
       foreign key (market_id) 
       references market (market_id);

    alter table product_image 
       add constraint FK6oo0cvcdtb6qmwsga468uuukk 
       foreign key (product_id) 
       references product (product_id);

    alter table product_option 
       add constraint FK1chbk3kb4ib2qwwitild434g4 
       foreign key (option_group_id) 
       references product_option_group (option_group_id);

    alter table product_option_group 
       add constraint FKsifetwvwtdfqltwegvv0ijt28 
       foreign key (product_id) 
       references product (product_id);

    alter table product_variant 
       add constraint FKgrbbs9t374m9gg43l6tq1xwdj 
       foreign key (product_id) 
       references product (product_id);

    alter table recent_search 
       add constraint FKjwtiy8gf03joqr0a7pn1ioy9j 
       foreign key (user_id) 
       references users (user_id);

    alter table variant_option_map 
       add constraint FKipnrov51jqfx8emcvoiv7lwfj 
       foreign key (option_id) 
       references product_option (option_id);

    alter table variant_option_map 
       add constraint FK9jg4lc8rq7ys8gkfs08lreccy 
       foreign key (variant_id) 
       references product_variant (variant_id);

    create table admin_refresh_token (
        refresh_token_seq bigint not null auto_increment,
        refresh_token varchar(256) not null,
        admin_email varchar(512) not null,
        primary key (refresh_token_seq)
    ) engine=InnoDB;

    create table category (
        `order` integer,
        category_id bigint not null auto_increment,
        parent_id bigint,
        icon_url varchar(2048),
        name varchar(255) not null,
        primary key (category_id)
    ) engine=InnoDB;

    create table market (
        market_id bigint not null auto_increment,
        seller_id bigint not null,
        main_category varchar(100),
        market_image_url varchar(512),
        market_url varchar(512),
        sns_link_1 varchar(512),
        sns_link_2 varchar(512),
        sns_link_3 varchar(512),
        market_description varchar(1000),
        cs_number varchar(255) not null,
        market_name varchar(255) not null,
        market_image_status enum ('APPROVED','REJECTED','UNDER_REVIEW'),
        primary key (market_id)
    ) engine=InnoDB;

    create table market_follow (
        created_at datetime(6) not null,
        follow_id bigint not null auto_increment,
        market_id bigint not null,
        user_id bigint not null,
        primary key (follow_id)
    ) engine=InnoDB;

    create table product (
        delivery_estimated_days integer,
        delivery_fee integer,
        delivery_free_threshold integer,
        is_display bit not null,
        is_out_of_stock_forced bit not null,
        is_recommended bit not null,
        purchase_price integer,
        regular_price integer not null,
        sale_price integer not null,
        category_id bigint not null,
        created_at datetime(6) not null,
        market_id bigint not null,
        product_id bigint not null auto_increment,
        product_number varchar(50),
        delivery_type varchar(100),
        seller_product_code varchar(100),
        thumbnail_url varchar(2048),
        description text,
        name varchar(255) not null,
        product_notice json,
        tags json,
        primary key (product_id)
    ) engine=InnoDB;

    create table product_image (
        `order` integer not null,
        image_id bigint not null auto_increment,
        product_id bigint not null,
        url varchar(2048) not null,
        primary key (image_id)
    ) engine=InnoDB;

    create table product_option (
        option_group_id bigint not null,
        option_id bigint not null auto_increment,
        name varchar(255) not null,
        primary key (option_id)
    ) engine=InnoDB;

    create table product_option_group (
        option_group_id bigint not null auto_increment,
        product_id bigint not null,
        name varchar(255) not null,
        primary key (option_group_id)
    ) engine=InnoDB;

    create table product_variant (
        is_representative bit not null,
        regular_price integer not null,
        sale_price integer not null,
        stock integer not null,
        product_id bigint not null,
        variant_id bigint not null auto_increment,
        name varchar(255),
        primary key (variant_id)
    ) engine=InnoDB;

    create table recent_search (
        searched_at datetime(6) not null,
        user_id bigint not null,
        id binary(16) not null,
        term varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table seller (
        created_at datetime(6) not null,
        modified_at datetime(6) not null,
        seller_id bigint not null auto_increment,
        phone_number varchar(20),
        name varchar(64) not null,
        password varchar(128) not null,
        rejection_reason varchar(500),
        email varchar(512) not null,
        role_type enum ('ADMIN','GUEST','SELLER','USER') not null,
        status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (seller_id)
    ) engine=InnoDB;

    create table user_refresh_token (
        refresh_token_seq bigint not null auto_increment,
        user_id varchar(64) not null,
        refresh_token varchar(256) not null,
        primary key (refresh_token_seq)
    ) engine=InnoDB;

    create table users (
        email_verified_yn varchar(1) not null,
        marketing_agree bit,
        privacy_agree bit,
        service_agree bit,
        created_at datetime(6) not null,
        modified_at datetime(6) not null,
        user_id bigint not null auto_increment,
        birthday varchar(10),
        gender varchar(10),
        phone_number varchar(20),
        name varchar(64),
        username varchar(64) not null,
        nickname varchar(100) not null,
        password varchar(128) not null,
        email varchar(512) not null,
        profile_image_url varchar(512),
        provider_type enum ('APPLE','FACEBOOK','GOOGLE','KAKAO','LOCAL','NAVER') not null,
        role_type enum ('ADMIN','GUEST','SELLER','USER') not null,
        primary key (user_id)
    ) engine=InnoDB;

    create table variant_option_map (
        option_id bigint not null,
        variant_id bigint not null
    ) engine=InnoDB;

    alter table admin_refresh_token 
       add constraint UKlob6xik9nhf2v8qbso6ft7sec unique (admin_email);

    alter table market 
       add constraint UKagtyaitfan2mngy8ocdu9tle5 unique (seller_id);

    alter table market 
       add constraint UKqb8gnd8e5hl8gkmv4m9nxude3 unique (market_name);

    alter table market_follow 
       add constraint UK_MARKET_FOLLOW unique (user_id, market_id);

    alter table product 
       add constraint UKo9lr7abbchek72c2xu8x0g884 unique (product_number);

    alter table seller 
       add constraint UKcrgbovyy4gvgsum2yyb3fbfn7 unique (email);

    alter table user_refresh_token 
       add constraint UKqca3mjxv5a1egwmn4wnbplfkt unique (user_id);

    alter table users 
       add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

    alter table users 
       add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table category 
       add constraint FK2y94svpmqttx80mshyny85wqr 
       foreign key (parent_id) 
       references category (category_id);

    alter table market 
       add constraint FKkqb83ae8c9vy3aut4sjdouhhf 
       foreign key (seller_id) 
       references seller (seller_id);

    alter table market_follow 
       add constraint FKp6mcjw6xqvldkjfdj913wjgjk 
       foreign key (market_id) 
       references market (market_id);

    alter table market_follow 
       add constraint FKeopukl7njb1axvoktdbqrlpqk 
       foreign key (user_id) 
       references users (user_id);

    alter table product 
       add constraint FK1mtsbur82frn64de7balymq9s 
       foreign key (category_id) 
       references category (category_id);

    alter table product 
       add constraint FKnd0xf8hu7ixgw6u0do43xp2fb 
       foreign key (market_id) 
       references market (market_id);

    alter table product_image 
       add constraint FK6oo0cvcdtb6qmwsga468uuukk 
       foreign key (product_id) 
       references product (product_id);

    alter table product_option 
       add constraint FK1chbk3kb4ib2qwwitild434g4 
       foreign key (option_group_id) 
       references product_option_group (option_group_id);

    alter table product_option_group 
       add constraint FKsifetwvwtdfqltwegvv0ijt28 
       foreign key (product_id) 
       references product (product_id);

    alter table product_variant 
       add constraint FKgrbbs9t374m9gg43l6tq1xwdj 
       foreign key (product_id) 
       references product (product_id);

    alter table recent_search 
       add constraint FKjwtiy8gf03joqr0a7pn1ioy9j 
       foreign key (user_id) 
       references users (user_id);

    alter table variant_option_map 
       add constraint FKipnrov51jqfx8emcvoiv7lwfj 
       foreign key (option_id) 
       references product_option (option_id);

    alter table variant_option_map 
       add constraint FK9jg4lc8rq7ys8gkfs08lreccy 
       foreign key (variant_id) 
       references product_variant (variant_id);
