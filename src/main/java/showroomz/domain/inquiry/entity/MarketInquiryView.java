package showroomz.domain.inquiry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@Immutable
@Table(name = "market_inquiry_view")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketInquiryView {

    @Id
    @Column(name = "inquiry_key", nullable = false, updatable = false)
    private String inquiryKey;

    @Column(name = "inquiry_id", nullable = false, updatable = false)
    private Long inquiryId;

    @Column(name = "source", nullable = false, updatable = false)
    private String source;

    @Column(name = "filter_type", nullable = false, updatable = false)
    private String filterType;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "customer_name", nullable = false, updatable = false)
    private String customerName;

    @Column(name = "product_name", updatable = false)
    private String productName;

    @Column(name = "market_id", nullable = false, updatable = false)
    private Long marketId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private InquiryStatus status;
}
