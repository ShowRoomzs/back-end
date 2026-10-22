package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.SanctionHoldParty;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 당사자 정지로 인한 계약 보류(1017 설계서 8-3 · 8-4 · ui-partner-15 S4 · S4b · R1 · ui-studio-13 S3 · R1).
 *
 * <ul>
 *   <li>정지되면 그 당사자의 검토 대기 · 서명 진행중 · 공구 생성 대기 계약을 보류로 표시한다 — 상태는 바꾸지 않는다.</li>
 *   <li>보류 중에는 운영자가 검토 통과 · 체결 완료를 하지 않는다(409 {@code CONTRACT_HELD_BY_SANCTION}) · 서명 기한이 멈춘다.</li>
 *   <li>해제되면 남은 보류 당사자가 없을 때만 멈춘 만큼 서명 기한을 뒤로 민다 — 「해제 후 남은 기한부터 다시 센다」.</li>
 * </ul>
 *
 * <p>모두싸인 API 가 없어 서명 자체는 막을 수 없다 — 막는 지점은 체결이다.
 */
@Service
@RequiredArgsConstructor
public class ContractSanctionHoldService {

    /** 보류 대상 — 체결 전이고 운영자가 다음 단계를 진행해야 하는 계약. 작성중 · 검토 반려는 브랜드 가드가 막는다. */
    public static final Set<ContractStatus> HOLDABLE =
            EnumSet.of(ContractStatus.REVIEW_PENDING, ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);

    private final ContractRepository contractRepository;

    /** 운영자 검토 통과 · 체결 완료 앞단 — 어느 당사자든 정지면 진행하지 않는다. */
    public void requireNoPartySuspended(Contract contract) {
        if (isMarketSuspended(contract.getMarket()) || isCreatorSuspended(contract.getCreator())) {
            throw new BusinessException(ErrorCode.CONTRACT_HELD_BY_SANCTION);
        }
    }

    public static boolean isMarketSuspended(Market market) {
        return market != null && market.isSuspended();
    }

    public static boolean isCreatorSuspended(Creator creator) {
        return creator != null && creator.getStatus() == CreatorStatus.SUSPENDED;
    }

    /** @return 이번에 새로 보류가 걸린 계약 수(넓힌 것 제외) */
    @Transactional
    public int holdForMarket(Long marketId, LocalDateTime now) {
        contractRepository.widenHoldForMarket(marketId, HOLDABLE);
        return contractRepository.holdForMarket(marketId, HOLDABLE, now);
    }

    @Transactional
    public int holdForCreator(Long creatorId, LocalDateTime now) {
        contractRepository.widenHoldForCreator(creatorId, HOLDABLE);
        return contractRepository.holdForCreator(creatorId, HOLDABLE, now);
    }

    /** @return 보류가 완전히 끝난 계약 id(상대방에게 「재개」 알림을 보낼 대상) */
    @Transactional
    public List<Long> resumeForMarket(Long marketId, LocalDateTime now) {
        return resume(contractRepository.findHeldForMarket(marketId), SanctionHoldParty.BRAND, now);
    }

    @Transactional
    public List<Long> resumeForCreator(Long creatorId, LocalDateTime now) {
        return resume(contractRepository.findHeldForCreator(creatorId), SanctionHoldParty.CREATOR, now);
    }

    private List<Long> resume(List<Contract> held, SanctionHoldParty released, LocalDateTime now) {
        List<Long> resumed = new ArrayList<>();
        for (Contract contract : held) {
            SanctionHoldParty remaining = contract.getSanctionHoldParty() == null
                    ? null : contract.getSanctionHoldParty().without(released);
            if (remaining != null) {
                contractRepository.narrowHold(contract.getId(), remaining);
                continue;
            }
            contractRepository.endHold(contract.getId(), shiftedDeadline(contract, now));
            resumed.add(contract.getId());
        }
        return resumed;
    }

    /** 서명 진행중이면 멈춘 기간만큼 민다 · 그 밖은 기한 그대로(검토 대기는 아직 기한이 없다). */
    static LocalDateTime shiftedDeadline(Contract contract, LocalDateTime now) {
        LocalDateTime deadline = contract.getSignatureDeadlineAt();
        if (deadline == null || contract.getStatus() != ContractStatus.SIGNING
                || contract.getSanctionHoldSince() == null || now.isBefore(contract.getSanctionHoldSince())) {
            return deadline;
        }
        return deadline.plus(Duration.between(contract.getSanctionHoldSince(), now));
    }
}
