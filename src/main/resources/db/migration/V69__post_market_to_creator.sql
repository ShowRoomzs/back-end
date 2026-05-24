alter table post
    drop foreign key FKl8xkjvcym3fq9lervw5tm6do9;

alter table post
    change column market_id creator_id bigint not null;

alter table post
    add constraint FK_post_creator_id
    foreign key (creator_id)
    references creator (creator_id);
