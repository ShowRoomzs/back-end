package showroomz.api.admin.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 어드민 공구 API의 진입 — 가시성 필터가 없다(32 설계 2-1). {@code /v1/admin/**} → ADMIN 인증만이 조건이다.
 *
 * <p><b>실행 API는 공구 행 잠금을 트랜잭션의 첫 문장으로 둔다.</b> 운영자 조회 같은 일반 SELECT가 먼저 돌면 MySQL
 * REPEATABLE READ 스냅샷이 잠금 대기 전에 잡혀, 잠금을 얻은 뒤 읽는 사실 테이블(요청·통지·판본)이 앞선 트랜잭션의
 * 커밋을 못 본다.
 */
@Component
@RequiredArgsConstructor
public class AdminGroupBuyAccess {

    private final GroupBuyRepository groupBuyRepository;
    private final AdminOperatorResolver operators;

    /** 조회용 — 계약·브랜드·인플루언서를 함께 올린다. */
    @Transactional(readOnly = true)
    public GroupBuy read(Long groupBuyId) {
        return groupBuyRepository.findDetailById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
    }

    /** 실행용 — {@code PESSIMISTIC_WRITE}. 잠금 순서는 {@code group_buy} → {@code group_buy_post} → 사실 테이블이다(9-1). */
    @Transactional
    public GroupBuy lock(Long groupBuyId) {
        return groupBuyRepository.findForUpdate(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
    }

    public String operatorName(Long operatorId) {
        return operators.operatorName(operatorId);
    }

    /**
     * 판매를 실제로 멈추는 판정 3종(중단 요청 승인 · 직권 중단 집행 · 긴급)의 권한 지점(32 설계 9-2).
     * 버튼 색 기준(「실제로 판매가 멈추는가」)과 같은 선이다.
     */
    public String requireSalesStoppingPermission(Long operatorId) {
        // 최고관리자 한정 정책 확정 시 이 지점에서 역할을 추가로 검사한다. (§33-1 #11)
        return operators.operatorName(operatorId);
    }
}
