package showroomz.domain.social.entity;

import jakarta.persistence.*;
import lombok.*;
import showroomz.api.app.auth.entity.ProviderType;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SocialLoginPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ProviderType providerType; // GOOGLE, NAVER, KAKAO, APPLE

    @Column(nullable = false)
    private boolean isActive; // true: 활성, false: 일시 중단

    public void updateStatus(boolean isActive) {
        this.isActive = isActive;
    }
}
