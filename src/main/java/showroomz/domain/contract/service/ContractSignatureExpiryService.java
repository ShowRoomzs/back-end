package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 서명 기한 경과 불성립(수정요청 B-11 · 2026-11-10) — 서명 기한까지 양측 서명이 갖춰지지 않은 계약은 성립하지 않는다. 시스템이
 * 만료(EXPIRED)로 닫고 양측에 알린다. 정지 보류 중에는 기한이 멈춰 있어 닫지 않는다(8-4). 어드민 수동 만료는 그대로 남는다.
 */
@Service
@RequiredArgsConstructor
public class ContractSignatureExpiryService {

    private final ContractRepository contracts;
    private final ContractHistoryRecorder history;
    private final ContractNotifier notifier;

    @Transactional(readOnly = true)
    public List<Long> findIdsToExpire(LocalDateTime now, int limit) {
        return contracts.findIdsToExpireBySignatureDeadline(now, PageRequest.of(0, limit));
    }

    /** 잠근 뒤 다시 본다 — 그사이 서명이 갖춰졌거나(CONCLUSION_PENDING) 보류 · 취소됐으면 false. */
    @Transactional
    public boolean expireIfOverdue(Long contractId, LocalDateTime now) {
        Contract c = contracts.findForAdminUpdate(contractId).orElse(null);
        if (c == null || c.getStatus() != ContractStatus.SIGNING || !c.isSignatureDeadlinePassed(now)) {
            return false;
        }
        if (contracts.transitionStatus(c.getId(), ContractStatus.SIGNING, ContractStatus.EXPIRED) != 1) {
            return false;
        }
        c.expire(now, ContractActorType.SYSTEM);
        history.record(c, ContractEventType.EXPIRED, ContractActorType.SYSTEM, null, null,
                "서명 기한 경과 · 불성립", now);
        notifier.notifyBothParties(c, "EXPIRED");
        return true;
    }
}
