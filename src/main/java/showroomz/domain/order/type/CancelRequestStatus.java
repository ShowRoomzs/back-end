package showroomz.domain.order.type;

/** 취소 요청 상태 — PENDING 행의 존재가 곧 「취소 요청 탭」 오버레이다(34 설계서 0-2). */
public enum CancelRequestStatus {
    PENDING, APPROVED, REJECTED
}
