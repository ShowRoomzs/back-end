package showroomz.domain.member.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.member.common.type.MemberType;

import java.util.List;

/** {@link CounterpartyHoldNotifier}의 스텁 — 알림함(1017 설계서 1절)이 들어오면 교체한다. */
@Slf4j
@Component
public class LoggingCounterpartyHoldNotifier implements CounterpartyHoldNotifier {

    @Override
    public void holdStarted(MemberType type, Long memberId) {
        log.info("[알림 스텁] 상대 보류 시작 - {} {}", type, memberId);
    }

    @Override
    public void holdResumed(MemberType type, Long memberId, List<Long> resumedContractIds) {
        log.info("[알림 스텁] 상대 보류 재개 - {} {} · 계약 {}건", type, memberId, resumedContractIds.size());
    }
}
