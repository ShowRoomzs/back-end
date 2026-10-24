package showroomz.domain.performance.service;

import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.performance.type.PerformanceCancelKind;
import showroomz.domain.performance.type.PerformanceExceptionKind;
import showroomz.domain.performance.type.PerformanceSource;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.settlement.entity.Settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 공구 1건의 성과 <b>원천값</b>(48 파트너 설계서 7-2) — 파트너 12 · 스튜디오 10 이 함께 읽는다.
 *
 * <p>라벨 · 값 상태(예정 · 정산 후 확정 · 협의 중) · 문장 · 원가는 들지 않는다 — 서피스 어셈블러의 일이다. 돈은 {@link #settlement()}
 * 정산 행이 정본이고(§41-4), 정산이 없으면({@link #projected()}) 판매 블록은 같은 분류기로 주문 테이블에서 낸 <b>예정</b>값이다.
 *
 * @param projected       정산 행이 없다 — 판매 · 소비자 블록이 「예정」이다
 * @param settlement      정산 행(엔티티 그대로) · 집계 중이면 null
 * @param fixedFeeAmount  계약 고정 지급비(없으면 0) — 정산 대상 아님 · 브랜드 직접 지급
 * @param closure         미처리 하위주문 — 집계 중일 때만 · 아니면 null
 * @param post            공구 게시물 지표 — 게시물이 없으면 null
 * @param products        계약 상품별 — {@code readRows} 에서는 빈 목록
 * @param delivery        배송 운영 품질(파트너 전용) — {@code readRows} 에서는 null
 * @param breakdown       취소 · 반품 · 배송 예외 분해(파트너 전용) — {@code readRows} 에서는 null
 */
public record PerformanceFacts(
        Long groupBuyId,
        Long marketId,
        Long creatorId,
        Long contractId,
        PerformanceStatus status,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        boolean projected,
        Settlement settlement,
        long fixedFeeAmount,
        Closure closure,
        Traffic traffic,
        PostFacts post,
        Orders orders,
        List<ProductLine> products,
        List<Buyer> buyers,
        Followers followers,
        Delivery delivery,
        Breakdown breakdown
) {

    /**
     * @param unclosedCount    종결되지 않은 하위주문 수
     * @param openReturnCount  결과가 나지 않은 반품 클레임 수(D2 「미처리 반품 N건」)
     */
    public record Closure(int unclosedCount, long openReturnCount, List<GroupBuySalesReader.UnclosedStage> stages) {
    }

    /**
     * @param visitorCount         쇼룸 방문(명) — 공구 기간 {@code DISTINCT visitor_key}
     * @param visitCount           방문(세션 · 30분)
     * @param visitorsBySource     사람당 첫 방문 소스 — 4종 합 = {@code visitorCount}
     * @param visitorFollowerCount 방문자 중 첫 방문 시각 팔로워(명) · 비로그인은 비팔로워
     * @param productViewCount     이 공구 상품 상세 조회(회)
     * @param productViewerCount   상품 상세 고유 조회자(명) — 상품 간 중복 제거
     */
    public record Traffic(long visitorCount, long visitCount, Map<PerformanceSource, Long> visitorsBySource,
                          long visitorFollowerCount, long productViewCount, long productViewerCount) {
    }

    /**
     * @param followerViewerCount 고유 조회자 중 첫 조회 시각 팔로워(명)
     * @param productViewCount    게시물 → 상품 상세 이동(회) · 라스트 터치 귀속
     * @param orderCount          게시물 → 주문(결제 완료 하위주문 건) · 결제 시 라스트 터치 귀속
     */
    public record PostFacts(Long postId, long impressionCount, long uniqueViewerCount, long followerViewerCount,
                            long productViewCount, long orderCount, long likeCount) {
    }

    /**
     * 건 수의 단위는 하위주문이다(0-7) — 주문 = 구매확정 + 취소 + 반품 + 배송 예외.
     *
     * @param shippedCount           송장이 등록된 적 있는 하위주문(누적 도달)
     * @param deliveredCount         배송완료된 적 있는 하위주문(누적 도달)
     * @param confirmedSalesAmount   확정 거래액 — 정산이 있으면 그 값 · 없으면 분류기 반영액 합(예정)
     * @param contractRewardAmount   계약 기준 판매 리워드(합의 전) = 항목 리워드 합
     * @param orderFollowerCount     결제 시각 팔로워의 하위주문(건)
     * @param confirmedFollowerCount 그중 구매확정(건)
     */
    public record Orders(int orderCount, int confirmedCount, int cancelledCount, int returnedCount,
                         int deliveryExceptionCount, int shippedCount, int deliveredCount, long orderedQuantity,
                         long confirmedQuantity, long confirmedSalesAmount, long contractRewardAmount,
                         int orderFollowerCount, int confirmedFollowerCount) {
    }

    /**
     * 계약 상품 1개 — 팔리지 않은 상품도 행이 있다.
     *
     * @param orderCount       이 상품이 든 하위주문(건)
     * @param returnedQuantity 반품 수량(전량 반품 항목 수량 + 부분 반품 수량) — 취소 · 배송 예외는 넣지 않는다
     */
    public record ProductLine(Long contractItemId, Long productId, String productName, BigDecimal rewardRate,
                              long detailViewCount, long detailViewerCount, int orderCount, long orderedQuantity,
                              long confirmedQuantity, long confirmedAmount, long rewardAmount, long returnedQuantity) {
    }

    /**
     * 구매확정 소비자 1명(중복 제거) — 분포 · 비율에만 쓰고 식별값은 응답에 싣지 않는다(§47-2).
     *
     * @param followerAtPurchase 첫 구매확정 주문의 결제 시각에 팔로워였나
     * @param repeatInBrand      이 공구 시작 전 같은 브랜드의 다른 공구에서 구매확정(파트너 재구매)
     * @param repeatWithCreator  이 공구 시작 전 같은 인플루언서의 다른 공구에서 구매확정(스튜디오 재구매)
     */
    public record Buyer(Long userId, String gender, String birthday, boolean followerAtPurchase,
                        boolean repeatInBrand, boolean repeatWithCreator) {
    }

    /** 쇼룸 팔로워 — 종료 시점 총량 · 공구 기간 증감(FOLLOW − UNFOLLOW). */
    public record Followers(int countAtEnd, int delta) {
    }

    /**
     * @param shipDueTrackedCount 발송기한이 있는 발송 건 · {@code shippedOnTimeCount} 의 분모
     * @param avgShipDays         결제완료 → 송장 등록 평균(일 · 소수 1자리) · 발송 0 이면 null
     * @param deliveryClaimCount  반송 · 분실 하위주문(건)
     * @param avgInquiryHours     상품 문의 답변까지 평균(시간 · 소수 1자리) · 답변 0 이면 null
     */
    public record Delivery(int shippedCount, int shipDueTrackedCount, int shippedOnTimeCount, BigDecimal avgShipDays,
                           int deliveryClaimCount, int answeredInquiryCount, BigDecimal avgInquiryHours) {
    }

    /** 분해 한 줄 — 개수(항목 수량) · 금액(차감 몫) · 건수(그 분류로 접힌 하위주문). */
    public record BreakdownLine(long quantity, long amount, int groupCount) {

        public static final BreakdownLine EMPTY = new BreakdownLine(0, 0, 0);

        public static BreakdownLine sum(BreakdownLine a, BreakdownLine b) {
            return new BreakdownLine(a.quantity + b.quantity, a.amount + b.amount, a.groupCount + b.groupCount);
        }
    }

    /**
     * 취소 · 반품 · 배송 예외 분해. 부분 반품 · 부분 취소(구매확정 하위주문 안의 항목)는 개수 · 금액에는 들고 건수에는 들지 않는다 —
     * 건수 검산식(주문 = 확정 + 취소 + 반품 + 예외)을 지키기 위해서다.
     */
    public record Breakdown(Map<PerformanceCancelKind, BreakdownLine> cancel,
                            Map<ClaimReason, BreakdownLine> returned,
                            Map<PerformanceExceptionKind, BreakdownLine> deliveryException) {

        public long deductionTotal() {
            return sum(cancel) + sum(returned) + sum(deliveryException);
        }

        private static long sum(Map<?, BreakdownLine> lines) {
            return lines.values().stream().mapToLong(BreakdownLine::amount).sum();
        }
    }
}
