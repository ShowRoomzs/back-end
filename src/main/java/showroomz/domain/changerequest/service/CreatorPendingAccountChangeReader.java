package showroomz.domain.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.settlement.port.PendingAccountChangeReader;

/**
 * 정산 포트 {@link PendingAccountChangeReader}의 구현 — 열린 요청(인플루언서당 1건)이 계좌 변경이면 확인 중이다.
 *
 * <p>트랜잭션을 따로 열지 않는다 — 취소 · 반려가 요청 상태를 바꾼 같은 트랜잭션 안에서 보류를 다시 판정하므로, 조회가 그 변경을 봐야 한다.
 */
@Component
@RequiredArgsConstructor
public class CreatorPendingAccountChangeReader implements PendingAccountChangeReader {

    private final ChangeRequestRepository changeRequestRepository;

    @Override
    public boolean hasPending(Long creatorId) {
        if (creatorId == null) {
            return false;
        }
        return changeRequestRepository.findOpenForCreator(creatorId)
                .filter(request -> request.getType() == ChangeRequestType.CREATOR_ACCOUNT)
                .isPresent();
    }
}
