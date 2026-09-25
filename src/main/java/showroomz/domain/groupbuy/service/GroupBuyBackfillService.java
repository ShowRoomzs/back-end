package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이미 체결된 계약의 공구 백필(설계서 2-3). SQL 마이그레이션이 아니라 코드인 이유 — 번호 발급·이력·상품
 * 동기화를 생성 경로와 <b>같은 코드</b>({@link GroupBuyFactory})로 태워야 한다.
 *
 * <p>멱등이다 — 계약 행을 잠그고 {@code group_buy_id}를 다시 본 뒤, 게이트({@code assignGroupBuy})가
 * 이미 생성된 계약을 0행으로 떨군다. 두 번 돌아도 안전하다.
 */
@Service
@RequiredArgsConstructor
public class GroupBuyBackfillService {

    private final ContractRepository contractRepository;
    private final GroupBuyFactory groupBuyFactory;

    @Transactional(readOnly = true)
    public List<Long> findTargets() {
        return contractRepository.findConcludedIdsWithoutGroupBuy();
    }

    /**
     * 계약 1건 = 트랜잭션 1개 — 한 건 실패가 나머지를 막지 않는다.
     *
     * <p>생성 시각은 체결 시각으로 둔다. 번호의 날짜와 이력의 「공구 생성」이 체결일을 가리켜야
     * 「공구 생성일 = 체결일」(설계서 1-10)이 백필 건에서도 성립한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean backfill(Long contractId) {
        Contract contract = contractRepository.findForAdminUpdate(contractId).orElse(null);
        if (contract == null || contract.getStatus() != ContractStatus.CONCLUDED || contract.getGroupBuyId() != null) {
            return false;
        }
        LocalDateTime createdAt = contract.getConcludedAt() != null ? contract.getConcludedAt() : LocalDateTime.now();
        groupBuyFactory.createFromConcludedContract(contract, createdAt);
        return true;
    }
}
