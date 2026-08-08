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

import java.util.Optional;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    Optional<Connection> findByTypeAndMarketAndCreator(ConnectionType type, Market market, Creator creator);

    Optional<Connection> findByTypeAndMarket(ConnectionType type, Market market);

    Optional<Connection> findByTypeAndCreator(ConnectionType type, Creator creator);

    /**
     * §13-6 쇼룸명 검색 — 상대별 현재 연결 상태(이 브랜드 기준)를 함께 내려준다.
     * 연결 이력이 없으면 status는 null(요청 가능), 있으면 최신 상태(REQUESTED/CONNECTED/REJECTED/DISCONNECTED).
     * 탈퇴한 인플루언서는 검색 결과에서 제외한다.
     */
    @Query("SELECT new showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem(" +
           "cr.id, cr.showroomName, cr.followerCount, cr.user.profileImageUrl, c.status) " +
           "FROM Creator cr " +
           "LEFT JOIN Connection c ON c.creator = cr AND c.type = showroomz.domain.connection.type.ConnectionType.PAIR " +
           "    AND c.market.id = :marketId " +
           "WHERE cr.showroomName LIKE CONCAT('%', :keyword, '%') " +
           "AND cr.user.status <> :withdrawnStatus " +
           "ORDER BY cr.showroomName ASC")
    Page<ConnectionCreatorSearchItem> searchConnectableCreators(
            @Param("marketId") Long marketId,
            @Param("keyword") String keyword,
            @Param("withdrawnStatus") UserStatus withdrawnStatus,
            Pageable pageable);

    /** §14-3 요청함 목록 — 브랜드 정보까지 한 번에 가져온다(N+1 방지). */
    @Query("SELECT c FROM Connection c JOIN FETCH c.market m " +
           "WHERE c.type = showroomz.domain.connection.type.ConnectionType.PAIR " +
           "AND c.creator = :creator AND c.status = :status " +
           "ORDER BY c.requestedAt DESC")
    Page<Connection> findRequestsByCreator(@Param("creator") Creator creator,
                                            @Param("status") ConnectionStatus status,
                                            Pageable pageable);

    long countByTypeAndCreatorAndStatus(ConnectionType type, Creator creator, ConnectionStatus status);
}
