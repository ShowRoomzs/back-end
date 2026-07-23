package showroomz.domain.history.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.seller.entity.SellerApplication;

@Entity
@Table(name = "seller_application_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplicationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_application_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_application_id", nullable = false)
    private SellerApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private SellerStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private SellerStatus newStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Builder
    public SellerApplicationHistory(
            SellerApplication application,
            SellerStatus previousStatus,
            SellerStatus newStatus,
            String reason) {
        this.application = application;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }
}
