package showroomz.api.app.user.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.WithdrawalReason;

@Getter
@NoArgsConstructor
public class WithdrawalRequest {
    private boolean agreeConsent;
    private WithdrawalReason reason; // JSON에서 자동으로 Enum 매핑됨
    private String customReason;     // 선택 사항
}
