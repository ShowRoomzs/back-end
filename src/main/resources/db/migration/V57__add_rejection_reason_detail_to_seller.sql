-- 반려 상세 사유 (선택)
alter table seller add column rejection_reason_detail varchar(1000) null comment '반려 상세 사유';