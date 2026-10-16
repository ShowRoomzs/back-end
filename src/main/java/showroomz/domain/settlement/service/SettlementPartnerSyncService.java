package showroomz.domain.settlement.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.seller.auth.repository.SellerRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 수취자 ↔ PG 셀러 동기화(44_토스페이먼츠_지급대행_연동_BE_설계서.md 4절). 확정 뒤(AfterCommit)에 두 수취자의 셀러를 보장하고,
 * 승인되지 않으면 확정은 그대로 둔 채 그 수취자 행만 {@code BLOCKED(사유)} 로 둔다 — 지급일에 등록 실패가 터지지 않게. 지급 배치 앞단과
 * {@code seller.changed} 웹훅이 다시 판정한다.
 *
 * <p>외부 호출은 트랜잭션 밖에서 하고, 읽기 · 쓰기는 각각 짧은 트랜잭션이다 — PG 지연 · 실패가 확정을 되돌리면 안 된다(0-11).
 * PG 가 발급한 셀러 id 는 회원 정보에 반드시 남긴다(0-5) — 참조 id 로는 조회할 수 없다.
 */
@Slf4j
@Service
public class SettlementPartnerSyncService {

    private final SettlementRepository settlementRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final MarketRepository marketRepository;
    private final SellerRepository sellerRepository;
    private final CreatorRepository creatorRepository;
    private final BankRepository bankRepository;
    private final SettlementPartnerGateway gateway;
    private final SettlementPayoutTransitions transitions;
    private final SettlementHistoryRecorder historyRecorder;
    private final SettlementNotifier notifier;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public SettlementPartnerSyncService(SettlementRepository settlementRepository,
                                        SettlementPayoutRepository payoutRepository, MarketRepository marketRepository,
                                        SellerRepository sellerRepository, CreatorRepository creatorRepository,
                                        BankRepository bankRepository, SettlementPartnerGateway gateway,
                                        SettlementPayoutTransitions transitions, SettlementHistoryRecorder historyRecorder,
                                        SettlementNotifier notifier, PlatformTransactionManager transactionManager) {
        this.settlementRepository = settlementRepository;
        this.payoutRepository = payoutRepository;
        this.marketRepository = marketRepository;
        this.sellerRepository = sellerRepository;
        this.creatorRepository = creatorRepository;
        this.bankRepository = bankRepository;
        this.gateway = gateway;
        this.transitions = transitions;
        this.historyRecorder = historyRecorder;
        this.notifier = notifier;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ 확정 뒤

    /** 확정 뒤 — 브랜드 · 인플루언서 셀러 보장. 승인되지 않은 수취자 행만 보류한다(4-3). 예외를 밖으로 내지 않는다. */
    public void ensureForSettlement(Long settlementId) {
        for (SettlementPayee payee : List.of(SettlementPayee.BRAND, SettlementPayee.CREATOR)) {
            PartnerResult result;
            try {
                result = ensure(settlementId, payee);
            } catch (RuntimeException e) {
                log.error("PG 셀러 동기화 실패 - settlementId: {}, payee: {}", settlementId, payee, e);
                result = PartnerResult.failed(PayoutBlockReason.PARTNER_NOT_READY.name(), "PG 호출 실패 — " + e.getMessage());
            }
            if (!result.isApproved()) {
                block(settlementId, payee, result);
            }
        }
    }

    /**
     * 수취자 1명의 셀러 보장 — 연결돼 있고 계좌가 같고 지급 가능 상태면 호출하지 않는다. 결과(PG 발급 id · 원시 상태)는 회원 정보에 기록한다.
     * 프로필을 만들 수 없는 사유는 PG 를 부르지 않고 실패로 돌려준다.
     */
    public PartnerResult ensure(Long settlementId, SettlementPayee payee) {
        Prepared prepared;
        try {
            prepared = readTransaction.execute(tx -> prepare(settlementId, payee));
        } catch (ProfileIncomplete e) {
            return PartnerResult.failed(e.reason.name(), e.getMessage());
        }
        if (prepared.upToDate()) {
            return PartnerResult.approved(prepared.profile().externalId(), prepared.rawStatus());
        }
        PartnerResult result = gateway.ensure(prepared.profile());
        LocalDateTime now = LocalDateTime.now();
        if (result.hasExternalId() && gateway.isExternal()) {
            writeTransaction.executeWithoutResult(tx -> link(prepared, result, now));
        }
        return result;
    }

    /** 정산계좌 변경 승인 — 이미 연결된 브랜드 셀러의 계좌를 즉시 갱신한다(4-3 · 4-4). 연결 전이면 다음 확정이 만든다. */
    public void onBrandAccountChanged(Long marketId) {
        Prepared prepared;
        try {
            prepared = readTransaction.execute(tx -> prepareBrand(marketRepository.findById(marketId).orElseThrow()));
        } catch (ProfileIncomplete e) {
            log.warn("브랜드 계좌 변경 — PG 셀러 갱신 보류 - marketId: {}, {}", marketId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("브랜드 계좌 변경 — PG 셀러 갱신 준비 실패 - marketId: {}", marketId, e);
            return;
        }
        if (!gateway.isExternal() || !prepared.profile().existing() || prepared.upToDate()) {
            return;
        }
        try {
            PartnerResult result = gateway.ensure(prepared.profile());
            if (result.hasExternalId()) {
                writeTransaction.executeWithoutResult(tx -> link(prepared, result, LocalDateTime.now()));
            } else {
                log.warn("브랜드 계좌 변경 — PG 셀러 갱신 실패 - marketId: {}, {}", marketId, result.failReason());
            }
        } catch (RuntimeException e) {
            log.error("브랜드 계좌 변경 — PG 셀러 갱신 실패 - marketId: {}", marketId, e);
        }
    }

    /**
     * 인플루언서 정산 계좌 확인(50 설계서 1-2 · 6-3) — 셀러 등록 · 수정의 계좌 검증을 지급일이 아니라 변경 시점에 당겨 온다. 연결이 없으면
     * 셀러를 <b>만든다</b>(구현 계획서 D-6). 연결 · 계좌 · 상태가 이미 최신이면 PG 를 부르지 않는다. {@code SIMULATED}(Noop)는 승인으로 본다.
     * 결과를 받은 그 계좌({@code bankName} · {@code accountNumber})를 함께 돌려준다 — 그 사이 계좌가 또 바뀌었으면 호출자가 결과를 버린다.
     * 예외를 밖으로 내지 않는다.
     */
    public CreatorAccountCheck verifyCreatorAccount(Long creatorId) {
        Prepared prepared;
        String[] checked = new String[2];
        try {
            prepared = readTransaction.execute(tx -> {
                Creator creator = creatorRepository.findById(creatorId).orElseThrow();
                checked[0] = creator.getBankName();
                checked[1] = creator.getAccountNumber();
                return prepareCreator(creator);
            });
        } catch (ProfileIncomplete e) {
            return new CreatorAccountCheck(PartnerResult.failed(e.reason.name(), e.getMessage()), checked[0], checked[1]);
        } catch (RuntimeException e) {
            log.error("인플루언서 계좌 확인 — 준비 실패 - creatorId: {}", creatorId, e);
            return new CreatorAccountCheck(PartnerResult.failed(PayoutBlockReason.PARTNER_NOT_READY.name(),
                    "준비 실패 — " + e.getMessage()), checked[0], checked[1]);
        }
        if (prepared.upToDate()) {
            return new CreatorAccountCheck(PartnerResult.approved(prepared.profile().externalId(), prepared.rawStatus()),
                    checked[0], checked[1]);
        }
        try {
            PartnerResult result = gateway.ensure(prepared.profile());
            if (result.hasExternalId() && gateway.isExternal()) {
                writeTransaction.executeWithoutResult(tx -> link(prepared, result, LocalDateTime.now()));
            }
            return new CreatorAccountCheck(result, checked[0], checked[1]);
        } catch (RuntimeException e) {
            log.error("인플루언서 계좌 확인 — PG 호출 실패 - creatorId: {}", creatorId, e);
            return new CreatorAccountCheck(PartnerResult.failed(PayoutBlockReason.PARTNER_NOT_READY.name(),
                    "PG 호출 실패 — " + e.getMessage()), checked[0], checked[1]);
        }
    }

    /** 계좌 확인 결과와 그 결과가 가리키는 계좌. */
    public record CreatorAccountCheck(PartnerResult result, String bankName, String accountNumber) {
    }

    // ------------------------------------------------------------------ 웹훅 seller.changed(4-3)

    /**
     * 셀러 상태 변경 — 회원 정보의 원시 상태를 갱신하고, 지급 가능이 됐으면 셀러 사유 보류 행을 <b>즉시</b> 예정으로 · 대기(KYC) 가 됐으면
     * 예정 행을 보류로. 우리 셀러가 아니면 false.
     */
    public boolean onPartnerStatusChanged(String externalId, String rawStatus, LocalDateTime now) {
        if (externalId == null || rawStatus == null) {
            return false;
        }
        Linked linked = writeTransaction.execute(tx -> {
            Seller seller = sellerRepository.findByPgPartnerId(externalId).orElse(null);
            if (seller != null) {
                seller.updatePgPartnerStatus(rawStatus, now);
                Long marketId = marketRepository.findBySeller(seller).map(Market::getId).orElse(null);
                return new Linked(SettlementPayee.BRAND, marketId);
            }
            Creator creator = creatorRepository.findByPgPartnerId(externalId).orElse(null);
            if (creator != null) {
                creator.updatePgPartnerStatus(rawStatus, now);
                return new Linked(SettlementPayee.CREATOR, creator.getId());
            }
            return null;
        });
        if (linked == null || linked.refId() == null) {
            return linked != null;
        }
        PartnerStatus status = gateway.statusOf(rawStatus);
        LocalDate today = now.toLocalDate();
        if (status == PartnerStatus.APPROVED) {
            List<SettlementPayout> blocked = readTransaction.execute(tx -> linked.payee() == SettlementPayee.BRAND
                    ? payoutRepository.findBlockedForMarket(linked.refId(), PayoutBlockReason.PARTNER_CODES)
                    : payoutRepository.findBlockedForCreator(linked.refId(), PayoutBlockReason.PARTNER_CODES));
            for (SettlementPayout payout : blocked) {
                if (transitions.releasePartnerBlocked(payout.getSettlementId(), payout.getId(), payout.getPayee(), today)) {
                    log.info("PG 셀러 승인 — 보류 해제 - payoutId: {}", payout.getId());
                }
            }
        } else if (status == PartnerStatus.PENDING) {
            String code = gateway.blockCodeOf(rawStatus);
            List<SettlementPayout> scheduled = readTransaction.execute(tx -> linked.payee() == SettlementPayee.BRAND
                    ? payoutRepository.findScheduledForMarket(linked.refId())
                    : payoutRepository.findScheduledForCreator(linked.refId()));
            for (SettlementPayout payout : scheduled) {
                transitions.blockScheduled(payout.getSettlementId(), payout.getId(), payout.getPayee(), code,
                        "PG 셀러 상태 " + rawStatus, now);
            }
        }
        return true;
    }

    private record Linked(SettlementPayee payee, Long refId) {
    }

    // ------------------------------------------------------------------ 프로필(4-1)

    private Prepared prepare(Long settlementId, SettlementPayee payee) {
        Settlement settlement = settlementRepository.findDetailById(settlementId).orElseThrow();
        return switch (payee) {
            case BRAND -> prepareBrand(settlement.getMarket());
            case CREATOR -> prepareCreator(settlement.getCreator());
            case PLATFORM -> throw new IllegalArgumentException("플랫폼 몫은 셀러가 아니다");
        };
    }

    private Prepared prepareBrand(Market market) {
        Seller seller = market.getSeller();
        if (seller == null) {
            throw new ProfileIncomplete(PayoutBlockReason.PARTNER_NOT_READY, "마켓에 소유자(셀러)가 없다");
        }
        Account account = account(seller.getBankName(), seller.getAccountNumber(), seller.getAccountHolder());
        String email = blank(seller.getTaxEmail()) ? seller.getEmail() : seller.getTaxEmail();
        // 개인사업자는 이 번호로 본인인증 문자가 간다(0-8 · Q2) — 대표자 연락처 우선.
        String phone = firstNonBlank(seller.getRepresentativeContact(), seller.getPhoneNumber());
        Contact contact = new Contact(firstNonBlank(seller.getRepresentativeName(), market.getMarketName()), phone, email);
        String brn = digits(seller.getBusinessRegistrationNumber());
        Business business = new Business(firstNonBlank(seller.getCompanyName(), market.getMarketName()), brn,
                seller.getRepresentativeName(), seller.getBusinessAddress(), seller.getBusinessCondition(),
                SettlementPartnerIds.isCorporate(brn, seller.getBusinessType()));
        String ref = SettlementPartnerIds.partnerRef(gateway.partnerRefPrefix(), SettlementPayee.BRAND, market.getId());
        boolean existing = linked(seller.getPgPartnerId(), seller.getPgPartnerRef(), seller.getPgPartnerEnv(), ref);
        PartnerProfile profile = new PartnerProfile(SettlementPayee.BRAND, market.getId(), ref,
                existing ? seller.getPgPartnerId() : null, market.getMarketName(), contact, account, business, null, existing);
        String hash = SettlementPartnerIds.accountHash(account.bankCode(), account.accountNumber(), account.holder());
        boolean upToDate = existing && hash.equals(seller.getPgPartnerAccountHash())
                && gateway.statusOf(seller.getPgPartnerStatus()) == PartnerStatus.APPROVED;
        return new Prepared(profile, hash, upToDate, seller.getPgPartnerStatus(), seller.getId(), null);
    }

    private Prepared prepareCreator(Creator creator) {
        Account account = account(creator.getBankName(), creator.getAccountNumber(), creator.getRealName());
        Contact contact = new Contact(firstNonBlank(creator.getRealName(), creator.getShowroomName()),
                creator.getPhoneNumber(), creator.getBusinessEmail());
        Business business = null;
        if (creator.getBusinessType() == CreatorBusinessType.BUSINESS) {
            String brn = digits(creator.getBusinessRegistrationNumber());
            business = new Business(firstNonBlank(creator.getShowroomName(), creator.getRealName()), brn,
                    creator.getRealName(), null, null, SettlementPartnerIds.isCorporate(brn, null));
        }
        String ref = SettlementPartnerIds.partnerRef(gateway.partnerRefPrefix(), SettlementPayee.CREATOR, creator.getId());
        boolean existing = linked(creator.getPgPartnerId(), creator.getPgPartnerRef(), creator.getPgPartnerEnv(), ref);
        PartnerProfile profile = new PartnerProfile(SettlementPayee.CREATOR, creator.getId(), ref,
                existing ? creator.getPgPartnerId() : null, creator.getShowroomName(), contact, account, business,
                blank(creator.getBirthday()) ? null : creator.getBirthday(), existing);
        String hash = SettlementPartnerIds.accountHash(account.bankCode(), account.accountNumber(), account.holder());
        boolean upToDate = existing && hash.equals(creator.getPgPartnerAccountHash())
                && gateway.statusOf(creator.getPgPartnerStatus()) == PartnerStatus.APPROVED;
        return new Prepared(profile, hash, upToDate, creator.getPgPartnerStatus(), null, creator.getId());
    }

    /** 연결이 있는가 — PG 발급 id · 같은 참조 id · 같은 환경(테스트 셀러가 운영에 섞이지 않게 · 3-1). */
    private boolean linked(String externalId, String storedRef, String storedEnv, String ref) {
        return !blank(externalId) && ref.equals(storedRef) && gateway.env().equals(storedEnv);
    }

    /** 회원 정보의 은행 이름을 코드로 — 못 찾으면 코드 null(구현이 {@code UNSUPPORTED_BANK}). 계좌가 비어도 그대로(구현이 {@code ACCOUNT_MISSING}). */
    private Account account(String bankName, String accountNumber, String holder) {
        String code = blank(bankName) ? null
                : bankRepository.findByName(bankName.trim()).map(Bank::getCode).orElse(null);
        return new Account(code, bankName, digits(accountNumber), holder);
    }

    private void link(Prepared prepared, PartnerResult result, LocalDateTime now) {
        String externalId = result.hasExternalId() ? result.externalId() : prepared.profile().externalId();
        String hash = result.isApproved() ? prepared.accountHash() : null;
        String ref = prepared.profile().partnerRef();
        String env = gateway.env();
        if (prepared.sellerId() != null) {
            marketRepository.findById(prepared.profile().refId()).map(Market::getSeller)
                    .ifPresent(seller -> seller.linkPgPartner(externalId, ref, env, result.rawStatus(), hash, now));
        } else {
            creatorRepository.findById(prepared.creatorId())
                    .ifPresent(creator -> creator.linkPgPartner(externalId, ref, env, result.rawStatus(), hash, now));
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
                // 이미 다른 사유(주민등록번호)로 보류 중 — 셀러 사유를 덧붙인다(재판정이 둘 다 본다).
                changed = payoutRepository.updateBlockReason(payout.getId(), code, reason) == 1;
            } else {
                changed = false;
            }
            if (changed) {
                LocalDateTime now = LocalDateTime.now();
                historyRecorder.recordBySystem(settlementId, SettlementEventType.PAYOUT_BLOCKED,
                        "%s · %s · %s".formatted(payee.getLabel(), PayoutBlockReason.fromCode(code)
                                .map(PayoutBlockReason::getLabel).orElse(code), reason), now);
                if (PayoutBlockReason.PAYEE_ACTION_CODES.contains(code)) {
                    // 본인인증 · KYC — 수취자 본인이 푼다. 개인 수취자의 첫 등록은 늘 여기를 지나므로 운영자는 부르지 않는다.
                    notifier.partnerVerificationRequired(settlementId, payee, code);
                } else {
                    notifier.payoutBlocked(settlementId, payee, code);
                }
            }
        });
    }

    // ------------------------------------------------------------------ 보조

    private record Prepared(PartnerProfile profile, String accountHash, boolean upToDate, String rawStatus, Long sellerId,
                            Long creatorId) {
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
