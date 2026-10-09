package showroomz.api.seller.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.service.ClaimStoragePolicy;
import showroomz.domain.order.type.ClaimOpenedBy;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.OrderBadgeTone;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.global.config.properties.OrderProperties;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 반품·교환 목록 행 조립(35 설계서 4-1) — 목록과 상세가 같은 메서드를 탄다. 여기서 조회하지 않는다 — 입력은 호출자가
 * 읽어 둔 것이다. 라벨 · 버튼 가능 여부 · 보관 기한 같은 파생값은 전부 서버가 만들어 내린다.
 */
@Component
@RequiredArgsConstructor
public class SellerClaimAssembler {

    /** 「보낼 상품·옵션」/「보관 중인 상품」을 말하는 단계. */
    private static final Set<ClaimStatus> SHIP_LABEL_STAGES = EnumSet.of(ClaimStatus.RESHIP_READY,
            ClaimStatus.RESHIPPING, ClaimStatus.REJECT_HOLD);

    private final ClaimStoragePolicy storagePolicy;
    private final OrderProperties orderProperties;

    /**
     * @param boxClaims      그 클레임과 같은 요청(박스)의 클레임 전부 — 묶음 머리 · 「N개 항목 중 M개 신청」
     * @param groupItems     그 하위주문의 항목 전부 — 행 확장
     * @param consumerPhotos 소비자 첨부 장수
     * @param sellerPhotos   브랜드 증빙 장수
     * @param charges        그 요청(박스)의 소비자 추가 결제 전부 — 「재발송비」 열
     */
    public SellerClaimListItem toListItem(OrderClaim claim, List<OrderClaim> boxClaims, List<OrderProduct> groupItems,
                                          int consumerPhotos, int sellerPhotos, List<OrderClaimCharge> charges,
                                          LocalDateTime now) {
        OrderProduct product = claim.getOrderProduct();
        OrderClaimCollection collection = claim.getCollection();
        ClaimStatus status = claim.getStatus();
        boolean rejected = claim.getRejectedAt() != null;
        boolean reshipStage = status == ClaimStatus.RESHIP_READY || status == ClaimStatus.RESHIPPING
                || claim.getReshipTrackingNumber() != null;

        return new SellerClaimListItem(
                claim.getId(),
                claim.claimNumber(),
                claim.getType(),
                claim.getType().getLabel(),
                claim.getDeliveryGroup().getOrder().getRecipientName(),
                product.getProductName(),
                product.getOptionName(),
                claim.getExchangeOptionName(),
                join(join(product.getProductName(), product.getOptionName(), " "), claim.getExchangeOptionName(), " → "),
                // 거절 건에 새 옵션을 보내는 사고가 나지 않게 「보낼 물건」을 서버가 정한다 — 교환 재발송은 새 옵션, 거절은 원래 옵션.
                !SHIP_LABEL_STAGES.contains(status) ? null
                        : join(product.getProductName(),
                        rejected || claim.getExchangeOptionName() == null ? product.getOptionName()
                                : claim.getExchangeOptionName(), " "),
                claim.getQuantity(),
                claim.getReasonCode(),
                claim.getReasonCode().getLabel(),
                consumerPhotos,
                claim.getOpenedBy() == ClaimOpenedBy.OPERATOR,
                claim.getOpenReason(),
                status,
                status.getLabel(),
                ClaimTab.stageOf(status),
                OrderBadgeTone.NEUTRAL,
                claim.getRequestedAt(),
                claim.getStageEnteredAt(),
                (int) Math.max(0, ChronoUnit.DAYS.between(claim.getStageEnteredAt().toLocalDate(), now.toLocalDate())),
                isOverdue(claim, now),
                new SellerClaimListItem.Collection(
                        collection.getId(),
                        boxClaims.size(),
                        boxClaims.isEmpty() ? claim.claimNumber() : boxClaims.get(0).claimNumber(),
                        collection.getCarrier(),
                        collection.getCarrier() == null ? null : collection.getCarrier().getLabel(),
                        collection.getTrackingNumber(),
                        collection.getLastTrackingLabel(),
                        collection.getLastTrackingAt(),
                        collection.getArrivedAt()),
                claim.getReceivedAt(),
                claim.getInspectDueAt(),
                !reshipStage ? null : rejected ? "REJECT_RETURN" : "EXCHANGE",
                !reshipStage ? null : rejected ? "반려 반송" : "교환 재발송",
                claim.getReshipCarrier(),
                claim.getReshipCarrier() == null ? null : claim.getReshipCarrier().getLabel(),
                claim.getReshipTrackingNumber(),
                reshipFee(claim, charges),
                claim.getRejectReasonCode() == null ? null : claim.getRejectReasonCode().getLabel(),
                sellerPhotos,
                claim.getRejectedAt(),
                status != ClaimStatus.REJECT_HOLD ? null : new SellerClaimListItem.Storage(
                        claim.getNoticeCount(),
                        claim.getLastNoticeAt(),
                        storagePolicy.storageDueAt(claim.getNoticeCount(), claim.getLastNoticeAt()),
                        storagePolicy.phase(claim.getNoticeCount(), claim.getLastNoticeAt(), now)),
                outcome(claim),
                amount(claim, product),
                claim.getCompletedAt(),
                new SellerClaimListItem.Actions(canConfirmReceipt(status), status == ClaimStatus.RECEIVED,
                        status == ClaimStatus.RESHIP_READY),
                orderItems(boxClaims, groupItems),
                "%d개 항목 중 %d개 신청".formatted(groupItems.size(), boxClaims.size()));
    }

