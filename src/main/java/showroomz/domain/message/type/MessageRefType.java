package showroomz.domain.message.type;

/** 카드가 가리키는 객체 — 액션 상태는 스냅샷이 아니라 이 객체에서 읽는다(36 설계 0-3). */
public enum MessageRefType {
    CONTRACT_RESEND_REQUEST,
    CONTRACT
}
