package showroomz.api.seller.connection.service;

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

    private final ConnectionRepository connectionRepository;
    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final CreatorRepository creatorRepository;

    /** §13-6 쇼룸명 검색 — 결과에 현재 연결 상태를 함께 실어 [요청] 버튼/상태 배지를 애초에 갈라 보여준다. */
    public PageResponse<ConnectionCreatorSearchItem> searchCreators(String sellerEmail, String keyword, PagingRequest pagingRequest) {
        Market market = getMyMarket(sellerEmail);
        String safeKeyword = keyword == null ? "" : keyword.trim();

        Page<ConnectionCreatorSearchItem> page = connectionRepository.searchConnectableCreators(
                market.getId(), safeKeyword, UserStatus.WITHDRAWN, pagingRequest.toPageable());
        return PageResponse.of(page);
    }

    /** §13-6 연결코드 확인 — 존재하지 않아도 예외가 아니라 found=false로 응답한다(정상적으로 자주 발생하는 입력 실수). */
    public ConnectionCodeCheckResponse checkConnectionCode(String sellerEmail, String code) {
        // getMyMarket으로 인증만 확인(인증된 브랜드만 조회 가능 — §13-6)
        getMyMarket(sellerEmail);

        if (code == null || code.isBlank()) {
            return ConnectionCodeCheckResponse.notFound();
        }
        String normalized = code.trim().toUpperCase();

        return creatorRepository.findByConnectionCode(normalized)
                .filter(this::isConnectable)
                .map(creator -> ConnectionCodeCheckResponse.found(creator.getId(), creator.getShowroomName()))
                .orElseGet(ConnectionCodeCheckResponse::notFound);
    }

    /** §13-6 연결 요청 — creatorId 또는 connectionCode 중 하나로 상대를 지정한다. */
    @Transactional
    public ConnectResponse requestConnection(String sellerEmail, ConnectRequest request) {
        Market market = getMyMarket(sellerEmail);
        Creator target = resolveTarget(request);

        if (!isConnectable(target)) {
            throw new BusinessException(ErrorCode.CONNECTION_TARGET_NOT_CONNECTABLE);
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
        return creator.getUser() != null && creator.getUser().getStatus() != UserStatus.WITHDRAWN;
    }

    private Market getMyMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}
