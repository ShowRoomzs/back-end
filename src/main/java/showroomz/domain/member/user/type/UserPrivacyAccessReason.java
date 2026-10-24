package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소비자 본인확인 정보 열람(마스킹 해제) 사유 4종(ui-admin-13 M2 · 55 계획서 M3-3) — 정형 사유만이다. 「기타」를 두지 않는 것은 이 4종을
 * 벗어나는 열람이 정당한 경우가 없기 때문이고(해당하지 않으면 열람하지 않는다), 상세 사유도 받지 않는다(사유 축은 통계로 집계돼야 한다).
 * 인플루언서 사유({@code CreatorPrivacyAccessReason})는 정산 · 계약 맥락이라 값이 다르다.
 */
@Getter
@RequiredArgsConstructor
public enum UserPrivacyAccessReason {
    CS_RESPONSE("CS 응대"),
    DELIVERY_ACCIDENT("배송 사고"),
    PAYMENT_RETURN_DISPUTE("결제·반품 분쟁"),
    INVESTIGATION_COOPERATION("수사기관 협조");

    private final String label;
}
