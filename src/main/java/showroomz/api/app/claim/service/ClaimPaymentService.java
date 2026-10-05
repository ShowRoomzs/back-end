package showroomz.api.app.claim.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.app.claim.dto.UserClaimDto;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.domain.order.entity.OrderClaim;
import showroomz.domain.order.entity.OrderClaimCharge;
import showroomz.domain.order.entity.OrderClaimCollection;
import showroomz.domain.order.entity.OrderClaimPayment;
import showroomz.domain.order.repository.OrderClaimChargeRepository;
import showroomz.domain.order.repository.OrderClaimPaymentRepository;
import showroomz.domain.order.repository.OrderClaimRepository;
import showroomz.domain.order.service.OrderClaimService;
import showroomz.domain.order.type.ClaimChargeType;
import showroomz.domain.order.type.ClaimPaymentStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneCancelResult;
import showroomz.global.payment.portone.PortOneCodes;
import showroomz.global.payment.portone.PortOnePayment;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.global.payment.portone.PortOneStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 클레임 재발송 배송비 결제(앱 클레임 설계서 1-5 · 3-3 · 6절) — 주문 결제의 축소판이다. 포트원 호출부만 같이 쓰고,
 * 주문 결제의 상태 기계(주문 PAID 전이 · 재고 · 만료)는 건드리지 않는다.
 *
 * <p>규칙은 주문 결제와 같다 — 포트원 호출은 <b>트랜잭션 밖</b>, 상태 전이는 조건부 UPDATE, 앱이 보낸 결제창 결과는
 * 믿지 않고 포트원 조회 결과만 믿는다. 확정은 멱등이다.
 * 받을 수 없는 결제(금액 불일치 · 요청이 이미 사라짐 · 이미 다른 시도로 정산됨)는 <b>자동 취소</b>한다.
 */
@Slf4j
@Service
public class ClaimPaymentService {

    private static final int BATCH_LIMIT = 200;
    private static final String EXCHANGE_ORDER_NAME = "교환 재발송 배송비";
    private static final String REJECT_ORDER_NAME = "반려 상품 재발송 배송비";

    private final OrderClaimPaymentRepository paymentRepository;
    private final OrderClaimChargeRepository chargeRepository;
    private final OrderClaimRepository claimRepository;
    private final OrderClaimService claimService;
    private final PortOnePaymentGateway gateway;
    private final OrderProperties orderProperties;
    private final TransactionTemplate transaction;

