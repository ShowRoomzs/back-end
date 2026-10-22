package showroomz.api.seller.connection.service;

import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.service.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.connection.dto.ConnectRequest;
import showroomz.api.seller.connection.dto.ConnectResponse;
import showroomz.api.seller.connection.dto.ConnectionCodeCheckResponse;
import showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.brand.service.BrandSanctionGuard;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerConnectionService {

    private final NotificationPublisher notificationPublisher;
    private final ConnectionRepository connectionRepository;
    private final BrandSanctionGuard brandSanctionGuard;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;

    /**
     * §13-6 쇼룸명 검색 — 결과에 현재 연결 상태를 함께 실어 [요청] 버튼/상태 배지를 애초에 갈라 보여준다.
     * 검색어는 필수다(1021 결정 노트 기-23 — 인플루언서 탐색은 베타 제외 · 연결 요청은 쇼룸명 검색 · 연결코드만). 빈 검색어로
     * 전체 목록을 훑는 길을 닫는다.
     */
    public PageResponse<ConnectionCreatorSearchItem> searchCreators(String sellerEmail, String keyword, PagingRequest pagingRequest) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "검색할 쇼룸명을 입력해 주세요.");
        }
        Market market = getMyMarket(sellerEmail);

        Page<ConnectionCreatorSearchItem> page = connectionRepository.searchConnectableCreators(
                market.getId(), safeKeyword, UserStatus.WITHDRAWN, pagingRequest.toPageable());
        return PageResponse.of(page);
    }

    /**
     * §13-6 연결코드 확인 — 존재하지 않아도 예외가 아니라 found=false로 응답한다(정상적으로 자주 발생하는 입력 실수).
     * 검색(B1·B4)과 마찬가지로 현재 연결 상태를 함께 내려, 이미 연결됨/요청중인 상대는 [요청 보내기]를
     * 눌러 409를 받는 대신 화면에서 먼저 막을 수 있게 한다.
     */
    public ConnectionCodeCheckResponse checkConnectionCode(String sellerEmail, String code) {
        Market market = getMyMarket(sellerEmail);

        if (code == null || code.isBlank()) {
            return ConnectionCodeCheckResponse.notFound();
        }
        String normalized = code.trim().toUpperCase();

        return creatorRepository.findByConnectionCode(normalized)
                .filter(this::isConnectable)
                .map(creator -> ConnectionCodeCheckResponse.found(
                        creator.getId(),
                        creator.getShowroomName(),
                        creator.getFollowerCount(),
                        creator.getUser() == null ? null : creator.getUser().getProfileImageUrl(),
                        activeStatusOf(market, creator),
                        creator.getStatus() != showroomz.domain.member.creator.type.CreatorStatus.SUSPENDED))
                .orElseGet(ConnectionCodeCheckResponse::notFound);
    }

    /**
     * 중복 요청을 막아야 하는 상태만 돌려준다 — REJECTED/DISCONNECTED 이력은 재요청이 허용되므로
     * (requestConnection이 같은 행을 재사용한다) 화면에는 "이력 없음"과 똑같이 보여야 한다.
     */
    private ConnectionStatus activeStatusOf(Market market, Creator creator) {
        return connectionRepository.findByTypeAndMarketAndCreator(ConnectionType.PAIR, market, creator)
                .map(Connection::getStatus)
                .filter(status -> status == ConnectionStatus.REQUESTED || status == ConnectionStatus.CONNECTED)
                .orElse(null);
    }

    /** §13-6 연결 요청 — creatorId 또는 connectionCode 중 하나로 상대를 지정한다. */
    @Transactional
    public ConnectResponse requestConnection(String sellerEmail, ConnectRequest request) {
        Market market = getMyMarket(sellerEmail);
        // 정지 중에는 새 연결을 요청하지 않는다(§51-1 · 52 설계서 7절).
        brandSanctionGuard.requireNotSuspended(market,
                showroomz.domain.member.brand.type.BrandBlockedAction.CONNECTION_REQUEST);
        Creator target = resolveTarget(request);

        if (!isConnectable(target)) {
            throw new BusinessException(ErrorCode.CONNECTION_TARGET_NOT_CONNECTABLE);
        }
        // 정지된 쇼룸에는 새 연결을 요청하지 않는다 — 정지 사실은 알리지 않는다(ui-partner-15 rev.2 · 1017 설계서 8-6).
        if (target.getStatus() == showroomz.domain.member.creator.type.CreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.CREATOR_COUNTERPARTY_SUSPENDED, "현재 연결 요청을 받을 수 없는 쇼룸입니다.");
        }

        Connection connection = connectionRepository
                .findByTypeAndMarketAndCreator(ConnectionType.PAIR, market, target)
                .map(existing -> {
                    if (existing.getStatus() == ConnectionStatus.CONNECTED
                            || existing.getStatus() == ConnectionStatus.REQUESTED) {
                        throw new BusinessException(ErrorCode.CONNECTION_ALREADY_EXISTS);
                    }
                    // REJECTED/DISCONNECTED였던 행을 재사용 — 같은 쌍은 대화 기록을 보존한 채로 이어진다(§13-4).
                    existing.markRequested();
                    return existing;
                })
                .orElseGet(() -> connectionRepository.save(Connection.requestPair(market, target)));

        // 스튜디오 알림 「퓨어랩이 연결을 요청했어요」(14 H1a) — 연결 요청함으로 간다.
        notificationPublisher.publish(NotificationCommand.toCreator(target.getId(), NotificationType.CONNECTION_REQUESTED)
                .actorType(NotificationActorType.BRAND).actorName(market.getMarketName())
                .title(market.getMarketName() + "이 연결을 요청했어요 · 수락하면 브랜드가 계약을 보낼 수 있어요")
                .emphasis(market.getMarketName())
                .targetType(NotificationTargetType.CONNECTION_REQUESTS).targetId(connection.getId()).build());
        return new ConnectResponse(connection.getId(), connection.getStatus());
    }

    private Creator resolveTarget(ConnectRequest request) {
        boolean hasCreatorId = request.getCreatorId() != null;
        boolean hasCode = request.getConnectionCode() != null && !request.getConnectionCode().isBlank();

        if (hasCreatorId == hasCode) {
            // 둘 다 없거나 둘 다 있으면 안 됨
            throw new BusinessException(hasCreatorId
                    ? ErrorCode.CONNECTION_TARGET_AMBIGUOUS
                    : ErrorCode.CONNECTION_TARGET_REQUIRED);
        }

        if (hasCreatorId) {
            return creatorRepository.findById(request.getCreatorId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        }
        String normalized = request.getConnectionCode().trim().toUpperCase();
        return creatorRepository.findByConnectionCode(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    private boolean isConnectable(Creator creator) {
        // 인플루언서 탈퇴(스튜디오 자격 해지 · 53 설계서 4-4)는 소비자 계정 상태와 다른 축이다 — 둘 다 본다.
        return creator.getUser() != null && creator.getUser().getStatus() != UserStatus.WITHDRAWN
                && !creator.isWithdrawn();
    }

    private Market getMyMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}
