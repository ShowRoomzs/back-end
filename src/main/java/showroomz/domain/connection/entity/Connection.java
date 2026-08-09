package showroomz.domain.connection.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import java.time.LocalDateTime;

/**
 * 브랜드-인플루언서 연결(PAIR) 및 운영자 고정 채널(OPERATOR_MARKET/OPERATOR_CREATOR)을
 * 하나의 모델로 취급한다. 세 타입 모두 "쌍당(또는 운영자-당사자당) 1행" 원칙을 공유하며,
 * TYPE으로 어느 쪽 당사자가 채워지는지만 갈린다.
 *
 * OPERATOR_MARKET/OPERATOR_CREATOR는 요청·수락 절차 없이 승인 즉시 CONNECTED로
 * 생성되고, 탈퇴 전까지 상태 전이가 없다(휴면 개념 자체가 없음).
 */
@Entity
@Table(name = "connection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Connection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ConnectionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id")
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    public static Connection requestPair(Market market, Creator creator) {
        return Connection.builder()
                .type(ConnectionType.PAIR)
                .market(market)
                .creator(creator)
                .status(ConnectionStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public static Connection createOperatorMarket(Market market) {
        LocalDateTime now = LocalDateTime.now();
        return Connection.builder()
                .type(ConnectionType.OPERATOR_MARKET)
                .market(market)
                .status(ConnectionStatus.CONNECTED)
                .requestedAt(now)
                .respondedAt(now)
                .build();
    }

    public static Connection createOperatorCreator(Creator creator) {
        LocalDateTime now = LocalDateTime.now();
        return Connection.builder()
                .type(ConnectionType.OPERATOR_CREATOR)
                .creator(creator)
                .status(ConnectionStatus.CONNECTED)
                .requestedAt(now)
                .respondedAt(now)
                .build();
    }

    /** REJECTED/DISCONNECTED 상태였던 기존 행을 재요청 상태로 되돌린다(같은 쌍은 스레드 기록 보존을 위해 같은 행을 재사용, §13-4). */
    public void markRequested() {
        this.status = ConnectionStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
        this.respondedAt = null;
        this.disconnectedAt = null;
    }

    public void markConnected() {
        this.status = ConnectionStatus.CONNECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void markRejected() {
        this.status = ConnectionStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void markDisconnected() {
        this.status = ConnectionStatus.DISCONNECTED;
        this.disconnectedAt = LocalDateTime.now();
    }

    public boolean isPair() {
        return this.type == ConnectionType.PAIR;
    }
}
