package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.AmlThresholdAction;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementActorType;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.domain.settlement.type.SettlementStatus;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 지급의 짧은 트랜잭션들(44 어드민 설계서 3-3) — PG 호출은 여기 없다. {@code SettlementPayoutService}가 트랜잭션 밖에서
 * 게이트웨이를 부르고 결과를 가져온다(환불 집행 {@code RefundTransitions}와 같은 구조).
 *
 * <p>행마다 조건부 UPDATE(SCHEDULED → REQUESTED → PAID / FAILED) · 정산 상태는 행 전체에서 <b>파생해 저장</b>한다(0-4): 하나라도
 * FAILED 면 PAYOUT_FAILED, 전부 PAID(또는 NOT_APPLICABLE)면 PAID · 공구 SETTLED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPayoutTransitions {

    public static final String FAIL_ACCOUNT_MISSING = "ACCOUNT_MISSING";
    private static final String PLATFORM_ACCOUNT = "SHOWROOMZ";

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementNotifier notifier;
    private final PersonalDataCipher cipher;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyCommandService groupBuyCommandService;
    private final PayoutBlockPolicy blockPolicy;
    private final PayoutHoldTransitions holdTransitions;
    private final BankRepository bankRepository;
    private final SettlementPartnerGateway partnerGateway;
    private final SettlementProperties properties;
    private final PayoutGroupPlanner groupPlanner;

    /** 지급 계좌 — 스냅샷의 원천(회원 정보 · 브랜드는 확정 시점 · 그 밖은 지시 시점). 플랫폼은 내부 계정이다. */
    public record Account(String bankName, String accountNumber, String holder) {
        public boolean isMissing() {
            return bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank();
        }
    }

    /** 묶음 지시 선점 결과 — 이번에 붙인 묶음(전송 대상)과 REQUESTED 로 올린 수취자 행 수. */
    public record GroupClaim(Long settlementId, List<Long> groupIds, int requested) {
    }

    /**
     * 묶음 지급 경로의 지시 선점(60 이니시스 전환 계획서 D-1 · D-2) — 지급일 {@code payDate} 가 되는 SCHEDULED 행(예정일 ≤ payDate)을 REQUESTED 로
     * 올리고 같은 트랜잭션에서 묶음에 붙인다(REQUESTED 행은 언제나 묶음이 있다). 정지 · 0원 행은 지시하지 않고, 계좌가 없으면 게이트웨이를 부르지 않고
     * {@code FAILED(ACCOUNT_MISSING)} — 운영자 재분배로 간다(13절 A-5). 플랫폼 몫은 묶지 않고
     * 바로 지급 완료(내부 계정 · 4-1). AML(D-6): 저장된 AML 상태가 막으면 {@code KYC_REQUIRED} 보류, 7일 누적 기준은 {@code ALERT} 면 지시하고
     * 안내만 · {@code BLOCK} 이면 보류.
     *
     * @return 지급할 정산이 아니면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GroupClaim claimForGroups(Long settlementId, LocalDate payDate, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null || (settlement.getStatus() != SettlementStatus.PAYOUT_SCHEDULED
                && settlement.getStatus() != SettlementStatus.PAYOUT_FAILED)) {
            return null;
        }
        // 회원 정보는 행 UPDATE(영속성 컨텍스트 비움) 전에 읽는다.
        Seller seller = settlement.getMarket().getSeller();
        Creator creatorEntity = settlement.getCreator();
        Account brand = accountOf(seller);
        Account creator = accountOf(creatorEntity);
        String settlementNumber = settlement.getSettlementNumber();
        Long marketId = settlement.getMarketId();
        Long creatorId = settlement.getCreatorId();
        String brandPartnerId = linkedPartnerId(seller);
        String creatorPartnerId = linkedPartnerId(creatorEntity);
        String brandAml = seller == null ? null : seller.getPgAmlStatus();
        String creatorAml = creatorEntity == null ? null : creatorEntity.getPgAmlStatus();
        boolean brandHeld = blockPolicy.isBrandBlocked(settlement);
        boolean creatorHeld = blockPolicy.isCreatorSuspended(creatorId);
        AmlThresholdAction amlAction = partnerGateway.amlThresholdAction();

        Set<Long> groupIds = new LinkedHashSet<>();
        int requested = 0;
        for (SettlementPayout payout : payoutRepository.findBySettlementId(settlementId)) {
            if (payout.getStatus() != PayoutStatus.SCHEDULED || payout.getDueDate() == null
                    || payout.getDueDate().isAfter(payDate)) {
                continue;
            }
            SettlementPayee payee = payout.getPayee();
            if (payout.getAmount() <= 0) {
                payoutRepository.updateStatusWhere(payout.getId(), EnumSet.of(PayoutStatus.SCHEDULED),
                        PayoutStatus.NOT_APPLICABLE, payout.getDueDate());
                continue;
            }
            if (payee == SettlementPayee.BRAND && brandHeld) {
                holdTransitions.holdRow(payout, PayoutBlockReason.BRAND_SUSPENDED, now);
                continue;
            }
            if (payee == SettlementPayee.CREATOR && creatorHeld) {
                holdTransitions.holdRow(payout, PayoutBlockReason.CREATOR_SUSPENDED, now);
                continue;
            }
            Account account = payout.hasAccountSnapshot()
                    ? new Account(payout.getBankName(), cipher.decrypt(payout.getAccountNumberEnc()), payout.getAccountHolder())
                    : switch (payee) {
                        case BRAND -> brand;
                        case CREATOR -> creator;
                        case PLATFORM -> new Account(PLATFORM_ACCOUNT, null, PLATFORM_ACCOUNT);
                    };
            if (payee != SettlementPayee.PLATFORM && account.isMissing()) {
                if (payoutRepository.markFailed(payout.getId(), null, FAIL_ACCOUNT_MISSING, "등록 계좌 없음", now) == 1) {
                    historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_FAILED,
                            payee.getLabel() + " · 등록 계좌 없음", now);
                    notifier.payoutFailed(settlementId, payee);
                }
                continue;
            }
            boolean amlNotice = false;
            if (payee != SettlementPayee.PLATFORM) {
                String rawAml = payee == SettlementPayee.BRAND ? brandAml : creatorAml;
                if (partnerGateway.isAmlBlocking(rawAml)) {
                    blockForKyc(settlementId, payout, "고객확인(AML) 이행 필요 — 이행 완료 후 지급", "AML 이행 필요", now);
                    continue;
                }
                if (amlAction != AmlThresholdAction.NONE
                        && exceedsWeeklyLimit(payee, marketId, creatorId, payout.getAmount(), now)) {
                    if (amlAction == AmlThresholdAction.BLOCK) {
                        blockForKyc(settlementId, payout, "7일 누적 고객확인(AML) 기준 초과 예상 — 이행 완료 후 지급",
                                "7일 누적 고객확인 기준 초과 예상", now);
                        continue;
                    }
                    amlNotice = true;   // ALERT — 지시는 한다(우리가 막으면 고객확인이 시작되지 않을 수 있다 · D-6 ①).
                }
            }
            if (payoutRepository.markRequested(payout.getId(), account.bankName(), cipher.encrypt(account.accountNumber()),
                    account.holder(), now) != 1) {
                continue;
            }
            historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_REQUESTED,
                    "%s · %,d원 지급 지시".formatted(payee.getLabel(), payout.getAmount()), now);
            if (payee == SettlementPayee.PLATFORM) {
                // 플랫폼 몫은 PG 에 가지 않는다(내부 계정) — 묶지 않고 바로 지급 완료(4-1).
                String reference = "PLATFORM-" + settlementNumber;
                if (payoutRepository.markPaid(payout.getId(), reference, now) == 1) {
                    historyRecorder.record(settlementId, SettlementEventType.PAYOUT_PAID, SettlementActorType.PG, null,
                            "%s · %,d원 · %s".formatted(payee.getLabel(), payout.getAmount(), reference), now);
                    notifier.settlementPaid(settlementId, payee);
                }
                continue;
            }
            Long payeeRefId = payee == SettlementPayee.BRAND ? marketId : creatorId;
            String partnerId = payee == SettlementPayee.BRAND ? brandPartnerId : creatorPartnerId;
            String bankCode = bankRepository.findByName(account.bankName().trim()).map(Bank::getCode).orElse(null);
            String accountHash = SettlementPartnerIds.accountHash(bankCode, digits(account.accountNumber()), account.holder());
            Long groupId = groupPlanner.attach(payout.getId(), payee, payeeRefId, partnerId, accountHash, payDate, now);
            if (groupId != null) {
                groupIds.add(groupId);
                requested++;
            }
            if (amlNotice) {
                notifier.payoutAmlThresholdNotice(settlementId, payee);
            }
        }
        derive(settlementId, now);
        return new GroupClaim(settlementId, List.copyOf(groupIds), requested);
    }

    private void blockForKyc(Long settlementId, SettlementPayout payout, String reason, String historyNote, LocalDateTime now) {
        String code = PayoutBlockReason.KYC_REQUIRED.name();
        if (payoutRepository.blockForPartner(payout.getId(), code, reason) == 1) {
            historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED,
                    payout.getPayee().getLabel() + " · " + PayoutBlockReason.KYC_REQUIRED.getLabel() + " · " + historyNote, now);
            notifier.payoutCheckRequired(settlementId, payout.getPayee(), code);
            notifier.partnerVerificationRequired(settlementId, payout.getPayee(), code);
        }
    }

    /** 이 환경 · 이 PG 의 연결만(60 이니시스 전환 계획서 D-12 · 토스 설계서 3-1) — 아니면 null(연결 없음). */
    private String linkedPartnerId(Seller seller) {
        return seller == null || !linked(seller.getPgPartnerEnv(), seller.getPgPartnerProvider()) ? null : seller.getPgPartnerId();
    }

    private String linkedPartnerId(Creator creator) {
        return creator == null || !linked(creator.getPgPartnerEnv(), creator.getPgPartnerProvider()) ? null
                : creator.getPgPartnerId();
    }

    private boolean linked(String env, String provider) {
        return partnerGateway.env().equals(env) && partnerGateway.provider().equals(provider);
    }

    private static String digits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }

    /** 최근 7일(지시 시각 기준) REQUESTED · PAID 합 + 이 행 ≥ 한도. */
    private boolean exceedsWeeklyLimit(SettlementPayee payee, Long marketId, Long creatorId, long amount, LocalDateTime now) {
        LocalDateTime since = now.minusDays(7);
        long recent = switch (payee) {
            case BRAND -> payoutRepository.sumRecentForMarket(marketId, since);
            case CREATOR -> payoutRepository.sumRecentForCreator(creatorId, since);
            case PLATFORM -> 0L;
        };
        return recent + amount >= properties.getPayout().getWeeklyLimitAmount();
    }

    /**
     * PG 서브몰 사유 보류 해제 — 서브몰 등록 · 고객확인이 지급 가능이 됐을 때. 인플루언서 행에 다른 보류 사유(주민등록번호 · 세금계산서)가
     * 남아 있으면 사유만 지우고 BLOCKED 로 둔다(기존 재판정이 푼다).
     *
     * @return 지급 예정으로 옮겼으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releasePartnerBlocked(Long settlementId, Long payoutId, SettlementPayee payee, LocalDate today) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null) {
            return false;
        }
        if (payee == SettlementPayee.CREATOR && blockPolicy.isBlocked(settlement)) {
            payoutRepository.releaseBlocked(payoutId, PayoutStatus.BLOCKED, null);
            return false;
        }
        LocalDate due = settlement.getPayoutDueDate() == null || settlement.getPayoutDueDate().isBefore(today)
                ? today : settlement.getPayoutDueDate();
        return payoutRepository.releaseBlocked(payoutId, PayoutStatus.SCHEDULED, due) == 1;
    }

    /** 파트너 재등록이 다른 사유로 또 실패했다 — 보류 사유만 바꾼다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBlockReason(Long payoutId, String failCode, String failReason) {
        payoutRepository.updateBlockReason(payoutId, failCode, failReason);
    }

    /** PG 처리 지연(D-8) — 지급일 뒤 N영업일이 지나도 REQUESTED 가 닫히지 않는다. 이력 · 운영자 알림. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStale(Long settlementId, SettlementPayee payee, LocalDateTime now) {
        historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_CHECK_REQUIRED,
                payee.getLabel() + " · PG 처리 지연 — 지급 결과가 아직 없습니다", now);
        notifier.payoutStale(settlementId);
    }

    /**
     * 정산 상태 재파생(0-4) — 수취자 행 전부 PAID · NOT_APPLICABLE → PAID(공구 SETTLED) · 하나라도 FAILED → PAYOUT_FAILED ·
     * 실패가 다 풀렸으면(재분배) PAYOUT_SCHEDULED. 호출자 트랜잭션에 합류한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void derive(Long settlementId, LocalDateTime now) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementStatus current = settlement.getStatus();
        if (current != SettlementStatus.PAYOUT_SCHEDULED && current != SettlementStatus.PAYOUT_FAILED) {
            return;
        }
        List<SettlementPayout> payouts = payoutRepository.findBySettlementId(settlementId);
        boolean allSettled = payouts.stream().allMatch(p -> p.getStatus().isSettled());
        boolean anyFailed = payouts.stream().anyMatch(p -> p.getStatus() == PayoutStatus.FAILED);
        if (allSettled) {
            if (settlementRepository.transition(settlementId, EnumSet.of(current), SettlementStatus.PAID) != 1) {
                throw new BusinessException(ErrorCode.SETTLEMENT_STATE_CHANGED);
            }
            settlement.applyPaid(now);
            markGroupBuySettled(settlement.getGroupBuyId(), now);
            log.info("정산 지급 완료 - settlementId: {}", settlementId);
        } else if (anyFailed && current == SettlementStatus.PAYOUT_SCHEDULED) {
            if (settlementRepository.transition(settlementId, EnumSet.of(current), SettlementStatus.PAYOUT_FAILED) == 1) {
                settlement.applyDerivedStatus(SettlementStatus.PAYOUT_FAILED);
            }
        } else if (!anyFailed && current == SettlementStatus.PAYOUT_FAILED) {
            if (settlementRepository.transition(settlementId, EnumSet.of(current),
                    SettlementStatus.PAYOUT_SCHEDULED) == 1) {
                settlement.applyDerivedStatus(SettlementStatus.PAYOUT_SCHEDULED);
            }
        }
    }

    /**
     * 보류 재판정(5-4) — 비사업자의 주민등록번호가 등록되면 인플루언서 행 {@code BLOCKED → SCHEDULED(예정일 = max(지급 예정일, 오늘))}.
     * 사업자 건(세금계산서 확인)은 운영자 대조(M4)가 푼다.
     *
     * @return 풀었으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseIfUnblocked(Long settlementId, Long payoutId, LocalDate today) {
        Settlement settlement = settlementRepository.findForUpdate(settlementId).orElse(null);
        if (settlement == null || settlement.isBusinessCreator() || blockPolicy.isBlocked(settlement)) {
            return false;
        }
        LocalDate due = settlement.getPayoutDueDate() == null || settlement.getPayoutDueDate().isBefore(today)
                ? today : settlement.getPayoutDueDate();
        return payoutRepository.updateStatusWhere(payoutId, EnumSet.of(PayoutStatus.BLOCKED), PayoutStatus.SCHEDULED,
                due) == 1;
    }

    private void markGroupBuySettled(Long groupBuyId, LocalDateTime paidAt) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElse(null);
        if (groupBuy == null || groupBuy.getStatus() != GroupBuyStatus.ENDED) {
            // 돈은 이미 나갔다 — 공구 전이 실패로 지급 결과를 되돌리지 않는다.
            log.warn("정산 지급 완료 · 공구 SETTLED 전이 건너뜀 - groupBuyId: {}, status: {}", groupBuyId,
                    groupBuy == null ? null : groupBuy.getStatus());
            return;
        }
        groupBuyCommandService.markSettled(groupBuyId, paidAt);
    }

    /** 수취자의 현재 등록 계좌 — 지시 · 재분배(현재 회원 정보)의 스냅샷 원천. 플랫폼은 내부 계정. */
    public Account currentAccount(Settlement settlement, SettlementPayee payee) {
        return switch (payee) {
            case BRAND -> accountOf(settlement.getMarket().getSeller());
            case CREATOR -> accountOf(settlement.getCreator());
            case PLATFORM -> new Account(PLATFORM_ACCOUNT, null, PLATFORM_ACCOUNT);
        };
    }

    static Account accountOf(Seller seller) {
        return seller == null ? new Account(null, null, null)
                : new Account(seller.getBankName(), seller.getAccountNumber(), seller.getAccountHolder());
    }

    private static Account accountOf(Creator creator) {
        return creator == null ? new Account(null, null, null)
                : new Account(creator.getBankName(), creator.getAccountNumber(), creator.getRealName());
    }
}
