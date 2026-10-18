package showroomz.domain.member.common.type;

/**
 * 정지 사유 — 지급 정지 여부는 <b>사유가 정한다</b>(§51-1 · 운영자 스위치가 아니다). 브랜드 · 인플루언서 사유 enum 이 구현한다.
 */
public interface SuspensionReasonType extends ReasonType {

    /** 그 회원 몫 지급을 정지하는가 — 계좌 · 사업자 확인이 필요한 사유만. */
    boolean isHoldsPayout();
}
