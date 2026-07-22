alter table creator_application
    add column account_id varchar(100) not null default '';

alter table creator
    add column account_id varchar(100) not null default '';
