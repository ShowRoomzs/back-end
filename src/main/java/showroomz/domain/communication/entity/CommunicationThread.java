package showroomz.domain.communication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.communication.type.ThreadStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;

/**
 * 소통 스레드 — 브랜드+인플루언서 쌍당 1개(유일).
 * 연결(Connection)이 아니라 쌍에 묶여, 연결이 끊겼다 재생성돼도 같은 스레드로 이어진다(기록 보존).
 * <ul>
 *   <li>연결됨 = OPEN(작성·조회 가능) / 해제 = DORMANT(열람만, 작성 불가) / 재연결 = 다시 OPEN</li>
 *   <li>읽음 처리는 스레드 단위 — 참여자별 마지막 읽은 시각 이후 메시지 수 = 안읽은 수</li>
 *   <li>운영자는 열람만 가능(작성 불가) — 열람 사실은 약관·화면에 상시 고지</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "communication_thread",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "communication_thread_uk",
                        columnNames = {"seller_id", "creator_id"}
                )
        }
)
public class CommunicationThread extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "thread_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ThreadStatus status = ThreadStatus.OPEN;

    @Column(name = "seller_last_read_at")
    private LocalDateTime sellerLastReadAt;

    @Column(name = "creator_last_read_at")
    private LocalDateTime creatorLastReadAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    public CommunicationThread(Seller seller, Creator creator) {
        this.seller = seller;
        this.creator = creator;
        this.status = ThreadStatus.OPEN;
        this.lastActivityAt = LocalDateTime.now();
    }

    /** 연결 해제 → 휴면(열람만) */
    public void dormant() {
        this.status = ThreadStatus.DORMANT;
    }

    /** 재연결 → 다시 열림 */
    public void reopen() {
        this.status = ThreadStatus.OPEN;
    }

    public void markReadBySeller() {
        this.sellerLastReadAt = LocalDateTime.now();
    }

    public void markReadByCreator() {
        this.creatorLastReadAt = LocalDateTime.now();
    }

    /** 새 메시지 작성 시 갱신 */
    public void touch() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public boolean isWritable() {
        return status == ThreadStatus.OPEN;
    }
}
