package showroomz.Market.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.user.entity.Users;

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

    // Users 테이블과 1:1 관계 (계정 정보)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @Column(name = "SELLER_NAME", nullable = false)
    private String sellerName;

    @Column(name = "SELLER_CONTACT", nullable = false)
    private String sellerContact;

    @Column(name = "MARKET_NAME", nullable = false, unique = true)
    private String marketName;

    @Column(name = "CS_NUMBER", nullable = false)
    private String csNumber;

    // 마켓 정보 필드
    @Column(name = "MARKET_IMAGE_URL", length = 512)
    private String marketImageUrl; // 마켓 대표 이미지

    @Column(name = "MARKET_DESCRIPTION", length = 1000)
    private String marketDescription; // 마켓 소개

    @Column(name = "MARKET_URL", length = 512)
    private String marketUrl; // 마켓 URL

    @Column(name = "MAIN_CATEGORY", length = 100)
    private String mainCategory; // 대표 카테고리

    // SNS 링크 (최대 3개)
    @Column(name = "SNS_LINK_1", length = 512)
    private String snsLink1;

    @Column(name = "SNS_LINK_2", length = 512)
    private String snsLink2;

    @Column(name = "SNS_LINK_3", length = 512)
    private String snsLink3;

    public Market(Users user, String sellerName, String sellerContact, String marketName, String csNumber) {
        this.user = user;
        this.sellerName = sellerName;
        this.sellerContact = sellerContact;
        this.marketName = marketName;
        this.csNumber = csNumber;
    }
}