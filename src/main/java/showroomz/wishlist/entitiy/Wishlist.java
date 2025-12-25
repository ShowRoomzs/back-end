package showroomz.wishlist.entitiy;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.oauthlogin.user.User;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // createdAt 자동 생성을 위함
@Table(name = "wishlist",
    // 유저 한 명이 동일 상품을 여러 번 찜하는 것을 방지
    uniqueConstraints = {
        @UniqueConstraint(
            name = "wishlist_uk",
            columnNames = {"member_id", "product_id"}
        )
    }
)
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wishlist_id")
    private Long id;

    // Member와의 관계 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Product와의 관계 (N:1)
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "product_id", nullable = false)
//    private Product product;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt; // 찜한 날짜

    //== 생성자 ==//
//    public Wishlist(Users user, Product product) {
//        this.user = user;
//        this.product = product;
//    }
}