package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.user.dto.AdminUserDto.Actions;
import showroomz.api.admin.user.dto.AdminUserDto.AppealInquiry;
import showroomz.api.admin.user.dto.AdminUserDto.SanctionInfo;
import showroomz.api.admin.user.dto.AdminUserDto.StatusCard;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.member.user.entity.UserSanction;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserSanctionRepository;
import showroomz.domain.member.user.type.UserStatus;

import java.util.List;

/**
 * 현재 상태 카드(51-6 설계서 2-3) — 정지 상태에서는 사유 · 처리자 · 일시 · 상세 사유 · 소명 문의가 같은 카드에 모인다(사유 코드만으로는
 * 재검토가 안 된다). 탈퇴 회원도 열린 정지가 있으면 그대로 보인다 — 재가입하면 그 정지로 돌아온다(6절).
 */
@Component
@RequiredArgsConstructor
public class AdminUserStatusCardAssembler {

    private final UserSanctionRepository sanctions;
    private final OneToOneInquiryRepository inquiries;

    public StatusCard statusCard(Users user) {
        UserSanction open = sanctions.findByUserIdAndReleasedAtIsNull(user.getId()).orElse(null);
        UserStatus status = user.getStatus();
        // 정지 상태엔 정지 버튼을 두지 않는다(§25-4) · 탈퇴 회원엔 운영자 액션이 없다(§25-5)
        Actions actions = new Actions(status == UserStatus.NORMAL, status == UserStatus.SUSPENDED);
        return new StatusCard(status, open == null ? null : sanctionInfo(open), actions);
    }

    private SanctionInfo sanctionInfo(UserSanction sanction) {
        List<AppealInquiry> appeals = inquiries.findBySanctionIdOrderByCreatedAtDescIdDesc(sanction.getId()).stream()
                .map(inquiry -> new AppealInquiry(inquiry.getId(), inquiry.isAnswered(), inquiry.getCreatedAt()))
                .toList();
        return new SanctionInfo(sanction.getId(), sanction.getReason(), sanction.getReason().getLabel(),
                sanction.getReasonDetail(), sanction.getSuspendedAt(), sanction.getSuspendedByName(), appeals);
    }
}
