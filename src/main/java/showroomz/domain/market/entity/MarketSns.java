package showroomz.domain.market.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "SNS_TYPE", nullable = false, length = 50)
    private String snsType; // 예: INSTAGRAM, YOUTUBE (Enum으로 관리하면 더 좋음)

    @Column(name = "SNS_URL", nullable = false, length = 512)
    private String snsUrl;

    public MarketSns(Market market, String snsType, String snsUrl) {
        this.market = market;
        this.snsType = snsType;
        this.snsUrl = snsUrl;
    }
}