    public ClaimPaymentService(OrderClaimPaymentRepository paymentRepository,
                               OrderClaimChargeRepository chargeRepository, OrderClaimRepository claimRepository,
                               OrderClaimService claimService, PortOnePaymentGateway gateway,
                               OrderProperties orderProperties, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.chargeRepository = chargeRepository;
        this.claimRepository = claimRepository;
        this.claimService = claimService;
        this.gateway = gateway;
        this.orderProperties = orderProperties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    private record Opened(String paymentId, int amount, String channelKey, PaymentMethod method,
                          CardIssuer cardIssuer, EasyPayProvider easyPayProvider, String orderName,
                          String customerName, String customerPhone) {
    }

    // ------------------------------------------------------------------ 결제창 열기

    /**
     * 청구에 대한 결제 시도를 만들고 결제창 파라미터를 돌려준다. 이전 시도가 남아 있어도 새로 만든다(수단을 바꿔 재시도) —
     * 이전 결제창이 뒤늦게 결제되면 확정 단계에서 「이미 정산된 청구」로 자동 취소된다.
     */
    public OrderDto.PaymentWindow open(Long userId, Long chargeId, OrderDto.PaymentSelection selection) {
        PaymentMethod method = selection == null ? null : selection.getMethod();
        if (method == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "결제수단을 선택해 주세요.");
        }
        CardIssuer cardIssuer = method == PaymentMethod.CARD ? selection.getCardIssuer() : null;
        EasyPayProvider provider = method == PaymentMethod.CARD ? null : selection.getEasyPayProvider();
        if (method == PaymentMethod.CARD && cardIssuer == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카드사를 선택해 주세요.");
        }
        if (method != PaymentMethod.CARD && provider == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "간편결제를 선택해 주세요.");
        }
        String channelKey = gateway.channelKeyFor(method, provider)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE));

        LocalDateTime now = LocalDateTime.now();
        Opened opened = transaction.execute(tx -> {
            OrderClaimCharge charge = chargeRepository.findById(chargeId)
                    .filter(found -> found.getCollection().isOwnedBy(userId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));
            OrderClaimCollection collection = charge.getCollection();
            // 반려 재발송비는 그 요청의 판정이 다 끝나 결제 기한이 발급된 뒤에만 낼 수 있다.
            if (!charge.isPending()
                    || (charge.getType() == ClaimChargeType.REJECT_RESHIP && charge.getDueAt() == null)) {
                throw new BusinessException(ErrorCode.CLAIM_PAYMENT_NOT_REQUIRED);
            }
            int attempt = (int) paymentRepository.countByChargeId(chargeId) + 1;
            OrderClaimPayment payment = paymentRepository.save(OrderClaimPayment.ready(chargeId, collection.getId(),
                    userId, attempt, method, cardIssuer, provider, charge.getAmount(), channelKey, now));
            return new Opened(payment.getPaymentId(), charge.getAmount(), channelKey, method, cardIssuer, provider,
                    charge.getType() == ClaimChargeType.EXCHANGE_RESHIP ? EXCHANGE_ORDER_NAME : REJECT_ORDER_NAME,
                    collection.getReshipRecipient(), collection.getReshipPhone());
        });

        // 금액 고정 — 커밋 뒤. 실패해도 결제 행은 남고(정리 배치가 실패로 닫는다) 앱은 다시 시도하면 된다.
        try {
            gateway.preRegister(opened.paymentId(), opened.amount(), Payment.CURRENCY_KRW);
        } catch (PaymentGatewayException | PaymentGatewayRejectedException e) {
            log.warn("클레임 결제 사전 등록 실패 - paymentId: {} - {}", opened.paymentId(), e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        return OrderDto.PaymentWindow.builder()
                .paymentId(opened.paymentId())
                .storeId(gateway.storeId())
                .channelKey(opened.channelKey())
                .orderName(opened.orderName())
                .totalAmount((long) opened.amount())
                .currency(Payment.CURRENCY_KRW)
                .payMethod(gateway.payMethodCode(opened.method()))
                .cardCompany(PortOneCodes.cardCompany(opened.cardIssuer()))
                .easyPayProvider(PortOneCodes.easyPayProvider(opened.easyPayProvider()))
                .customer(OrderDto.Customer.builder()
                        .fullName(opened.customerName()).phoneNumber(opened.customerPhone()).build())
                .build();
    }

    // ------------------------------------------------------------------ 확정

    /** 앱 콜백(결제창 복귀) — 본인 결제만. 실패로 닫힌 결제는 다시 조회하지 않는다(화면에서 반복 호출해도 포트원으로 나가지 않는다). */
    public UserClaimDto.PaymentCompleteResponse complete(Long userId, String paymentId) {
        OrderClaimPayment payment = paymentRepository.findById(paymentId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        ClaimPaymentStatus status = confirm(paymentId, false);
        List<OrderClaim> claims = claimRepository.findByCollectionId(payment.getCollectionId());
        return new UserClaimDto.PaymentCompleteResponse(status, payment.getCollectionId(),
                claims.stream().map(OrderClaim::getId).toList(), claims.isEmpty() ? null : claims.get(0).getStatus());
    }

    /**
     * 확정 — 멱등이다. 포트원 조회 결과만 믿는다.
     *
     * @param recheckFailed 실패로 닫힌 결제도 다시 조회하는가 — 웹훅·배치는 본다(실패 뒤 같은 paymentId 로 결제될 수 있다)
     * @throws BusinessException PAYMENT_NOT_FOUND · PAYMENT_GATEWAY_ERROR(조회 실패 — 상태 불변)
     */
    public ClaimPaymentStatus confirm(String paymentId, boolean recheckFailed) {
        OrderClaimPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        ClaimPaymentStatus status = payment.getStatus();
        if (status != ClaimPaymentStatus.READY && !(status == ClaimPaymentStatus.FAILED && recheckFailed)) {
            return status;
        }
        Optional<PortOnePayment> remote;
        try {
            remote = gateway.getPayment(paymentId);
        } catch (PaymentGatewayException | PaymentGatewayRejectedException e) {
            log.warn("클레임 결제 조회 실패 - paymentId: {} - {}", paymentId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        if (remote.isEmpty()) {
            return status; // 사전 등록만 되고 결제창을 연 적 없는 결제 — 「결제 안 됨」이다.
        }
        PortOnePayment portone = remote.get();
        LocalDateTime now = LocalDateTime.now();
        if (portone.status() == PortOneStatus.PAID) {
            handlePaid(payment, portone, now);
        } else if (portone.status() == PortOneStatus.FAILED || portone.status() == PortOneStatus.CANCELLED) {
            transaction.executeWithoutResult(tx -> paymentRepository.markFailed(paymentId,
                    portone.failCode() != null ? portone.failCode() : portone.status().name(), now));
        }
        return paymentRepository.findById(paymentId).map(OrderClaimPayment::getStatus).orElse(status);
    }

    /**
     * 검증을 통과하면 결제 확정과 도메인 반영(요청 접수 · 재발송 대기 전환)을 <b>한 트랜잭션</b>으로 한다 — 결제는 됐는데
     * 요청이 접수되지 않은 상태를 남기지 않는다. 도메인이 받지 않으면 통째로 되돌리고 그 결제를 자동 취소한다.
     */
    private void handlePaid(OrderClaimPayment payment, PortOnePayment portone, LocalDateTime now) {
        String paymentId = payment.getPaymentId();
        boolean valid = gateway.storeId().equals(portone.storeId())
                && Payment.CURRENCY_KRW.equals(portone.currency())
                && portone.totalAmount() != null
                && portone.totalAmount() == payment.getAmount().longValue();
        if (!valid) {
            log.warn("클레임 결제 검증 실패 - paymentId: {}, storeId: {}, amount: {} (청구 {})", paymentId,
                    portone.storeId(), portone.totalAmount(), payment.getAmount());
            autoCancel(payment, now);
            return;
        }
        try {
            transaction.executeWithoutResult(tx -> {
                if (paymentRepository.markPaid(paymentId, portone.transactionId(),
                        portone.paidAt() != null ? portone.paidAt() : now, portone.rawJson()) == 1) {
                    claimService.onChargePaid(payment.getChargeId(), paymentId, now);
                }
            });
        } catch (BusinessException e) {
            log.info("받을 수 없는 클레임 결제 - 자동 취소 - paymentId: {} - {}", paymentId, e.getMessage());
            autoCancel(payment, now);
        }
    }

    /** 선점한 호출만 포트원을 부른다 — 동시에 둘이 판정해도 외부 호출은 한 번이다. */
    private void autoCancel(OrderClaimPayment payment, LocalDateTime now) {
        Integer claimed = transaction.execute(tx -> paymentRepository.requestCancel(payment.getPaymentId(),
                List.of(ClaimPaymentStatus.READY, ClaimPaymentStatus.FAILED), now));
        if (claimed != null && claimed == 1) {
            executeCancel(payment.getPaymentId());
        }
    }

    // ------------------------------------------------------------------ 취소 집행

    /**
     * 취소가 선점된 결제를 포트원에서 취소한다 — 「이미 취소됨」도 성공이다. 결과를 모르거나 거절되면 상태를 그대로 두고
     * 정리 배치가 다시 시도한다(돈이 걸린 일이라 조용히 포기하지 않는다).
     */
    public void executeCancel(String paymentId) {
        OrderClaimPayment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != ClaimPaymentStatus.CANCEL_REQUESTED) {
            return;
        }
        try {
            PortOneCancelResult result = gateway.cancel(paymentId, payment.getAmount(), "재발송 배송비 결제 취소");
            if (result.isCancelled()) {
                transaction.executeWithoutResult(tx -> paymentRepository.markCancelled(paymentId, LocalDateTime.now()));
            }
        } catch (PaymentGatewayException e) {
            log.warn("클레임 결제 취소 결과 미상 - 재시도 대기 - paymentId: {} - {}", paymentId, e.getMessage());
        } catch (PaymentGatewayRejectedException e) {
            log.error("클레임 결제 취소 거절 - 운영자 확인 필요 - paymentId: {} - {}", paymentId, e.getMessage());
        }
    }

    /** 취소가 선점된 채 남은 결제를 전부 집행한다 — 철회 직후와 정리 배치가 부른다. */
    public void cancelRequested() {
        for (String paymentId : paymentRepository.findCancelRequestedIds(PageRequest.of(0, BATCH_LIMIT))) {
            executeCancel(paymentId);
        }
    }

    // ------------------------------------------------------------------ 정리 배치(6절)

    /**
     * ① 오래된 결제 대기 — 앱이 콜백을 못 보낸 결제를 조회해 확정하고, 결제되지 않았으면 실패로 닫는다
     * ② 결제 없이 남은 요청 초안 삭제 — 선점 재고를 되돌린다 ③ 끝나지 않은 취소 재시도.
     */
    public void reconcile(LocalDateTime now) {
        LocalDateTime before = now.minusMinutes(orderProperties.getClaim().getPaymentPendingMinutes());
        for (String paymentId : paymentRepository.findStaleReadyIds(before, PageRequest.of(0, BATCH_LIMIT))) {
            try {
                if (confirm(paymentId, false) == ClaimPaymentStatus.READY) {
                    transaction.executeWithoutResult(tx -> paymentRepository.markFailed(paymentId, "EXPIRED", now));
                }
            } catch (BusinessException e) {
                // 조회가 안 되면 판정하지 않는다 — 결제됐을 수도 있다. 다음 회차가 다시 본다.
                log.warn("클레임 결제 수렴 보류 - paymentId: {} - {}", paymentId, e.getMessage());
            }
        }
        for (Long collectionId : claimService.findStaleDraftIds(before, BATCH_LIMIT)) {
            try {
                claimService.discardDraft(collectionId);
            } catch (RuntimeException e) {
                log.error("결제 대기 요청 초안 삭제 실패 - collectionId: {}", collectionId, e);
            }
        }
        cancelRequested();
    }
}
