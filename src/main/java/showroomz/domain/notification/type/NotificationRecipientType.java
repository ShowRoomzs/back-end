package showroomz.domain.notification.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림함 수신자(1017 설계서 1-0 ①) — 행 모양은 같고 보관 기간 · 배지 규칙 · 푸시 여부만 다르다.
 *
 * <ul>
 *   <li>USER — {@code users.USER_ID} · 90일 · 배지 = 목록 진입 뒤 새로 온 수 · 앱 푸시</li>
 *   <li>SELLER — {@code market.market_id} · 30일 · 배지 = 안 읽은 수 · 인앱만</li>
 *   <li>CREATOR — {@code creator.creator_id} · 30일 · 배지 = 안 읽은 수 · 인앱만</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum NotificationRecipientType {
    USER(90),
    SELLER(30),
    CREATOR(30);

    /** 보관 · 노출 기간(일) — 목록은 이 기간 안의 행만 보이고 보관 배치가 지난 행을 지운다. */
    private final int retentionDays;
}
