package showroomz.api.creator.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 스튜디오 공구 API의 가시성 판정 — <b>내 공구인가</b> 하나다(31 설계 0-4).
 *
 * <p>계약과 달리 공구에는 「도착 전」 단계가 없다 — 공구는 체결 트랜잭션 안에서 생기고 그때 인플루언서는 이미 서명을 끝냈다.
 * 그러므로 판정은 {@code group_buy.creator_id = :me} 한 줄이고, 모든 경로가 {@link #requireMine}을 통과한다.
 *
 * <p><b>남의 공구는 403이 아니라 404</b>다. 공구번호가 {@code GB-YYYYMMDD-NNN}으로 추측 가능해 403은 「이 번호로 누군가의
 * 공구가 있다」를 알려준다. {@code NOT_OWNED_BY_CREATOR}와 {@code NOT_FOUND}는 문구가 같고 서버 로그에서만 갈린다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorGroupBuyReader {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final GroupBuyRepository groupBuyRepository;

    public Creator resolveCreator(String creatorEmail) {
        Users user = userRepository.findByUsername(creatorEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    /** 조회용 — 계약·브랜드를 함께 올린다. */
    public GroupBuy requireMine(Long creatorId, Long groupBuyId) {
        return groupBuyRepository.findDetailByIdAndCreatorId(groupBuyId, creatorId)
                .orElseThrow(() -> notFound(groupBuyId));
    }

    /**
     * 실행용 — 공구 행을 {@code PESSIMISTIC_WRITE}로 잠근다. 판정과 집행 사이에 다른 요청이 끼어들지 못하게 한다.
     * 게시물 쓰기의 잠금 순서(공구 → 게시물)의 첫 칸이다. 호출자의 쓰기 트랜잭션 안에서만 부른다.
     */
    @Transactional
    public GroupBuy requireMineForUpdate(Long creatorId, Long groupBuyId) {
        return groupBuyRepository.findForUpdateByIdAndCreatorId(groupBuyId, creatorId)
                .orElseThrow(() -> notFound(groupBuyId));
    }

    /** 404 두 갈래 — 화면에서는 구분되지 않는다. 로그에서 「남의 공구를 찔렀다」를 가려내려고 코드만 나눈다. */
    private BusinessException notFound(Long groupBuyId) {
        return new BusinessException(groupBuyRepository.existsById(groupBuyId)
                ? ErrorCode.GROUP_BUY_NOT_OWNED_BY_CREATOR
                : ErrorCode.GROUP_BUY_NOT_FOUND);
    }
}
