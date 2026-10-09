package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.market.entity.Market;

import java.time.LocalDateTime;

/**
 * 발송 기한 = <b>공구 마감 시각 + 브랜드가 정한 N영업일</b>(1009 기획 수정본 1절 · 거래 관리 결정 3 · 15).
 *
 * <ul>
 *   <li>N 은 브랜드 설정({@code market.shipping_lead_days}) · 기본 3 · 1~7. <b>주문 시점 값으로 고정</b>한다
 *       ({@code order_delivery_group.ship_due_business_days}) — 브랜드가 뒤에 바꿔도 접수된 주문은 그대로다.</li>
 *   <li>공구 진행 중 결제된 주문은 마감 전까지 기한이 없다 — 공구가 종결되는 순간 일괄 계산한다
 *       ({@code OrderShipDueAssigner}). 조기 마감 · 연장 · 중단으로 마감 시각이 바뀌므로 결제 때 미리 계산하지 않는다.</li>
 *   <li>영업일 계산은 클레임 기한과 같은 {@link BusinessDayCalculator}다 — 기산 시각이 비영업일이면 다음 영업일을
 *       기산일로 보고 + N영업일, 그 날의 끝(23:59:59). 금요일 마감 + 3 = 수요일 끝 · 토요일 마감 + 3 = 목요일 끝.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ShipDuePolicy {

    public static final int DEFAULT_BUSINESS_DAYS = 3;
    public static final int MIN_BUSINESS_DAYS = 1;
    public static final int MAX_BUSINESS_DAYS = 7;

    private final BusinessDayCalculator businessDayCalculator;

    /** 브랜드 설정 N — 비었으면 기본 3, 범위 밖이면 1~7로 자른다(기존 행 방어 · 입력은 1~7로 검증한다). */
    public static int businessDaysOf(Market market) {
        Integer configured = market == null ? null : market.getShippingLeadDays();
        if (configured == null) {
            return DEFAULT_BUSINESS_DAYS;
        }
        return Math.max(MIN_BUSINESS_DAYS, Math.min(MAX_BUSINESS_DAYS, configured));
    }

    public LocalDateTime dueAt(LocalDateTime groupBuyEndedAt, int businessDays) {
        return businessDayCalculator.dueAt(groupBuyEndedAt, businessDays);
    }

    /** 소비자 상품 상세 · 결제 화면의 고지 문구(전자상거래법 제15조① 공급 시기 약정). */
    public static String noticeText(int businessDays) {
        return "공구 마감 후 " + businessDays + "영업일 이내 발송 (주말·공휴일 제외)";
    }
}
