package showroomz.api.creator.connection.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.creator.connection.dto.ConnectionCodeResponse;
import showroomz.api.creator.connection.dto.ConnectionRequestItem;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.ConnectionCodeGenerator;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorConnectionService {

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final MessageThreadService messageThreadService;

    /**
     * §14-3 요청함 탭 — 받은 연결 요청 목록(브랜드가 발신 주체, §13-6).
     * keyword는 좌측 목록 상단의 "브랜드명 검색"(S12·S13) — 비어 있으면 전체를 내려준다.
     */
    public PageResponse<ConnectionRequestItem> getRequests(String creatorEmail, String keyword,
                                                           PagingRequest pagingRequest) {
        Creator creator = getMyCreator(creatorEmail);

        Page<ConnectionRequestItem> page = connectionRepository
                .findRequestsByCreator(creator, ConnectionStatus.REQUESTED, normalizeKeyword(keyword),
                        pagingRequest.toPageable())
                .map(c -> new ConnectionRequestItem(
                        c.getId(),
                        c.getMarket().getId(),
                        c.getMarket().getMarketName(),
                        c.getMarket().getMarketImageUrl(),
                        mainCategoryNameOf(c.getMarket()),
                        c.getRequestedAt()));
        return PageResponse.of(page);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /** 대표 카테고리는 선택 입력이라 미설정 브랜드가 존재한다 — S12 카드는 이 값이 없으면 요청 시각만 표기한다. */
    private static String mainCategoryNameOf(Market market) {
        return market.getMainCategory() == null ? null : market.getMainCategory().getName();
    }

    /** §14-4 수락 — 연결됨으로 전이 + 스레드 활성화(신규 생성 또는 DORMANT→OPEN, §13-4). */
    @Transactional
    public void accept(String creatorEmail, Long connectionId) {
        Connection connection = getMyRequestedConnection(creatorEmail, connectionId);
        connection.markConnected();
        messageThreadService.activateThread(connection);
    }

    /** §14-4 거절 — 해제(거절) 상태로 전이. */
    @Transactional
    public void reject(String creatorEmail, Long connectionId) {
        Connection connection = getMyRequestedConnection(creatorEmail, connectionId);
        connection.markRejected();
    }

    public ConnectionCodeResponse getMyConnectionCode(String creatorEmail) {
        Creator creator = getMyCreator(creatorEmail);
        return new ConnectionCodeResponse(creator.getConnectionCode(), creator.getConnectionCodeIssuedAt());
    }

    /** §13-6 — 인플루언서가 원하면 재발급 가능(쇼룸 스튜디오 UI는 §14-8 미결, BE 선행 구현). */
    @Transactional
    public ConnectionCodeResponse reissueConnectionCode(String creatorEmail) {
        Creator creator = getMyCreator(creatorEmail);
        String newCode = ConnectionCodeGenerator.generateUnique(creatorRepository::existsByConnectionCode);
        creator.reissueConnectionCode(newCode);
        return new ConnectionCodeResponse(creator.getConnectionCode(), creator.getConnectionCodeIssuedAt());
    }

    private Connection getMyRequestedConnection(String creatorEmail, Long connectionId) {
        Creator creator = getMyCreator(creatorEmail);
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONNECTION_NOT_FOUND));

        if (connection.getType() != ConnectionType.PAIR || !connection.getCreator().getId().equals(creator.getId())) {
            throw new BusinessException(ErrorCode.CONNECTION_ACCESS_DENIED);
        }
        if (connection.getStatus() != ConnectionStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.CONNECTION_INVALID_STATUS);
        }
        return connection;
    }

    private Creator getMyCreator(String creatorEmail) {
        Users user = userRepository.findByUsername(creatorEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
