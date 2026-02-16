package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "PRODUCT_INQUIRY")
public class ProductInquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_INQUIRY_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private ProductInquiryType type;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "ANSWER_CONTENT", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "ANSWERED_AT")
    private LocalDateTime answeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private InquiryStatus status;

    @Builder
    public ProductInquiry(Users user, Product product, ProductInquiryType type, String content) {
        this.user = user;
        this.product = product;
        this.type = type;
        this.content = content;
        this.status = InquiryStatus.WAITING;
    }

    public void registerAnswer(String answerContent) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.status = InquiryStatus.ANSWERED;
    }

    public void update(ProductInquiryType type, String content) {
        this.type = type;
        this.content = content;
    }
}
