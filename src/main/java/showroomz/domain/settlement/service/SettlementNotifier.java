package showroomz.domain.settlement.service;

import java.time.format.DateTimeFormatter;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.service.NotificationCommand;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDate;

/**
 * 정산 알림 — <b>훅 자리만 두고 발송은 스텁</b>이다(44 어드민 설계서 · 이슈 스레드 설계서 8-2). {@code GroupBuyNotifier}와 같다.
 *
 * <p>채널(메일 / 웹 알림센터)이 정해지지 않았다. 정산 · 조정 이벤트를 전부 이 클래스 하나에 두고 호출 지점만 박는다 — 채널이
 * 정해지면 이 클래스 안쪽만 채운다. 운영자에게 보내는 것은 조치가 필요한 사건(분배 실패 · 세금계산서 대조 대기)뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementNotifier {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");

    private final SettlementRepository settlementRepository;
    private final NotificationPublisher publisher;

    // ------------------------------------------------------------------ 정산

    /** 생성 — 양측 「{MM.DD} 23:59까지 확인 · 조정 요청 가능」. */
    @Transactional(readOnly = true)
    public void settlementGenerated(Long settlementId) {
        stub("BOTH", "SETTLEMENT_GENERATED", settlementId);
        load(settlementId, s -> {
            String due = s.getReviewDueAt() == null ? "" : " · " + s.getReviewDueAt().format(MD) + " 23:59까지 확인할 수 있습니다";
            toBrand(s, NotificationType.SETTLEMENT_CREATED, NotificationActorType.SYSTEM, null,
                    "「" + title(s) + "」 정산이 생성되었습니다" + due);
            toCreator(s, NotificationType.SETTLEMENT_CREATED, NotificationActorType.PLATFORM, null,
                    title(s) + " 공구 정산이 생성됐어요" + (s.getReviewDueAt() == null ? ""
                            : " · " + s.getReviewDueAt().format(MD) + "까지 확인할 수 있어요"));
        });
    }

    /** 재계산(1021 기-13) — 양측 「환불로 정산 금액이 바뀌었습니다 · {MM.DD}까지 다시 확인」. */
    @Transactional(readOnly = true)
    public void settlementRecalculated(Long settlementId) {
        stub("BOTH", "SETTLEMENT_RECALCULATED", settlementId);
        load(settlementId, s -> {
            String due = s.getReviewDueAt() == null ? "" : " · " + s.getReviewDueAt().format(MD) + " 23:59까지 다시 확인할 수 있습니다";
            toBrand(s, NotificationType.SETTLEMENT_RECALCULATED, NotificationActorType.SYSTEM, null,
                    "「" + title(s) + "」 정산이 환불로 재계산되었습니다" + due);
            toCreator(s, NotificationType.SETTLEMENT_RECALCULATED, NotificationActorType.PLATFORM, null,
                    title(s) + " 공구 정산이 환불로 다시 계산됐어요" + (s.getReviewDueAt() == null ? ""
                            : " · " + s.getReviewDueAt().format(MD) + "까지 확인할 수 있어요"));
        });
    }

    /** 확정 — 양측 지급 예정일 · 사업자 「세금계산서 발행 요청」. */
    @Transactional(readOnly = true)
    public void settlementConfirmed(Long settlementId) {
        stub("BOTH", "SETTLEMENT_CONFIRMED", settlementId);
        load(settlementId, s -> {
            toBrand(s, NotificationType.SETTLEMENT_CONFIRMED, NotificationActorType.SYSTEM, null,
                    "「" + title(s) + "」 정산이 확정되었습니다 — 지급 예정 %,d원".formatted(s.getBrandPayoutAmount()));
            toCreator(s, NotificationType.SETTLEMENT_CONFIRMED, NotificationActorType.PLATFORM, null,
                    title(s) + " 공구 정산이 확정됐어요 · 지급 예정 %,d원".formatted(s.getCreatorPayoutAmount()));
            if (s.isBusinessCreator()) {
                // 사업자 — 세금계산서를 발행해 승인번호를 입력해야 지급된다(스튜디오 H4 「지급 대기」).
                toCreator(s, NotificationType.TAX_INVOICE_NEEDED, NotificationActorType.PLATFORM, null,
                        title(s) + " 공구 세금계산서를 발행해 주세요 · 승인번호를 입력하면 지급돼요");
            }
        });
    }

    /** 수취자 몫 지급 완료 — 수취자별. */
    @Transactional(readOnly = true)
    public void settlementPaid(Long settlementId, SettlementPayee payee) {
        stub(payee.name(), "SETTLEMENT_PAID", settlementId);
        load(settlementId, s -> {
            if (payee == SettlementPayee.BRAND) {
                toBrand(s, NotificationType.SETTLEMENT_PAID, NotificationActorType.SYSTEM, null,
                        "「" + title(s) + "」 정산 %,d원이 지급되었습니다".formatted(s.getBrandPayoutAmount()));
            } else if (payee == SettlementPayee.CREATOR) {
                toCreator(s, NotificationType.REWARD_PAID, NotificationActorType.PLATFORM, null,
                        title(s) + " 공구 리워드 %,d원이 지급됐어요".formatted(s.getCreatorPayoutAmount()));
            }
        });
    }

    /** 분배 실패 — 운영자(재분배 M3). */
    public void payoutFailed(Long settlementId, SettlementPayee payee) {
        stub("ADMIN", "PAYOUT_FAILED:" + payee.name(), settlementId);
    }

    // ------------------------------------------------------------------ PG 지급대행(44 토스 설계서 4-3 · 5-1 · 5-3 · 5-4)

    /** 셀러 등록 실패로 수취자 행 보류 — 운영자. */
    public void payoutBlocked(Long settlementId, SettlementPayee payee, String reasonCode) {
        stub("ADMIN", "PAYOUT_BLOCKED:" + payee.name() + ":" + reasonCode, settlementId);
    }

    /** 수취자 본인이 풀어야 하는 보류(본인인증 · KYC) — 수취자에게 「토스페이먼츠 본인인증 문자를 확인해 주세요」(4-3 · L-2′). */
    public void partnerVerificationRequired(Long settlementId, SettlementPayee payee, String reasonCode) {
        stub(payee.name(), "PARTNER_VERIFICATION_REQUIRED:" + reasonCode, settlementId);
    }

    /** 지시 끝 — 「D 일자 지급 N건 · 합계」 · PG 대사 결과 포함(5-3). 사람이 할 일은 없고 어긋날 때만 본다. */
    public void payoutRequestedSummary(LocalDate payoutDate, int ourCount, long ourAmount, Integer pgCount, Long pgAmount) {
        log.info("[settlement-notify:stub] to=ADMIN event=PAYOUT_REQUESTED_SUMMARY date={} ours={}건/{}원 pg={}건/{}원",
                payoutDate, ourCount, ourAmount, pgCount, pgAmount);
    }

    /** 잔액 부족 — 들어가는 만큼만 지시하고 나머지는 다음 영업일로 연기했다(5-1 ①). */
    public void payoutBalanceInsufficient(LocalDate payoutDate, long available, long required, int deferredCount) {
        log.info("[settlement-notify:stub] to=ADMIN event=PAYOUT_BALANCE_INSUFFICIENT date={} available={} required={} deferred={}",
                payoutDate, available, required, deferredCount);
    }

    /** 지시 뒤 N영업일 — REQUESTED 가 닫히지 않는다(PG 처리 지연). */
    public void payoutStale(Long settlementId) {
        stub("ADMIN", "PAYOUT_STALE", settlementId);
    }

    /** 결과 조회가 사람을 부른다 — PG 보류 · 주당 한도 초과 예상. */
    public void payoutCheckRequired(Long settlementId, SettlementPayee payee, String code) {
        stub("ADMIN", "PAYOUT_CHECK_REQUIRED:" + payee.name() + ":" + code, settlementId);
    }

    public void taxInvoiceSubmitted(Long settlementId) {
        stub("ADMIN", "TAX_INVOICE_SUBMITTED", settlementId);
    }

    @Transactional(readOnly = true)
    public void taxInvoiceVerified(Long settlementId) {
        stub("CREATOR", "TAX_INVOICE_VERIFIED", settlementId);
        load(settlementId, s -> toCreator(s, NotificationType.TAX_INVOICE_VERIFIED, NotificationActorType.PLATFORM,
                null, title(s) + " 공구 세금계산서가 확인됐어요 · 지급 예정일에 입금돼요"));
    }

    @Transactional(readOnly = true)
    public void taxInvoiceRejected(Long settlementId) {
        stub("CREATOR", "TAX_INVOICE_REJECTED", settlementId);
        load(settlementId, s -> toCreator(s, NotificationType.TAX_INVOICE_REJECTED, NotificationActorType.OPERATOR,
                null, title(s) + " 공구 세금계산서 승인번호가 반려됐어요 · 다시 입력해 주세요"));
    }

    /** 차감 발생 — 해당 측 「다음 정산에서 {amount}원이 차감됩니다」(§41-7 사전 고지). */
    @Transactional(readOnly = true)
    public void clawbackRegistered(Long originSettlementId, String clawbackNumber) {
        stub("BOTH", "CLAWBACK_REGISTERED:" + clawbackNumber, originSettlementId);
        load(originSettlementId, s -> {
            toBrand(s, NotificationType.CLAWBACK_APPLIED, NotificationActorType.SYSTEM, null,
                    "「" + title(s) + "」 구매확정 후 환불이 생겨 다음 정산에서 차감됩니다");
            toCreator(s, NotificationType.CLAWBACK_APPLIED, NotificationActorType.PLATFORM, null,
                    title(s) + " 공구에서 구매확정 후 환불이 생겨 다음 정산 리워드에서 차감돼요");
        });
    }

    // ------------------------------------------------------------------ 조정 협의(이슈 스레드 설계서 8-2)

    @Transactional(readOnly = true)
    public void adjustmentRequested(Long settlementId, String to) {
        stub(to, "ADJUSTMENT_REQUESTED", settlementId);
        load(settlementId, s -> {
            if ("SELLER".equals(to) || "BRAND".equals(to)) {
                toBrand(s, NotificationType.ADJUSTMENT_REQUESTED, NotificationActorType.CREATOR, s.getCreator().getShowroomName(),
                        "「" + title(s) + "」 정산 조정을 요청했습니다 — 내 응답이 필요합니다");
            } else {
                toCreator(s, NotificationType.ADJUSTMENT_REQUESTED, NotificationActorType.BRAND, s.getMarket().getMarketName(),
                        title(s) + " 공구 정산 조정을 요청했어요 · 응답해 주세요");
            }
        });
    }

    @Transactional(readOnly = true)
    public void adjustmentCountered(Long settlementId, String to) {
        stub(to, "ADJUSTMENT_COUNTERED", settlementId);
        load(settlementId, s -> {
            if ("SELLER".equals(to) || "BRAND".equals(to)) {
                toBrand(s, NotificationType.ADJUSTMENT_COUNTERED, NotificationActorType.CREATOR, s.getCreator().getShowroomName(),
                        "「" + title(s) + "」 정산 조정에 다른 금액을 제안했습니다");
            } else {
                toCreator(s, NotificationType.ADJUSTMENT_COUNTERED, NotificationActorType.BRAND, s.getMarket().getMarketName(),
                        title(s) + " 공구 정산 조정에 다른 금액을 제안했어요");
            }
        });
    }

    @Transactional(readOnly = true)
    public void adjustmentRejected(Long settlementId, String to) {
        stub(to, "ADJUSTMENT_REJECTED", settlementId);
        load(settlementId, s -> {
            if ("SELLER".equals(to) || "BRAND".equals(to)) {
                toBrand(s, NotificationType.ADJUSTMENT_REJECTED, NotificationActorType.CREATOR, s.getCreator().getShowroomName(),
                        "「" + title(s) + "」 정산 조정 제안에 반대했습니다");
            } else {
                toCreator(s, NotificationType.ADJUSTMENT_REJECTED, NotificationActorType.BRAND, s.getMarket().getMarketName(),
                        title(s) + " 공구 정산 조정 제안에 반대했어요");
            }
        });
    }

    @Transactional(readOnly = true)
    public void adjustmentAgreed(Long settlementId) {
        stub("BOTH", "ADJUSTMENT_AGREED", settlementId);
        load(settlementId, s -> {
            toBrand(s, NotificationType.ADJUSTMENT_AGREED, NotificationActorType.SYSTEM, null, "「" + title(s) + "」 정산 조정이 합의되었습니다");
            toCreator(s, NotificationType.ADJUSTMENT_AGREED, NotificationActorType.PLATFORM, null, title(s) + " 공구 정산 조정이 합의됐어요");
        });
    }

    @Transactional(readOnly = true)
    public void adjustmentDeadlineD1(Long settlementId) {
        stub("BOTH", "ADJUSTMENT_DEADLINE_D1", settlementId);
        load(settlementId, s -> {
            toBrand(s, NotificationType.ADJUSTMENT_DEADLINE_D1, NotificationActorType.SYSTEM, null, "「" + title(s) + "」 정산 조정 합의 기한이 내일입니다");
            toCreator(s, NotificationType.ADJUSTMENT_DEADLINE_D1, NotificationActorType.PLATFORM, null, title(s) + " 공구 정산 조정 합의 기한이 내일이에요");
        });
    }

    @Transactional(readOnly = true)
    public void adjustmentExpired(Long settlementId) {
        stub("BOTH", "ADJUSTMENT_EXPIRED", settlementId);
        load(settlementId, s -> {
            toBrand(s, NotificationType.ADJUSTMENT_EXPIRED, NotificationActorType.SYSTEM, null, "「" + title(s) + "」 정산 조정 합의 기한이 지나 원래 금액으로 확정되었습니다");
            toCreator(s, NotificationType.ADJUSTMENT_EXPIRED, NotificationActorType.PLATFORM, null, title(s) + " 공구 정산 조정 합의 기한이 지나 원래 금액으로 확정됐어요");
        });
    }

    /** 요청 철회(1021 기-14) — 상대에게 「조정 요청이 철회되어 원래 금액으로 확정」. */
    @Transactional(readOnly = true)
    public void adjustmentWithdrawn(Long settlementId, String withdrawnBy) {
        stub("SELLER".equals(withdrawnBy) ? "CREATOR" : "SELLER", "ADJUSTMENT_WITHDRAWN", settlementId);
        load(settlementId, s -> {
            if ("SELLER".equals(withdrawnBy)) {
                toCreator(s, NotificationType.ADJUSTMENT_WITHDRAWN, NotificationActorType.PLATFORM, null,
                        title(s) + " 공구 정산 조정 요청이 철회되어 원래 금액으로 확정됐어요");
            } else {
                toBrand(s, NotificationType.ADJUSTMENT_WITHDRAWN, NotificationActorType.SYSTEM, null,
                        "「" + title(s) + "」 정산 조정 요청이 철회되어 원래 금액으로 확정되었습니다");
            }
        });
    }

    private static void stub(String to, String event, Long settlementId) {
        log.info("[settlement-notify:stub] to={} event={} settlementId={}", to, event, settlementId);
    }

    // ------------------------------------------------------------------ 알림함(1017 설계서 1-4 · 1-5)

    private void load(Long settlementId, java.util.function.Consumer<Settlement> action) {
        try {
            settlementRepository.findById(settlementId).ifPresent(action);
        } catch (RuntimeException e) {
            log.warn("정산 알림 조립 실패 - settlementId: {}", settlementId, e);
        }
    }

    private void toBrand(Settlement s, NotificationType type, NotificationActorType actor, String actorName, String title) {
        publisher.publish(NotificationCommand.toSeller(s.getMarketId(), type).actorType(actor).actorName(actorName)
                .title(title).targetType(target(type)).targetId(s.getId()).build());
    }

    private void toCreator(Settlement s, NotificationType type, NotificationActorType actor, String actorName, String title) {
        publisher.publish(NotificationCommand.toCreator(s.getCreatorId(), type).actorType(actor).actorName(actorName)
                .title(title).targetType(target(type)).targetId(s.getId()).build());
    }

    private static NotificationTargetType target(NotificationType type) {
        return type.name().startsWith("ADJUSTMENT_") ? NotificationTargetType.ADJUSTMENT : NotificationTargetType.SETTLEMENT;
    }

    private static String title(Settlement s) {
        String title = s.getContract() == null ? null : s.getContract().getTitle();
        return title == null || title.isBlank() ? s.getSettlementNumber() : title;
    }
}
