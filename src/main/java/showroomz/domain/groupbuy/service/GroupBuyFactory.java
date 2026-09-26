package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 공구 생성 — <b>계약 체결 트랜잭션 안에서만</b> 생긴다(설계서 0-2 · 2-1). 생성 진입점은 세 서피스 어디에도 없다.
 *
 * <p>체결은 유일한 불가역 조치다. 공구 생성이 실패하면 체결도 롤백되어야 결과가 반쪽이 되지 않으므로
 * {@code MANDATORY}로 호출자의 트랜잭션에 묶는다. 「체결완료인데 공구가 없는 계약」이 구조적으로 생기지 않는다.
 *
 * <p>중복 생성의 최종 방어선은 두 겹이다 — 계약 쪽 조건부 UPDATE({@code assignGroupBuy})와
 * {@code group_buy.contract_id} UNIQUE.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyFactory {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyNumberGenerator numberGenerator;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final ContractRepository contractRepository;
    private final ContractHistoryRecorder contractHistoryRecorder;
    private final ProductGroupBuyStatusSynchronizer productSynchronizer;
    private final GroupBuyNotifier notifier;

    @Transactional(propagation = Propagation.MANDATORY)
    public GroupBuy createFromConcludedContract(Contract contract, LocalDateTime now) {
        if (contract.getStatus() != ContractStatus.CONCLUDED || contract.getGroupBuyId() != null) {
            throw new BusinessException(ErrorCode.CONTRACT_GROUP_BUY_ALREADY_CREATED);
        }

        String number = numberGenerator.generate(now.toLocalDate());
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.createFrom(contract, number));

        // 26 · 1-8 게이트 — 0행이면 다른 경로(백필 등)가 먼저 만들었다. 예외로 체결까지 롤백한다.
        if (contractRepository.assignGroupBuy(contract.getId(), groupBuy.getId()) != 1) {
            throw new BusinessException(ErrorCode.CONTRACT_GROUP_BUY_ALREADY_CREATED);
        }
        contract.linkGroupBuy(groupBuy.getId());

        // 생성 주체는 시스템이다 — 시안의 「○○ 브랜드」는 정정 대상이다(설계서 7-1 #7).
        historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.CREATED, null, now);
        contractHistoryRecorder.record(contract, ContractEventType.GROUP_BUY_CREATED, ContractActorType.SYSTEM,
                null, null, number, now);
        productSynchronizer.resync(groupBuy);
        notifier.notifyBothParties(groupBuy, "CREATED");
        return groupBuy;
    }
}
