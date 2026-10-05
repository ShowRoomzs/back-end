package showroomz.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.order.type.ClaimAttachmentOwner;

/** 클레임 첨부(35 설계서 1-9) — 소비자 신청 사진 · 브랜드 거절 증빙. 업로드는 기존 이미지 API 가 하고 URL 만 온다. */
@Entity
@Table(name = "order_claim_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderClaimAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private OrderClaim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner", nullable = false, length = 16)
    private ClaimAttachmentOwner owner;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
