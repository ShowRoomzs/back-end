package showroomz.api.creator.profile.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.profile.dto.MyShowroomResponse;
import showroomz.api.creator.profile.dto.ShowroomNameResponse;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSanction;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.service.CreatorSanctionReader;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorProfileService {

    private final CreatorRepository creatorRepository;
    private final CreatorSanctionReader sanctionReader;

    public MyShowroomResponse getMyShowroom(Long userId) {
        Creator creator = getMyCreator(userId);
        CreatorSanction sanction = sanctionReader.openSanction(creator.getId()).orElse(null);
        boolean withdrawalPending = sanctionReader.hasPendingWithdrawal(creator.getId());
        return MyShowroomResponse.from(creator, new MyShowroomResponse.Sanction(creator.getStatus(),
                sanction == null ? null : sanction.getReason().getLabel(),
                sanction == null ? null : sanction.getSuspendedAt(),
                sanction != null && sanction.isPayoutHeld(), withdrawalPending));
    }

    public ShowroomNameResponse getMyShowroomName(Long userId) {
        return ShowroomNameResponse.from(getMyCreator(userId));
    }

    private Creator getMyCreator(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
