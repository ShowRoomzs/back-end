package showroomz.domain.contract.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서명 기한 정지 · 재개(1017 설계서 8-4) — 서명 진행중 계약은 보류된 시간만큼 기한이 밀린다. 그 밖의 상태 · 보류 없음은 그대로.
 */
@DisplayName("ContractSanctionHoldService.shiftedDeadline — 멈춘 만큼 미는 기한")
class ContractSanctionHoldDeadlineTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.parse("2026-10-25T23:59:59");
    private static final LocalDateTime HOLD = LocalDateTime.parse("2026-10-20T10:00:00");

    private static Contract contract(ContractStatus status, LocalDateTime deadline, LocalDateTime holdSince) {
        Contract contract = BeanUtils.instantiateClass(Contract.class);
        ReflectionTestUtils.setField(contract, "status", status);
        ReflectionTestUtils.setField(contract, "signatureDeadlineAt", deadline);
        ReflectionTestUtils.setField(contract, "sanctionHoldSince", holdSince);
        return contract;
    }

    @Test
    @DisplayName("서명 진행중 — 보류 2일 3시간이면 기한도 2일 3시간 뒤로")
    void signingShiftsByHeldDuration() {
        LocalDateTime resumedAt = HOLD.plusDays(2).plusHours(3);

        assertThat(ContractSanctionHoldService.shiftedDeadline(contract(ContractStatus.SIGNING, DEADLINE, HOLD), resumedAt))
                .isEqualTo(DEADLINE.plusDays(2).plusHours(3));
    }

    @Test
    @DisplayName("검토 대기 등 서명 전 상태 · 보류 시각 없음 · 기한 없음 — 그대로")
    void otherwiseUnchanged() {
        LocalDateTime now = HOLD.plusDays(1);

        assertThat(ContractSanctionHoldService.shiftedDeadline(contract(ContractStatus.REVIEW_PENDING, DEADLINE, HOLD), now))
                .isEqualTo(DEADLINE);
        assertThat(ContractSanctionHoldService.shiftedDeadline(contract(ContractStatus.SIGNING, DEADLINE, null), now))
                .isEqualTo(DEADLINE);
        assertThat(ContractSanctionHoldService.shiftedDeadline(contract(ContractStatus.SIGNING, null, HOLD), now)).isNull();
    }

    @Test
    @DisplayName("시계가 보류 시작보다 앞이면(재개가 먼저 기록) 밀지 않는다")
    void clockBeforeHold() {
        assertThat(ContractSanctionHoldService.shiftedDeadline(contract(ContractStatus.SIGNING, DEADLINE, HOLD),
                HOLD.minusMinutes(1))).isEqualTo(DEADLINE);
    }
}
