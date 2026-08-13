package showroomz.domain.connection.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.type.UserStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    Optional<Connection> findByTypeAndMarketAndCreator(ConnectionType type, Market market, Creator creator);

    Optional<Connection> findByTypeAndMarket(ConnectionType type, Market market);

    Optional<Connection> findByTypeAndCreator(ConnectionType type, Creator creator);

    /**
     * §13-6 쇼룸명 검색 — 상대별 현재 연결 상태(이 브랜드 기준)를 함께 내려준다.
     * status는 <b>REQUESTED/CONNECTED일 때만</b> 채워지고 그 외에는 null이다 — 시안(B1·B4)의 배지는
     * "요청중"·"연결됨" 두 가지뿐이고, REJECTED/DISCONNECTED 이력은 재요청이 허용되므로
     * (requestConnection이 같은 행을 재사용한다) 이력이 없는 상대와 똑같이 [요청] 버튼이 떠야 한다.
     * 탈퇴한 인플루언서는 검색 결과에서 제외한다.
     */
    @Query("SELECT new showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem(" +
           "cr.id, cr.showroomName, cr.followerCount, cr.user.profileImageUrl, c.status) " +
           "FROM Creator cr " +
           "LEFT JOIN Connection c ON c.creator = cr AND c.type = showroomz.domain.connection.type.ConnectionType.PAIR " +
           "    AND c.market.id = :marketId " +
           "    AND c.status IN (showroomz.domain.connection.type.ConnectionStatus.REQUESTED, " +
           "                     showroomz.domain.connection.type.ConnectionStatus.CONNECTED) " +
           "WHERE cr.showroomName LIKE CONCAT('%', :keyword, '%') " +
           "AND cr.user.status <> :withdrawnStatus " +
           "ORDER BY cr.showroomName ASC")
    Page<ConnectionCreatorSearchItem> searchConnectableCreators(
            @Param("marketId") Long marketId,
            @Param("keyword") String keyword,
            @Param("withdrawnStatus") UserStatus withdrawnStatus,
            Pageable pageable);

    /**
     * §14-3 요청함 목록 — 브랜드 정보까지 한 번에 가져온다(N+1 방지).
     * statuses로 미처리(REQUESTED)만 또는 요청·수락·거절 전체를 걸러낸다.
     * keyword는 좌측 목록 상단의 "브랜드명 검색" 입력으로, 연결됨 탭과 동일하게 브랜드명 부분 일치다.
     */
    @Query("SELECT c FROM Connection c JOIN FETCH c.market m " +
           "WHERE c.type = showroomz.domain.connection.type.ConnectionType.PAIR " +
           "AND c.creator = :creator " +
           "AND c.status IN :statuses " +
           "AND (:keyword IS NULL OR :keyword = '' OR m.marketName LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY c.requestedAt DESC")
    Page<Connection> findRequestsByCreator(@Param("creator") Creator creator,
                                            @Param("statuses") Collection<ConnectionStatus> statuses,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    long countByTypeAndCreatorAndStatus(ConnectionType type, Creator creator, ConnectionStatus status);

    /**
     * 진행 중 공구를 보유한 쇼룸 판별 (C2 팔로잉 목록의 아바타 링).
     * 공구 상태는 상품(product.group_buy_status)에 있으므로, 연결(CONNECTED)된 브랜드의 진열 중 상품 가운데
     * 공구 진행중(IN_PROGRESS)이 하나라도 있으면 그 쇼룸을 진행 중으로 본다.
     */
    @Query("SELECT DISTINCT c.creator.id FROM Connection c, Product p " +
           "WHERE p.market = c.market " +
           "AND c.creator.id IN :creatorIds " +
           "AND c.status = showroomz.domain.connection.type.ConnectionStatus.CONNECTED " +
           "AND p.groupBuyStatus = showroomz.domain.product.type.ProductGroupBuyStatus.IN_PROGRESS " +
           "AND p.displayStatus = showroomz.domain.product.type.ProductDisplayStatus.DISPLAY")
    List<Long> findCreatorIdsWithOngoingGroupBuy(@Param("creatorIds") Collection<Long> creatorIds);
}
