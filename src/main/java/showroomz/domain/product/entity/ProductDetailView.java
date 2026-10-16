package showroomz.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.product.type.ProductDetailViewSource;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회 원천 로그(49 스튜디오 성과 설계서 1-4) — 행 1건 = 30분 세션 1회.
 *
 * <p>성과의 상품별 「상세 조회」와 게시물 「상품 상세 이동」이 이 로그 하나를 읽는다. 같은 사실을 두 로그에 쓰면 합이 어긋난다.
 * {@code viewerKey} 규칙은 쇼룸 방문 · 게시물 노출과 같다 — 그래야 라스트 터치 귀속이 이어진다.
 *
 * <p>연관을 두지 않고 id 만 든다 — 통계 로그라 상품 · 공구 · 게시물이 파기돼도 행이 막히지 않아야 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_detail_view", indexes = {
        @Index(name = "idx_pdv_group_buy_product", columnList = "group_buy_id, product_id, viewed_at"),
        @Index(name = "idx_pdv_session", columnList = "product_id, viewer_key, viewed_at"),
        @Index(name = "idx_pdv_attribution", columnList = "attributed_post_id, viewed_at")
})
public class ProductDetailView {

    /** 중복 조회 판정 세션 — 쇼룸 방문 · 게시물 노출과 같은 30분. */
    public static final int SESSION_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "view_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "group_buy_id")
    private Long groupBuyId;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "viewer_key", nullable = false, length = 64)
    private String viewerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ProductDetailViewSource source;

    @Column(name = "attributed_post_id")
    private Long attributedPostId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    public ProductDetailView(Long productId, Long groupBuyId, Long creatorId, Long userId, String viewerKey,
                             ProductDetailViewSource source, Long attributedPostId, LocalDateTime viewedAt) {
        this.productId = productId;
        this.groupBuyId = groupBuyId;
        this.creatorId = creatorId;
        this.userId = userId;
        this.viewerKey = viewerKey;
        this.source = source;
        this.attributedPostId = attributedPostId;
        this.viewedAt = viewedAt;
    }
}
