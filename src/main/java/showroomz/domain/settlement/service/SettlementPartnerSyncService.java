package showroomz.domain.settlement.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Account;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Business;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Contact;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerProfile;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerResult;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerStatus;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.PayoutStatus;
import showroomz.domain.settlement.type.SettlementEventType;
import showroomz.domain.settlement.type.SettlementPayee;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 수취자 ↔ PG 파트너 동기화(44_포트원_파트너정산_연동_BE_설계서.md 4절). 확정 뒤(AfterCommit)에 두 수취자의 파트너를 보장하고,
 * 실패하면 확정은 그대로 둔 채 그 수취자 행만 {@code BLOCKED(사유)} 로 둔다 — 지급일에 등록 실패가 터지지 않게. 지급 배치 앞단이
 * 같은 메서드로 다시 시도한다({@code SettlementPayoutService.releaseBlocked}).
 *
 * <p>외부 호출은 트랜잭션 밖에서 하고, 읽기 · 쓰기는 각각 짧은 트랜잭션이다 — 포트원 지연 · 실패가 확정을 되돌리면 안 된다(0-8).
 */
@Slf4j
@Service
public class SettlementPartnerSyncService {

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;
    private final BankRepository bankRepository;
    private final SettlementPartnerGateway gateway;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementNotifier notifier;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public SettlementPartnerSyncService(SettlementRepository settlementRepository,
                                        SettlementPayoutRepository payoutRepository, MarketRepository marketRepository,
                                        CreatorRepository creatorRepository, BankRepository bankRepository,
                                        SettlementPartnerGateway gateway, SettlementHistoryRecorder historyRecorder,
                                        SettlementNotifier notifier, PlatformTransactionManager transactionManager) {
        this.settlementRepository = settlementRepository;
        this.payoutRepository = payoutRepository;
        this.marketRepository = marketRepository;
        this.creatorRepository = creatorRepository;
        this.bankRepository = bankRepository;
        this.gateway = gateway;
        this.historyRecorder = historyRecorder;
        this.notifier = notifier;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ 확정 뒤

    /** 확정 뒤 — 브랜드 · 인플루언서 파트너 보장. 실패한 수취자 행만 보류한다(4-3). 예외를 밖으로 내지 않는다. */
    public void ensureForSettlement(Long settlementId) {
        for (SettlementPayee payee : List.of(SettlementPayee.BRAND, SettlementPayee.CREATOR)) {
            PartnerResult result;
            try {
                result = ensure(settlementId, payee);
            } catch (RuntimeException e) {
                log.error("PG 파트너 동기화 실패 - settlementId: {}, payee: {}", settlementId, payee, e);
                result = PartnerResult.failed(PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패 — " + e.getMessage());
            }
            if (!result.isApproved()) {
                block(settlementId, payee, result);
            }
        }
    }

    /**
     * 수취자 1명의 파트너 보장 — 연결돼 있고 계좌가 같고 승인 상태면 호출하지 않는다. 결과는 회원 정보에 기록한다.
     * 프로필을 만들 수 없는 사유(계좌 없음 · 미지원 은행 · 생년월일 없음)는 PG 를 부르지 않고 실패로 돌려준다.
     */
    public PartnerResult ensure(Long settlementId, SettlementPayee payee) {
        Prepared prepared;
        try {
            prepared = readTransaction.execute(tx -> prepare(settlementId, payee));
        } catch (ProfileIncomplete e) {
            return PartnerResult.failed(e.reason.name(), e.getMessage());
        }
        if (prepared.upToDate()) {
            return PartnerResult.approved();
        }
        PartnerResult result = gateway.ensure(prepared.profile());
        LocalDateTime now = LocalDateTime.now();
        if (result.status() != PartnerStatus.FAILED && gateway.isExternal()) {
            writeTransaction.executeWithoutResult(tx -> link(prepared, result, now));
        }
        return result;
    }

    /** 정산계좌 변경 승인 — 이미 연결된 브랜드 파트너의 계좌를 즉시 갱신한다(4-3 · 4-4). 연결 전이면 다음 확정이 만든다. */
    public void onBrandAccountChanged(Long marketId) {
        Prepared prepared;
        try {
            prepared = readTransaction.execute(tx -> prepareBrand(marketRepository.findById(marketId).orElseThrow()));
        } catch (ProfileIncomplete e) {
            log.warn("브랜드 계좌 변경 — PG 파트너 갱신 보류 - marketId: {}, {}", marketId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("브랜드 계좌 변경 — PG 파트너 갱신 준비 실패 - marketId: {}", marketId, e);
            return;
        }
        if (!gateway.isExternal() || !prepared.profile().existing() || prepared.upToDate()) {
            return;
        }
        try {
            PartnerResult result = gateway.ensure(prepared.profile());
            if (result.status() != PartnerStatus.FAILED) {
                writeTransaction.executeWithoutResult(tx -> link(prepared, result, LocalDateTime.now()));
            } else {
                log.warn("브랜드 계좌 변경 — PG 파트너 갱신 실패 - marketId: {}, {}", marketId, result.failReason());
            }
        } catch (RuntimeException e) {
            log.error("브랜드 계좌 변경 — PG 파트너 갱신 실패 - marketId: {}", marketId, e);
        }
    }

    // ------------------------------------------------------------------ 프로필(4-1)

    private Prepared prepare(Long settlementId, SettlementPayee payee) {
        Settlement settlement = settlementRepository.findDetailById(settlementId).orElseThrow();
        return switch (payee) {
            case BRAND -> prepareBrand(settlement.getMarket());
            case CREATOR -> prepareCreator(settlement.getCreator());
            case PLATFORM -> throw new IllegalArgumentException("플랫폼 몫은 파트너가 아니다");
        };
    }

    private Prepared prepareBrand(Market market) {
        Seller seller = market.getSeller();
        if (seller == null) {
            throw new ProfileIncomplete(PayoutBlockReason.PARTNER_NOT_READY, "마켓에 소유자(셀러)가 없다");
        }
        Account account = account(seller.getBankName(), seller.getAccountNumber(), seller.getAccountHolder());
        String email = blank(seller.getTaxEmail()) ? seller.getEmail() : seller.getTaxEmail();
        Contact contact = new Contact(firstNonBlank(seller.getRepresentativeName(), market.getMarketName()),
                seller.getPhoneNumber(), email);
        // 빠진 값(사업자등록번호 등)의 판정은 구현(포트원)이 한다 — SIMULATED 는 아무것도 막지 않는다.
        Business business = new Business(firstNonBlank(seller.getCompanyName(), market.getMarketName()),
                digits(seller.getBusinessRegistrationNumber()), seller.getRepresentativeName(),
                seller.getBusinessAddress(), seller.getBusinessCondition());
        String partnerId = SettlementPartnerIds.partnerId(gateway.partnerIdPrefix(), SettlementPayee.BRAND, market.getId());
        boolean existing = partnerId.equals(seller.getPortonePartnerId());
        PartnerProfile profile = new PartnerProfile(SettlementPayee.BRAND, market.getId(), partnerId,
                market.getMarketName(), contact, account, business, null, existing);
        String hash = SettlementPartnerIds.accountHash(account.bankCode(), account.accountNumber(), account.holder());
        boolean upToDate = existing && hash.equals(seller.getPortonePartnerAccountHash())
                && PartnerStatus.APPROVED.name().equals(seller.getPortonePartnerStatus());
        return new Prepared(profile, hash, upToDate, seller.getId(), null);
    }

    private Prepared prepareCreator(Creator creator) {
        Account account = account(creator.getBankName(), creator.getAccountNumber(), creator.getRealName());
        Contact contact = new Contact(firstNonBlank(creator.getRealName(), creator.getShowroomName()),
                creator.getPhoneNumber(), creator.getBusinessEmail());
        Business business = null;
        String birthdate = null;
        if (creator.getBusinessType() == CreatorBusinessType.BUSINESS) {
            business = new Business(firstNonBlank(creator.getShowroomName(), creator.getRealName()),
                    digits(creator.getBusinessRegistrationNumber()), creator.getRealName(), null, null);
        } else {
            // 비사업자 = 원천징수 대상자 — 생년월일이 필요하다(없으면 구현이 거절한다). 주민등록번호 앞자리로 보완하지 않는다(4-1).
            birthdate = blank(creator.getBirthday()) ? null : creator.getBirthday();
        }
        String partnerId = SettlementPartnerIds.partnerId(gateway.partnerIdPrefix(), SettlementPayee.CREATOR, creator.getId());
        boolean existing = partnerId.equals(creator.getPortonePartnerId());
        PartnerProfile profile = new PartnerProfile(SettlementPayee.CREATOR, creator.getId(), partnerId,
                creator.getShowroomName(), contact, account, business, birthdate, existing);
        String hash = SettlementPartnerIds.accountHash(account.bankCode(), account.accountNumber(), account.holder());
        boolean upToDate = existing && hash.equals(creator.getPortonePartnerAccountHash())
                && PartnerStatus.APPROVED.name().equals(creator.getPortonePartnerStatus());
        return new Prepared(profile, hash, upToDate, null, creator.getId());
    }

    /** 회원 정보의 은행 이름을 코드로 — 못 찾으면 코드 null(구현이 {@code UNSUPPORTED_BANK}). 계좌가 비어도 그대로(구현이 {@code ACCOUNT_MISSING}). */
    private Account account(String bankName, String accountNumber, String holder) {
        String code = blank(bankName) ? null
                : bankRepository.findByName(bankName.trim()).map(Bank::getCode).orElse(null);
        return new Account(code, bankName, digits(accountNumber), holder);
    }

    private void link(Prepared prepared, PartnerResult result, LocalDateTime now) {
        String status = result.status().name();
        String hash = result.isApproved() ? prepared.accountHash() : null;
        if (prepared.sellerId() != null) {
            marketRepository.findById(prepared.profile().refId()).map(Market::getSeller)
                    .ifPresent(seller -> seller.linkPortOnePartner(prepared.profile().partnerId(), status, hash, now));
        } else {
            creatorRepository.findById(prepared.creatorId())
                    .ifPresent(creator -> creator.linkPortOnePartner(prepared.profile().partnerId(), status, hash, now));
        }
    }

    // ------------------------------------------------------------------ 보류(4-3)

    private void block(Long settlementId, SettlementPayee payee, PartnerResult result) {
        String code = result.failCode() == null ? PayoutBlockReason.PARTNER_NOT_READY.name() : result.failCode();
        String reason = result.failReason() == null ? code : result.failReason();
        writeTransaction.executeWithoutResult(tx -> {
            SettlementPayout payout = payoutRepository.findBySettlementIdAndPayee(settlementId, payee).orElse(null);
            if (payout == null) {
                return;
            }
            boolean changed;
            if (payout.getStatus() == PayoutStatus.SCHEDULED) {
                changed = payoutRepository.blockWithReason(payout.getId(), code, reason) == 1;
            } else if (payout.getStatus() == PayoutStatus.BLOCKED && !code.equals(payout.getFailCode())) {
                // 이미 다른 사유(주민등록번호)로 보류 중 — 파트너 사유를 덧붙인다(재판정이 둘 다 본다).
                changed = payoutRepository.updateBlockReason(payout.getId(), code, reason) == 1;
            } else {
                changed = false;
            }
            if (changed) {
                LocalDateTime now = LocalDateTime.now();
                historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED,
                        "%s · %s · %s".formatted(payee.getLabel(), PayoutBlockReason.fromCode(code)
                                .map(PayoutBlockReason::getLabel).orElse(code), reason), now);
                notifier.payoutBlocked(settlementId, payee, code);
            }
        });
    }

    // ------------------------------------------------------------------ 보조

    private record Prepared(PartnerProfile profile, String accountHash, boolean upToDate, Long sellerId, Long creatorId) {
    }

    /** PG 를 부를 수 없는 프로필 — 사유는 {@link PayoutBlockReason} 이름으로 행에 남는다. */
    @Getter
    static final class ProfileIncomplete extends RuntimeException {
        private final PayoutBlockReason reason;

        ProfileIncomplete(PayoutBlockReason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return blank(first) ? second : first;
    }

    private static String digits(String value) {
        return value == null ? null : value.replaceAll("[^0-9]", "");
    }
}
