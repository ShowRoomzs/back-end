package showroomz.domain.settlement.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 정산 명세 xlsx 열(44 어드민 설계서 7-8 · 파트너 2-5 · 스튜디오 4-1) — 컬럼 구성은 §46 ③ 미정이라 <b>이 enum 한 곳</b>에 모은다.
 * 바뀌면 열만 뺀다. 서피스별 열 집합은 {@link #forSurface} — 소비자 열은 어드민 · 파트너 명세에만 있고 스튜디오에는 없다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementStatementColumn {
    SETTLEMENT_NUMBER("정산번호"),
    ORDER_NUMBER("주문번호"),
    SUB_ORDER_NUMBER("하위주문번호"),
    CONSUMER("주문자"),
    PRODUCT("상품"),
    OPTION("옵션"),
    QUANTITY("수량"),
    SETTLED_QUANTITY("반영 수량"),
    UNIT_PRICE("단가"),
    PAID_AMOUNT("결제금액"),
    STATUS("상태"),
    SETTLED_AMOUNT("정산 반영액"),
    REWARD_RATE("리워드율(%)"),
    REWARD_AMOUNT("리워드");

    /** 소비자 식별 정보 — 스튜디오 명세에는 내리지 않는다(스튜디오 설계서 1절). */
    private static final Set<SettlementStatementColumn> CONSUMER_COLUMNS = EnumSet.of(CONSUMER, SUB_ORDER_NUMBER);
    /** 스튜디오에 없는 브랜드 쪽 열 — 단가 · 정산번호는 파일명으로 충분하다. */
    private static final Set<SettlementStatementColumn> STUDIO_EXCLUDED = EnumSet.of(SETTLEMENT_NUMBER, UNIT_PRICE);

    private final String header;

    public enum Surface { ADMIN, PARTNER, STUDIO }

    public static List<SettlementStatementColumn> forSurface(Surface surface) {
        if (surface == Surface.STUDIO) {
            return Arrays.stream(values())
                    .filter(column -> !CONSUMER_COLUMNS.contains(column) && !STUDIO_EXCLUDED.contains(column))
                    .toList();
        }
        return List.of(values());
    }
}
