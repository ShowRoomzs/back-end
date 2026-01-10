package showroomz.domain.market.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.entity.Users;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "MARKET_FOLLOW",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UK_MARKET_FOLLOW",
            columnNames = {"USER_ID", "MARKET_ID"}
        )
    }
)
public class MarketFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FOLLOW_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MARKET_ID", nullable = false)
    private Market market;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MarketFollow(Users user, Market market) {
        this.user = user;
        this.market = market;
    }
}

