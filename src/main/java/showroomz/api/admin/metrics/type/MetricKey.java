package showroomz.api.admin.metrics.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운영 지표 정의(1017 설계서 7-2) — 라벨 · 값 종류 · <b>증감 방향의 의미</b>({@code betterWhen})를 지표가 든다. 탭 배지
 * 「나빠진 지표 수」를 서버가 센다.
 */
@Getter
@RequiredArgsConstructor
public enum MetricKey {
    // 핵심 · 거래
    GMV("공구 거래액", ValueType.AMOUNT, BetterWhen.UP, "결제 시점 · 취소 포함"),
    ORDERS("주문", ValueType.COUNT, BetterWhen.UP, "결제 완료 공구 주문"),
    GMV_MEDIAN("공구당 거래액(중앙값)", ValueType.AMOUNT, BetterWhen.UP, "기간 중 판매된 공구별 거래액의 중앙값"),
    FEE_REVENUE("플랫폼 수수료 수익(예상)", ValueType.AMOUNT, BetterWhen.UP, "거래액 × 표준 수수료율 · 예상 · 베타 0%"),
    RETURN_EXCHANGE_RATE("반품 · 교환율", ValueType.RATE, BetterWhen.DOWN, "반품 · 교환 접수 ÷ 배송완료"),
    // 공급
    ACTIVE_CREATORS("활성 인플루언서", ValueType.COUNT, BetterWhen.UP, "기간 중 결제가 1건 이상 있는 공구의 인플루언서"),
    ACTIVE_BRANDS("활성 브랜드", ValueType.COUNT, BetterWhen.UP, "기간 중 결제가 1건 이상 있는 공구의 브랜드"),
    CREATOR_REGROUPBUY_RATE("인플루언서 재공구율", ValueType.RATE, BetterWhen.UP, "종료 공구 중 60일 안에 다음 공구를 시작한 비율"),
    BRAND_RECONTRACT_RATE("브랜드 재계약률", ValueType.RATE, BetterWhen.UP, "기간 중 체결한 브랜드 중 이전 체결이 있는 비율"),
    CONNECTION_CONVERSION("연결 → 체결 전환율", ValueType.RATE, BetterWhen.UP, "기간 중 요청된 연결 중 그 연결로 체결된 비율"),
    CONCLUDE_TO_OPEN_DAYS("체결 → 오픈 소요(중앙값)", ValueType.DAYS, BetterWhen.DOWN, "기간 중 오픈한 공구"),
    NEW_ONBOARDING("신규 입점", ValueType.COUNT, BetterWhen.NONE, "입점 승인 — 브랜드 + 인플루언서"),
    CONTRACTS_CONCLUDED("계약 체결", ValueType.COUNT, BetterWhen.UP, "체결 시각 기준"),
    // 공구
    GROUP_BUYS_RUN("진행한 공구", ValueType.COUNT, BetterWhen.UP, "기간 중 판매일이 있는 공구"),
    MIN_QUANTITY_RATE("최소 물량 달성률", ValueType.RATE, BetterWhen.UP, "기간 중 종료된 공구 중 판매 수량(취소 제외) ≥ 최소 준비 물량"),
    BRAND_ROI("브랜드 ROI(중앙값)", ValueType.RATIO, BetterWhen.UP, "거래액 ÷ (리워드 + 고정 지급비) · 정산 전이면 리워드율로 추정"),
    SUSPENSIONS("중단", ValueType.COUNT, BetterWhen.DOWN, "승인된 중단 요청 + 집행된 직권 중단"),
    OPEN_REVIEW_DAYS("오픈 승인 소요", ValueType.DAYS, BetterWhen.DOWN, "제출 → 승인 영업일 평균"),
    SELLING_GROUP_BUYS("판매 중인 공구", ValueType.COUNT, BetterWhen.UP, "버킷 시작 시점에 판매 중인 공구 수"),
    // 수요
    BUYERS("구매 소비자", ValueType.COUNT, BetterWhen.UP, "결제한 소비자(중복 제거)"),
    BUYERS_MEDIAN("공구당 구매 소비자(중앙값)", ValueType.COUNT, BetterWhen.UP, "공구별 구매 소비자 수의 중앙값"),
    FOLLOWER_CONVERSION("팔로워 → 구매 전환율", ValueType.RATE, BetterWhen.UP, "결제 전에 그 인플루언서를 팔로우한 구매자 ÷ 진행 쇼룸 팔로워"),
    REPURCHASE_RATE("재구매율", ValueType.RATE, BetterWhen.UP, "기간 구매자 중 이전 결제 이력이 있는 비율"),
    NEW_CONSUMERS("신규 소비자", ValueType.COUNT, BetterWhen.UP, "가입 — 경로별"),
    CONSUMER_WITHDRAWALS("소비자 탈퇴", ValueType.COUNT, BetterWhen.DOWN, "상태 이력 기준"),
    // 운영
    BRAND_FAULT_RETURN_RATE("브랜드 귀책 반품률", ValueType.RATE, BetterWhen.DOWN, "브랜드 귀책 반품 ÷ 배송완료"),
    ADJUSTMENT_REQUEST_RATE("정산 조정 요청률", ValueType.RATE, BetterWhen.DOWN, "조정 협의 개시 ÷ 정산 생성"),
    POST_REPORT_RATE("게시물 신고율", ValueType.RATE, BetterWhen.DOWN, "신고 ÷ 공개 게시물"),
    PROCESSED("처리 완료", ValueType.COUNT, BetterWhen.UP, "처리 시각이 있는 큐의 처리 건수"),
    SLA_RATE("SLA 준수율", ValueType.RATE, BetterWhen.UP, "1:1 문의 24h · 오픈 승인 3영업일 · 입점 심사 3영업일 · 변경 요청 48h"),
    INQUIRY_FIRST_ANSWER_HOURS("1:1 문의 첫 답변", ValueType.HOURS, BetterWhen.DOWN, "접수 → 답변 평균(시간)"),
    REFUND_FAILURES("환불 실패", ValueType.COUNT, BetterWhen.DOWN, "실패 시각 기준");

    private final String label;
    private final ValueType valueType;
    private final BetterWhen betterWhen;
    private final String definition;

    public enum ValueType { COUNT, AMOUNT, RATE, RATIO, DAYS, HOURS }

    public enum BetterWhen { UP, DOWN, NONE }
}
