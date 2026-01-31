package showroomz.domain.market.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.market.type.SnsType;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "MARKET_SNS")
public class MarketSns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MARKET_ID", nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(name = "SNS_TYPE", nullable = false, length = 50)
    private SnsType snsType; // 예: INSTAGRAM, TIKTOK, X, YOUTUBE

    @Column(name = "SNS_URL", nullable = false, length = 512)
    private String snsUrl;

    public MarketSns(Market market, SnsType snsType, String snsUrl) {
        this.market = market;
        this.snsType = snsType;
        this.snsUrl = snsUrl;
    }
}
