package showroomz.domain.member.creator.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 본인확인 정보 마스킹 해제 사유 4종(§53-2 · M4) — 정형 사유만 · 상세 사유 · 기타 없음(사유 축은 통계로 집계돼야 한다 · §25-2).
 * 소비자 사유(배송 사고 · 결제 · 반품 분쟁)를 인플루언서 맥락(정산 · 원천징수 · 계약 · 분쟁)으로 바꿨다.
 */
@Getter
@RequiredArgsConstructor
public enum CreatorPrivacyAccessReason {
    CS_RESPONSE("CS 응대"),
    SETTLEMENT_WITHHOLDING_CHECK("정산·원천징수 확인"),
    CONTRACT_DISPUTE("계약·분쟁 대응"),
    INVESTIGATION_COOPERATION("수사기관 협조");

    private final String label;
}
