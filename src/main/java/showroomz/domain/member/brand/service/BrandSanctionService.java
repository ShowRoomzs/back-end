package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.brand.entity.BrandSanction;
import showroomz.domain.member.brand.repository.BrandSanctionRepository;
import showroomz.domain.member.brand.type.BrandAdminEventType;
import showroomz.domain.member.brand.type.BrandSuspensionReason;
import showroomz.domain.member.brand.type.SanctionReleaseReason;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageSanctionChannelGateway;
import showroomz.domain.message.service.MessageSanctionChannelGateway.SanctionKind;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.domain.settlement.service.SettlementSanctionHook;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 브랜드 계정 정지 · 해제(§51-1 ~ 51-4 · 52 설계서 3절). 마켓 행을 잠그고 한 트랜잭션에서 정지 행 · 회원 상태 · 지급 보류 · 이력 ·
 * 운영팀 스레드 카드를 함께 바꾼다 — 카드도 조치와 같이 롤백된다(계약 직권 취소 카드와 같은 방식). 알림만 커밋 뒤다.
 *
 * <p>운영자 이름은 호출자(어드민 서비스)가 확인해 넘긴다 — 도메인이 api 의 운영자 해석기를 부르지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BrandSanctionService {

    private final MarketRepository marketRepository;
    private final BrandSanctionRepository sanctionRepository;
    private final BrandAdminHistoryRecorder historyRecorder;
    private final SettlementSanctionHook settlementHook;
    private final OperatorChannelService operatorChannelService;
    private final MessageSanctionChannelGateway channelGateway;
    private final BrandMemberNotifier notifier;

    public BrandSanction suspend(Long marketId, BrandSuspensionReason reason, String reasonDetail, Long operatorId,
                                 String operatorName, LocalDateTime now) {
        if (reason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "정지 사유를 선택해 주세요.");
        }
        String detail = normalize(reasonDetail);
        if (reason.isDetailRequired() && detail == null) {
            throw new BusinessException(ErrorCode.BRAND_SUSPEND_DETAIL_REQUIRED);
        }
        Market market = lockBrand(marketId);
        if (market.getStatus() == MarketStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.BRAND_WITHDRAWN);
        }
        if (market.getStatus() == MarketStatus.SUSPENDED || sanctionRepository.existsByMarketIdAndReleasedAtIsNull(marketId)) {
            throw new BusinessException(ErrorCode.BRAND_ALREADY_SUSPENDED);
        }

        BrandSanction sanction = sanctionRepository.save(
                BrandSanction.suspend(marketId, reason, detail, operatorId, operatorName, now));
        market.setStatus(MarketStatus.SUSPENDED);
        if (sanction.isPayoutHeld()) {
            settlementHook.holdBrandPayouts(marketId, now);
        }
        historyRecorder.byAdmin(marketId, BrandAdminEventType.SUSPENDED, operatorId, operatorName,
                sanction.reasonText(), BrandAdminHistoryRecorder.REF_SANCTION, sanction.getId(), now);

        MessageThread channel = operatorChannelService.requireMarketChannel(market);
        sanction.recordSuspendCard(channelGateway.postSuspendedCard(channel, SanctionKind.BRAND, sanction.getId(),
                sanction.reasonText(), now, operatorId));
        AfterCommit.run(() -> notifier.suspended(marketId, reason.getLabel()));
        log.info("브랜드 정지 - marketId: {}, reason: {}, payoutHeld: {}", marketId, reason, sanction.isPayoutHeld());
        return sanction;
    }

    /**
     * 해제 — 회원 상세에서만(§51-4). 정지 행 없이 정지 상태인 마켓(기획 제외 컨트롤러로 바꾼 경우 · 52 설계서 11절 #1)도 풀 수 있어야 한다 —
     * 그때는 상태만 되돌리고 지급 · 카드는 건드릴 것이 없다.
     */
    public BrandSanction release(Long marketId, SanctionReleaseReason reason, String note, Long operatorId,
                                 String operatorName, LocalDateTime now) {
        SanctionReleaseReason resolved = reason == null ? SanctionReleaseReason.RESOLVED : reason;
        if (resolved == SanctionReleaseReason.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴 종결은 운영자가 고를 수 있는 해제 사유가 아닙니다.");
        }
        Market market = lockBrand(marketId);
        if (market.getStatus() != MarketStatus.SUSPENDED) {
            throw new BusinessException(market.getStatus() == MarketStatus.WITHDRAWN
                    ? ErrorCode.BRAND_WITHDRAWN : ErrorCode.BRAND_NOT_SUSPENDED);
        }
        BrandSanction sanction = sanctionRepository.findByMarketIdAndReleasedAtIsNull(marketId).orElse(null);
        String text = releaseText(resolved, normalize(note));
        market.setStatus(MarketStatus.ACTIVE);
        if (sanction != null) {
            sanction.release(resolved, normalize(note), operatorId, operatorName, now);
            if (sanction.isPayoutHeld()) {
                settlementHook.releaseBrandPayouts(marketId, now.toLocalDate(), now);
            }
            MessageThread channel = operatorChannelService.requireMarketChannel(market);
            sanction.recordReleaseCard(channelGateway.postReleasedCard(channel, SanctionKind.BRAND, sanction.getId(),
                    text, now, operatorId));
        }
        historyRecorder.byAdmin(marketId, BrandAdminEventType.SUSPENSION_RELEASED, operatorId, operatorName, text,
                BrandAdminHistoryRecorder.REF_SANCTION, sanction == null ? null : sanction.getId(), now);
        AfterCommit.run(() -> notifier.released(marketId));
        log.info("브랜드 정지 해제 - marketId: {}, reason: {}", marketId, resolved);
        return sanction;
    }

    /**
     * 탈퇴 승인이 열린 정지를 종결한다(52 설계서 4-3 ④) — 호출자 트랜잭션 · 마켓 잠금 안. 해제 카드 · 알림은 없다(해지된 계정이다).
     * 지급 정지 중이었다면 탈퇴 조건 ④가 이미 막았으므로 풀 지급도 없다.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void closeByWithdrawal(Long marketId, LocalDateTime now) {
        sanctionRepository.findByMarketIdAndReleasedAtIsNull(marketId).ifPresent(sanction -> {
            sanction.release(SanctionReleaseReason.WITHDRAWN, null, null, null, now);
            historyRecorder.bySystem(marketId, BrandAdminEventType.SUSPENSION_RELEASED,
                    SanctionReleaseReason.WITHDRAWN.getLabel(), BrandAdminHistoryRecorder.REF_SANCTION,
                    sanction.getId(), now);
        });
    }

    /** 입점이 끝난 브랜드만 회원 관리 대상이다(52 설계서 0-1) — 그 밖은 존재를 알리지 않는다. */
    public Market lockBrand(Long marketId) {
        Market market = marketRepository.findForUpdate(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        Seller seller = market.getSeller();
        if (seller == null || seller.getRoleType() != RoleType.SELLER || seller.getStatus() != SellerStatus.APPROVED) {
            throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
        }
        return market;
    }

    private static String releaseText(SanctionReleaseReason reason, String note) {
        return note == null ? reason.getLabel() : reason.getLabel() + " — " + note;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
