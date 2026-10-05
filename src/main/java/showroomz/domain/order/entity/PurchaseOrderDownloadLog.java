package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발주서 반출 이력(34 설계서 1-8 · §34-13 #14 선반영) — 엑셀로 개인정보가 나간다.
 * 다운로드 트랜잭션 안에서 기록한다 — 반출 기록이 빠지면 안 된다. 조회 화면은 어드민 몫이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "purchase_order_download_log")
public class PurchaseOrderDownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "download_log_id")
    private Long id;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** 무엇을 반출했나 — 발주서 · 재발송 목록(35 설계서 1-10). 둘 다 수취인 개인정보 반출이라 같은 로그에 남긴다. */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind = MarketPurchaseOrderTemplate.TYPE_PURCHASE_ORDER;

    /** 반출한 건수 — 발주서는 하위주문 수, 재발송 목록은 클레임 수. */
    @Column(name = "delivery_group_count", nullable = false)
    private Integer deliveryGroupCount;

    /** 어떤 컬럼으로 — 개인정보 포함 여부가 컬럼에 있다. */
    @Column(name = "columns", nullable = false, length = 500)
    private String columns;

    @Column(name = "prepare_started", nullable = false)
    private boolean prepareStarted;

    @Column(name = "downloaded_at", nullable = false)
    private LocalDateTime downloadedAt;

    @Builder
    public PurchaseOrderDownloadLog(Long marketId, Long sellerId, String kind, Integer deliveryGroupCount,
                                    String columns, boolean prepareStarted, LocalDateTime downloadedAt) {
        if (kind != null) {
            this.kind = kind;
        }
        this.marketId = marketId;
        this.sellerId = sellerId;
        this.deliveryGroupCount = deliveryGroupCount;
        this.columns = columns;
        this.prepareStarted = prepareStarted;
        this.downloadedAt = downloadedAt;
    }
}
