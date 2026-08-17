package showroomz.domain.history.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.member.user.entity.Users;

/**
 * 동의·철회 일시 기록.
 *
 * <p>광고성 정보 수신은 철회 시 일시를 통지해야 하고, 본인확인 재인증은 가입 시 동의와 별개의
 * 새 수집 행위라 매번 다시 받은 동의를 남겨야 한다. 두 경우 모두 "현재 상태"만으로는 부족해
 * 바뀐 시점을 행으로 쌓는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_consent_history")
public class UserConsentHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_consent_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    /** true=동의, false=철회 */
    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Builder
    public UserConsentHistory(Users user, ConsentType consentType, boolean agreed) {
        this.user = user;
        this.consentType = consentType;
        this.agreed = agreed;
    }
}
