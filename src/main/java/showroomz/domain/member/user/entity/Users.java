package showroomz.domain.member.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.member.user.type.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "USERS")
public class Users {
    @JsonIgnore
    @Id
    @Column(name = "USER_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USERNAME", length = 64, unique = true)
    @NotNull
    @Size(max = 64)
    private String username; // 로그인 아이디

    @Column(name = "NICKNAME", length = 100)
    @NotNull
    @Size(max = 100)
    private String nickname;

    @Column(name = "NAME", length = 64)
    @Size(max = 64)
    private String name; // 실명(판매자 이름)

    @Column(name = "PHONE_NUMBER", length = 20)
    @Size(max = 20)
    private String phoneNumber; // 연락처

    @JsonIgnore
    @Column(name = "PASSWORD", length = 128)
    @NotNull
    @Size(max = 128)
    private String password;

    @Column(name = "EMAIL", length = 512, unique = true)
    @NotNull
    @Size(max = 512)
    private String email;

    @Column(name = "EMAIL_VERIFIED_YN", length = 1)
    @NotNull
    @Size(min = 1, max = 1)
    private String emailVerifiedYn;

    @Column(name = "PROFILE_IMAGE_URL", length = 512)
    @Size(max = 512)
    private String profileImageUrl;

    @Column(name = "GENDER", length = 10)
    private String gender; // "MALE", "FEMALE", null

    @Column(name = "BIRTHDAY", length = 10)
    private String birthday; // "YYYY-MM-DD"

    @Column(name = "PROVIDER_TYPE", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private ProviderType providerType;

    @Column(name = "ROLE_TYPE", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private RoleType roleType;

    @Column(name = "CREATED_AT")
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "MODIFIED_AT")
    @NotNull
    private LocalDateTime modifiedAt;

    @Column(name = "SERVICE_AGREE")
    private boolean serviceAgree;

    @Column(name = "PRIVACY_AGREE")
    private boolean privacyAgree;

    @Column(name = "MARKETING_AGREE")
    private boolean marketingAgree;

    @Column(name = "STATUS", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    public Users(
            @NotNull @Size(max = 64) String username,
            @NotNull @Size(max = 100) String nickname,
            @NotNull @Size(max = 512) String email,
            @NotNull @Size(max = 1) String emailVerifiedYn,
            @Size(max = 512) String profileImageUrl,
            @NotNull ProviderType providerType,
            @NotNull RoleType roleType,
            @NotNull LocalDateTime createdAt,
            @NotNull LocalDateTime modifiedAt
    ) {
        this.username = username;
        this.nickname = nickname;
        this.password = "NO_PASS";
        this.email = email != null ? email : "NO_EMAIL";
        this.emailVerifiedYn = emailVerifiedYn;
        this.profileImageUrl = profileImageUrl; // null 허용
        this.providerType = providerType;
        this.roleType = roleType;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.status = UserStatus.NORMAL; // 생성 시 기본값 설정
    }

    // 상태 변경을 위한 메서드 추가 (비즈니스 로직용)
    public void updateStatus(UserStatus status) {
        this.status = status;
    }
}

