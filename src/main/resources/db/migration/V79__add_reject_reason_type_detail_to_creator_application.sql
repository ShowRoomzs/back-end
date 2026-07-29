alter table creator_application
    add column reject_reason_type varchar(50) null comment '반려 사유 유형',
    add column reject_reason_detail varchar(1000) null comment '반려 상세 사유';
