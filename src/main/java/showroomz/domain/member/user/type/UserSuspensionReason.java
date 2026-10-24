package showroomz.domain.member.user.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import showroomz.domain.member.common.type.ReasonType;

/**
 * 소비자 정지 사유 6종(§25-4 · 51-6 설계서 0-4). 「기타」만 상세 사유가 필수이고, 상세 사유는 앱에 내리지 않는다(§51-6).
 *
 * <p>{@code appLabel}은 앱 배너 · 안내 시트 · 푸시에 쓰는 문구다 — 지금은 「정형 사유 그대로」(§51-6)라 어드민 라벨과 같고, 기타만 입력
 * 안내를 뺀다. 시안 예시 「운영정책 위반」처럼 포괄 문구로 덮기로 하면 이 열만 바꾼다(51-6 설계서 13절 #1).
 *
 * <p>{@link ReasonType}을 구현해 브랜드 · 인플루언서와 같은 조립식으로 모달 사유 목록을 낸다(55 계획서 M8). 소비자 정지는 지급과 무관해
 * {@code SuspensionReasonType}(지급 정지 여부)은 구현하지 않는다 — 목록의 {@code holdsPayout}은 null 이다.
 */
@Getter
@RequiredArgsConstructor
public enum UserSuspensionReason implements ReasonType {
    ABUSIVE_LANGUAGE("욕설·비방", "욕설·비방"),
    RETURN_ABUSE("반품 남용", "반품 남용"),
    FRAUDULENT_ORDER_SUSPECTED("부정 주문 의심", "부정 주문 의심"),
    PAYMENT_FRAUD_SUSPECTED("결제 도용 의심", "결제 도용 의심"),
    MULTIPLE_REPORTS("신고 다수 접수", "신고 다수 접수"),
    OTHER("기타(직접 입력)", "기타");

    private final String label;
    private final String appLabel;

    @Override
    public boolean isDetailRequired() {
        return this == OTHER;
    }
}
