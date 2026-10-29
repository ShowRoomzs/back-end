package showroomz.api.creator.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;
import showroomz.api.creator.common.CreatorResolver;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.service.CreatorSanctionReader;

/** 쇼룸 스튜디오 계정 상태 — 정지 배너(ui-studio-13) · 홈 H5 재료(1017 설계서 8-10). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAccountStatusService {

    private final CreatorResolver creatorResolver;
    private final showroomz.domain.member.common.service.SanctionTermPolicy termPolicy;
    private final CreatorSanctionReader sanctionReader;
    private final OperatorChannelService operatorChannelService;

    public MemberAccountStatusResponse getStatus(Long userId) {
        return of(creatorResolver.resolve(userId));
    }

    public MemberAccountStatusResponse of(Creator creator) {
        MemberAccountStatusResponse.Sanction sanction = sanctionReader.openSanction(creator.getId())
                .map(s -> toSanction(s, creator))
                .orElse(null);
        return new MemberAccountStatusResponse(creator.getStatus().name(), sanction,
                sanctionReader.hasPendingWithdrawal(creator.getId()));
    }

    private MemberAccountStatusResponse.Sanction toSanction(CreatorSanction s, Creator creator) {
        return new MemberAccountStatusResponse.Sanction(s.getReason().name(), s.getReason().getLabel(),
                MemberAccountStatusResponse.blankToNull(s.getReasonDetail()), s.getSuspendedAt(), s.isPayoutHeld(),
                operatorChannelService.findCreatorChannelThreadId(creator).orElse(null), s.getSuspendCardId(),
                MemberAccountStatusResponse.Term.of(s.getEndsOn(), s.getSuspendedAt(), s.getAppealDeadlineAt(),
                        termPolicy));
    }
}
