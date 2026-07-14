package showroomz.domain.connection.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.connection.type.DisconnectType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;

/**
 * 연결 (브랜드 ↔ 인플루언서) — 계약·소통의 전제 관계.
 * 브랜드가 인플루언서를 지정(검색/코드)해 요청하고, 인플루언서 수락으로 성립한다.
 * <p>같은 쌍에 동시에 유효한 연결(REQUESTED/CONNECTED)은 1개만 허용 — 서비스 계층에서 검증.
 * 재연결은 새 Connection 레코드로 생성하며, 소통 스레드는 쌍 기준이라 이어진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "connection")
public class Connection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status = ConnectionStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "disconnect_type", length = 20)
    private DisconnectType disconnectType;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    public Connection(Seller seller, Creator creator) {
        this.seller = seller;
        this.creator = creator;
        this.status = ConnectionStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    /** 인플루언서 수락 → 연결됨 (소통 스레드 활성은 서비스에서 처리) */
    public void accept() {
        this.status = ConnectionStatus.CONNECTED;
        this.acceptedAt = LocalDateTime.now();
    }

    /** 인플루언서 거절 → 해제(거절) */
    public void reject() {
        this.status = ConnectionStatus.DISCONNECTED;
        this.disconnectType = DisconnectType.REJECTED;
        this.disconnectedAt = LocalDateTime.now();
    }

    /** 연결 해제 — 진행중 공구가 없을 때만 가능(서비스 검증) */
    public void release() {
        this.status = ConnectionStatus.DISCONNECTED;
        this.disconnectType = DisconnectType.RELEASED;
        this.disconnectedAt = LocalDateTime.now();
    }

    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }
}
