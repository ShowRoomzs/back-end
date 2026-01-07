package showroomz.api.seller.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "SELLER")
public class Seller {

    @Id
    @Column(name = "SELLER_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL", length = 512, unique = true)
    @NotNull
    @Size(max = 512)
    private String email; // 로그인 ID로 사용

    @Column(name = "PASSWORD", length = 128)
    @NotNull
    @Size(max = 128)
    private String password;

    @Column(name = "NAME", length = 64)
    @NotNull
    @Size(max = 64)
    private String name; // 판매자 실명/대표자명

    @Column(name = "PHONE_NUMBER", length = 20)
    @Size(max = 20)
    private String phoneNumber; // 담당자 연락처

    @Column(name = "ROLE_TYPE", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private RoleType roleType; // 주로 SELLER

    @Column(name = "STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private SellerStatus status;

    @Column(name = "CREATED_AT")
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "MODIFIED_AT")
    @NotNull
    private LocalDateTime modifiedAt;

    // 생성자 편의 메서드
    public Seller(String email, String password, String name, String phoneNumber, LocalDateTime now) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.roleType = RoleType.SELLER;
        this.status = SellerStatus.PENDING; // 기본값을 승인 대기로 설정
        this.createdAt = now;
        this.modifiedAt = now;
    }
}

