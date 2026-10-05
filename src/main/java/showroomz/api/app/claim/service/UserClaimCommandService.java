package showroomz.api.app.claim.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.app.claim.dto.UserClaimDto;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.repository.OrderClaimCollectionRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.service.OrderClaimService.Invoice;
import showroomz.domain.order.service.OrderClaimService.Item;
import showroomz.domain.order.service.OrderClaimService.RequestCommand;
import showroomz.domain.order.service.OrderClaimService.RequestResult;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 소비자 앱 반품·교환 요청 · 변경(앱 클레임 설계서 3-2 · 3-4 · 3-5). 여기는 앱의 입력 규칙(고를 수 있는 사유·택배사 ·
 * 글자 수 · 금액 확인)만 보고, 상태 전이와 잠금은 {@link OrderClaimService}(도메인)가 한다.
 */
@Service
@RequiredArgsConstructor
public class UserClaimCommandService {

    private final OrderClaimService claimService;
    private final UserClaimQueryService queryService;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimCollectionRepository collectionRepository;
    private final OrderProperties orderProperties;

    /**
     * 요청 — 수량은 받지 않는다(시안에 수량 선택이 없다). 고른 항목의 신청 가능 수량 전량으로 접수한다.
     * 같은 멱등키의 재요청은 새로 만들지 않고 기존 요청을 돌려준다.
     */
    public UserClaimDto.CreateResponse create(Long userId, UserClaimDto.CreateRequest request) {
        UserClaimQueryService.requireSupported(request.getType());
        if (request.getIdempotencyKey() != null) {
            Optional<OrderClaimCollection> existing = collectionRepository
                    .findByUserIdAndIdempotencyKey(userId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                List<OrderClaim> claims = claimRepository.findByCollectionId(existing.get().getId());
                return new UserClaimDto.CreateResponse(existing.get().getId(),
                        claims.stream().map(OrderClaim::getId).toList(),
                        claims.isEmpty() ? null : claims.get(0).getStatus());
            }
        }

        ClaimReason reason = request.getReasonCode();
        OrderProperties.Claim config = orderProperties.getClaim();
        if (!reason.isConsumerSelectable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "선택할 수 없는 사유입니다.");
        }
        String detail = request.getReasonDetail();
        if (detail != null && detail.length() > config.getDetailMaxLength()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "상세 내용은 " + config.getDetailMaxLength() + "자 이내로 입력해 주세요.");
        }
        // 사진은 브랜드 귀책 사유에서만 받는다 — 그 밖의 사유에 딸려 온 것은 무시한다(시안: 그때만 블록이 열린다).
        List<String> imageUrls = reason.getFeeBearer() == ClaimFeeBearer.SELLER && request.getImageUrls() != null
                ? request.getImageUrls() : List.of();
        if (imageUrls.size() > config.getPhotoMax()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "사진은 " + config.getPhotoMax() + "장까지 첨부할 수 있습니다.");
        }
        Invoice invoice = toInvoice(request.getInvoice());

        UserClaimQueryService.CreatePlan plan = queryService.planCreate(userId, request.getDeliveryGroupId(),
                request.getType(), reason.getFeeBearer());
        // 폼에서 본 금액과 지금 계산이 다르면 접수하지 않는다 — 앱이 귀책을 잘못 갈랐거나 오래된 폼이다.
        if (request.getExpectedFee() != null && request.getExpectedFee() != plan.fee()) {
            throw new BusinessException(ErrorCode.CLAIM_AMOUNT_CHANGED);
        }

        Map<Long, Integer> claimable = plan.claimable();
        if (claimable.isEmpty()) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_ELIGIBLE);
        }
        List<Item> items = new ArrayList<>();
        for (UserClaimDto.CreateItem item : request.getItems()) {
            Integer quantity = claimable.get(item.getOrderProductId());
            if (quantity == null) {
                throw new BusinessException(ErrorCode.ORDER_PRODUCT_NOT_FOUND); // 그 하위주문의 살아 있는 항목이 아니다
            }
            if (quantity < 1) {
                throw new BusinessException(ErrorCode.CLAIM_QUANTITY_EXCEEDED);
            }
            items.add(new Item(item.getOrderProductId(), quantity));
        }

        RequestResult result = claimService.request(new RequestCommand(userId, request.getDeliveryGroupId(), request.getType(),
                reason, detail, imageUrls, items, invoice, request.getIdempotencyKey()), LocalDateTime.now());
        return new UserClaimDto.CreateResponse(result.collectionId(), result.claimIds(),
                invoice != null ? ClaimStatus.COLLECTING : ClaimStatus.REQUESTED);
    }

    /**
     * 회수 송장 등록 · 수정 — 요청(박스) 단위로 적용된다. 회수 대기면 등록, 회수 중이면 수정이고(추적 이력이 없고 기한
     * 전일 때만 — 도메인이 본다) 그 밖의 단계에서는 받지 않는다.
     */
    public UserClaimDto.DetailResponse putCollectionInvoice(Long userId, Long claimId,
                                                           UserClaimDto.InvoiceRequest request) {
        OrderClaim claim = requireOwned(userId, claimId);
        Invoice invoice = toInvoice(request);
        Long collectionId = claim.getCollection().getId();
        LocalDateTime now = LocalDateTime.now();
        switch (claim.getStatus()) {
            case REQUESTED -> claimService.registerCollectionInvoice(collectionId, userId, invoice, now);
            case COLLECTING -> claimService.updateCollectionInvoice(collectionId, userId, invoice, now);
            default -> throw new BusinessException(ErrorCode.CLAIM_STATE_CHANGED);
        }
        return queryService.getDetail(userId, claimId);
    }

    /** 요청 철회 — 회수 송장을 넣기 전까지만. 항목은 배송완료로 돌아간다. */
    public UserClaimDto.WithdrawResponse withdraw(Long userId, Long claimId) {
        requireOwned(userId, claimId);
        claimService.withdraw(claimId, userId, LocalDateTime.now());
        return new UserClaimDto.WithdrawResponse(claimId, ClaimStatus.COMPLETED, ClaimResult.CANCELLED);
    }

    private OrderClaim requireOwned(Long userId, Long claimId) {
        return claimRepository.findOwnedByUser(claimId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
    }

    /** 소비자가 고를 수 있는 택배사만 — 형식 검증과 숫자 정제는 도메인이 한다. */
    private static Invoice toInvoice(UserClaimDto.InvoiceRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getCarrier() == null || !request.getCarrier().isConsumerSelectable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "택배사를 선택해 주세요.");
        }
        return new Invoice(request.getCarrier(), request.getTrackingNumber());
    }
}