    /** 추적이 꺼져 있는 동안에는 도착 감지가 없다 — 설정이 켜져 있으면 회수 중에서도 입고 확인을 받는다(35 설계서 0-7). */
    boolean canConfirmReceipt(ClaimStatus status) {
        return status == ClaimStatus.ARRIVED
                || (status == ClaimStatus.COLLECTING && orderProperties.getClaim().isReceiveBeforeArrival());
    }

    /** 기한 ①(회수 대기 방치 — 소비자 미발송) · 기한 ②(검수 — 브랜드 귀책). 「경과」 열과 섞지 않는다. */
    static boolean isOverdue(OrderClaim claim, LocalDateTime now) {
        return (claim.getStatus() == ClaimStatus.REQUESTED && now.isAfter(claim.getCollectDueAt()))
                || (claim.getStatus() == ClaimStatus.RECEIVED && claim.getInspectDueAt() != null
                && now.isAfter(claim.getInspectDueAt()));
    }

    /** 반품 환불 예정액의 산정 근거 문구(1-8). */
    String refundBasisLabel(OrderClaimCollection collection) {
        if (collection.getFeeBearer() == ClaimFeeBearer.SELLER) {
            return "브랜드 부담 사유 — 배송비 차감 없음";
        }
        return collection.getReturnDeduction() > 0 ? "상품 금액 − 최초 배송비" : "상품 금액(배송비 차감 없음)";
    }

    /**
     * 재발송비(결정 14) — 반려 건은 반려 재발송 배송비({@code REJECT_RESHIP}), 반려되지 않은 교환은 요청 때 낸 선결제분
     * ({@code EXCHANGE_RESHIP}). 같은 종류가 여럿이면 마지막 청구가 지금 값이다. 청구가 없으면 null.
     */
    static SellerClaimListItem.ReshipFee reshipFee(OrderClaim claim, List<OrderClaimCharge> charges) {
        ClaimChargeType wanted = claim.getRejectedAt() != null ? ClaimChargeType.REJECT_RESHIP
                : claim.getType() == ClaimType.EXCHANGE ? ClaimChargeType.EXCHANGE_RESHIP : null;
        if (wanted == null) {
            return null;
        }
        return charges.stream()
                .filter(charge -> charge.getType() == wanted)
                .reduce((first, second) -> second)
                .map(charge -> new SellerClaimListItem.ReshipFee(
                        charge.getAmount() == null ? 0 : charge.getAmount(),
                        charge.getStatus(), charge.getStatus().getLabel()))
                .orElse(null);
    }

    private static SellerClaimListItem.Outcome outcome(OrderClaim claim) {
        return switch (claim.getStatus()) {
            case REFUND_PENDING -> new SellerClaimListItem.Outcome("REFUND_PENDING", "환불 대기", false);
            case RESHIPPING -> new SellerClaimListItem.Outcome("RESHIPPING", "재발송 중", false);
            case COMPLETED -> {
                ClaimResult result = claim.getResult();
                yield result == null ? null
                        : new SellerClaimListItem.Outcome(result.name(), result.getLabel(), true);
            }
            default -> null;
        };
    }

    /** 완료 탭 「금액」 — 환불로 가는 반품만. 차감은 요청 단위라 항목 금액에 얹지 않는다(상세가 따로 말한다). */
    private static Long amount(OrderClaim claim, OrderProduct product) {
        boolean refund = claim.getType() == ClaimType.RETURN
                && (claim.getStatus() == ClaimStatus.REFUND_PENDING || claim.getResult() == ClaimResult.REFUNDED);
        if (!refund) {
            return null;
        }
        return claim.getRefundedAmount() != null ? claim.getRefundedAmount().longValue()
                : (long) product.getPrice() * claim.getQuantity();
    }

    /** 그 하위주문의 항목 전체 — 신청분과 살아 있는 분이 갈려야 한다(§35-2). */
    private static List<SellerClaimListItem.OrderItem> orderItems(List<OrderClaim> boxClaims,
                                                                  List<OrderProduct> groupItems) {
        Map<Long, OrderClaim> claimByProduct = boxClaims.stream()
                .collect(Collectors.toMap(c -> c.getOrderProduct().getId(), c -> c, (a, b) -> a));
        return groupItems.stream().map(item -> {
            OrderClaim boxClaim = claimByProduct.get(item.getId());
            String label = item.getStatus() == OrderProductStatus.CANCELLED ? "취소"
                    : item.getStatus() == OrderProductStatus.RETURNED ? "반품"
                    : boxClaim != null && boxClaim.isOpen() ? boxClaim.getType().getLabel() + " 신청"
                    : item.getStatus() == OrderProductStatus.PURCHASE_CONFIRMED ? "구매확정"
                    : "배송완료";
            return new SellerClaimListItem.OrderItem(item.getProductName(), item.getOptionName(), item.getQuantity(),
                    boxClaim == null ? 0 : boxClaim.getQuantity(), item.getPrice(), label);
        }).toList();
    }

    private static String join(String left, String right, String separator) {
        return right == null || right.isBlank() ? left : left + separator + right;
    }
}
