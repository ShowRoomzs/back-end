package showroomz.domain.history.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.user.type.WithdrawalReason;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalHistory extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private boolean agreeConsent;

    // String 대신 Enum 사용
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalReason reason; 

    @Column(length = 1000)
    private String customReason; // "기타" 선택 시 상세 사유 저장용

    @Builder
    public WithdrawalHistory(Long userId, boolean agreeConsent, WithdrawalReason reason, String customReason) {
        this.userId = userId;
        this.agreeConsent = agreeConsent;
        this.reason = reason;
        this.customReason = customReason;
    }
}
