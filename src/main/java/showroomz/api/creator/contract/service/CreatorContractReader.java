package showroomz.api.creator.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 스튜디오 계약 API의 <b>가시성 판정</b> — 이 설계서의 본체다(§27 설계서 0-2 · 1-1).
 *
 * <p>브랜드가 작성중 화면에서 계약 상대를 고르는 순간 {@code creator_id}가 박힌다(§25-5-1).
 * 즉 <b>{@code WHERE creator_id = :me}만으로 조회하면 브랜드가 아직 보내지도 않은 계약이
 * 인플루언서 목록에 뜬다.</b> 운영자가 문제를 지적해 브랜드에게 되돌린 검토 반려 계약까지 보인다.
 *
 * <p><b>가시성은 필터가 아니라 권한이다.</b> 목록 · 상세 · 거절 · 재발송 · 문서 · 조항
 * 여섯 경로가 예외 없이 {@link #requireReceived}를 통과하고, 리포지토리에는
 * 판정 없는 조회 메서드를 두지 않는다. 한 곳만 빠져도 미발송 계약이 나간다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorContractReader {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final ContractRepository contractRepository;

    public Creator resolveCreator(String creatorEmail) {
        Users user = userRepository.findByUsername(creatorEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return creatorRepository.findByUser(user)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }

    /**
     * 「도착한 계약」만 돌려준다. 아니면 <b>404</b>다 — 403이 아니다(설계서 1-2).
     *
     * <p>403은 「있는데 못 본다」는 뜻이다. 그 응답을 주면 인플루언서가 <b>브랜드가 자기 앞으로
     * 계약을 작성 중이라는 사실</b>을 알게 된다. 검토 반려된 계약이라면 「나한테 보내려다
     * 운영자에게 막혔다」까지 읽힌다. 계약번호가 {@code CTR-YYYYMMDD-NNN}으로 추측 가능하므로
     * <b>존재 여부 자체를 숨긴다.</b>
     *
     * <p>남의 계약은 {@code CONTRACT_NOT_FOUND}, 내 앞으로 작성 중인 미도착 계약은
     * {@code CONTRACT_NOT_RECEIVED}로 갈리지만 <b>문구가 같다</b> — 화면에서 구분할 수 없어야 한다.
     * 코드를 나눈 것은 서버 로그에서만 구분하기 위해서다.
     *
     * <p>이미 종결된 계약은 여기를 <b>통과한다</b>. 종결 계약도 읽을 수 있다(S7·S8·S9).
     */
    public Contract requireReceived(Long creatorId, Long contractId) {
        return contractRepository
                .findReceivedByCreator(contractId, creatorId, ContractStatus.RECEIVED_BY_CREATOR)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_RECEIVED));
    }
}
