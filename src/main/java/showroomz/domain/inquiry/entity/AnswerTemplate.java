package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.seller.entity.Seller;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ANSWER_TEMPLATE")
public class AnswerTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ANSWER_TEMPLATE_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SELLER_ID", nullable = false)
    private Seller seller;

    @Column(name = "TITLE", nullable = false, length = 30)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false)
    private MarketInquiryFilterType category;

    @Column(name = "CONTENT", nullable = false, length = 1000)
    private String content;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean isActive = true;

    @Builder
    public AnswerTemplate(Seller seller, String title, MarketInquiryFilterType category,
                          String content, boolean isActive) {
        this.seller = seller;
        this.title = title;
        this.category = category;
        this.content = content;
        this.isActive = isActive;
    }

    public void update(String title, MarketInquiryFilterType category, String content, boolean isActive) {
        this.title = title;
        this.category = category;
        this.content = content;
        this.isActive = isActive;
    }
}
