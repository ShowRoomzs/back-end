package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractEventType;

import java.time.LocalDateTime;

/**
 * 계약 이력 기록(설계서 3-5).
 *
 * <p>이벤트 리스너로 분리하지 않고 서비스 안에서 직접 호출한다. 이력이 비면 화면의 「이력」 카드가 비고,
 * 그건 분쟁에서 근거가 없다는 뜻이다 — 비동기로 흘려보낼 종류의 데이터가 아니다.
 *
 * <p>{@code MANDATORY}로 잠근다. 호출자가 트랜잭션 없이 부르면 즉시 실패하므로,
 * 전이와 이력이 따로 커밋되는 상황 자체가 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ContractHistoryRecorder {

    private final ContractHistoryRepository contractHistoryRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Contract contract, ContractEventType eventType, ContractActorType actorType,
                       Long actorId, String actorDisplayName, String detail, LocalDateTime occurredAt) {
        contractHistoryRepository.save(ContractHistory.of(
                contract, eventType, actorType, actorId, actorDisplayName, detail, occurredAt));
    }

    /**
     * 인플루언서가 주체인 이력 — 표시명은 쇼룸명 스냅샷이다.
     * 스튜디오에서 이 경로를 타는 것은 거절 하나뿐이다(§27 설계서 0-1).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordByCreator(Contract contract, ContractEventType eventType, String detail,
                                LocalDateTime occurredAt) {
        String showroomName = contract.getCreator() == null ? null : contract.getCreator().getShowroomName();
        Long creatorId = contract.getCreator() == null ? null : contract.getCreator().getId();
        record(contract, eventType, ContractActorType.CREATOR, creatorId, showroomName, detail, occurredAt);
    }

    /** 브랜드가 주체인 이력 — 표시명은 브랜드명 스냅샷이다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordBySeller(Contract contract, ContractEventType eventType, String detail,
                               LocalDateTime occurredAt) {
        String brandName = contract.getMarket() == null ? null : contract.getMarket().getMarketName();
        Long marketId = contract.getMarket() == null ? null : contract.getMarket().getId();
        record(contract, eventType, ContractActorType.SELLER, marketId, brandName, detail, occurredAt);
    }
}
