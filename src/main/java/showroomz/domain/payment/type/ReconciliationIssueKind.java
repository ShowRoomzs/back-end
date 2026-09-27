package showroomz.domain.payment.type;

/** 일일 대사가 발견하는 어긋남 4종(결제 계획서 4-8). 앞의 둘은 confirm 재호출로 자동 수렴을 시도하고, 뒤의 둘은 사람이 본다. */
public enum ReconciliationIssueKind {
    PORTONE_PAID_NOT_OURS,
    PORTONE_CANCELLED_NOT_OURS,
    OURS_PAID_NOT_PORTONE,
    AMOUNT_DIFF
}
