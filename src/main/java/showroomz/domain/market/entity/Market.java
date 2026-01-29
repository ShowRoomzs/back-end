package showroomz.domain.market.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.category.entity.Category;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.market.type.ShopType;
import showroomz.domain.market.type.SnsType;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "MARKET")
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MARKET_ID")
    private Long id;

    // Seller 테이블과 1:1 관계 (계정 정보)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SELLER_ID", nullable = false)
    private Seller seller;

    @Column(name = "MARKET_NAME", nullable = false, unique = true)
    private String marketName;

    @Column(name = "CS_NUMBER", nullable = false)
    private String csNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "SHOP_TYPE")
    private ShopType shopType; // MARKET or SHOWROOM

    // 마켓 정보 필드
    @Column(name = "MARKET_IMAGE_URL", length = 512)
    private String marketImageUrl; // 마켓 대표 이미지

    @Column(name = "MARKET_DESCRIPTION", length = 1000)
    private String marketDescription; // 마켓 소개

    @Column(name = "MARKET_URL", length = 512)
    private String marketUrl; // 마켓 URL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MAIN_CATEGORY_ID")
    private Category mainCategory; // 대표 카테고리

    // SNS 링크 (1:N 관계)
    @OneToMany(mappedBy = "market", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MarketSns> snsLinks = new ArrayList<>();

    public Market(Seller seller, String marketName, String csNumber) {
        this.seller = seller;
        this.marketName = marketName;
        this.csNumber = csNumber;
    }

    // 연관관계 편의 메서드
    public void addSnsLink(SnsType type, String url) {
        MarketSns sns = new MarketSns(this, type, url);
        this.snsLinks.add(sns);
    }

    public void clearSnsLinks() {
        this.snsLinks.clear();
    }
}