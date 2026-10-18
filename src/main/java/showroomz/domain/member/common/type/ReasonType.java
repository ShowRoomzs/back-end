package showroomz.domain.member.common.type;

/**
 * 운영자가 고르는 정형 사유(정지 · 탈퇴 반려) — 회원 유형마다 값은 다르지만(기획 M1 · M3) 모양은 같다: 코드 · 라벨 · 「기타면 상세 필수」.
 * 사유 선택지 응답과 상세 필수 검사를 한 곳에서 처리하려고 둔다(정합 계획서 R1-1).
 */
public interface ReasonType {

    String name();

    String getLabel();

    /** 상세(직접 입력)가 필수인가 — 기타(OTHER)만. */
    boolean isDetailRequired();
}
