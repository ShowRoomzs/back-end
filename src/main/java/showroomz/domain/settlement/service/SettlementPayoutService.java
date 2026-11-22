package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.SettlementPayout;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.global.config.properties.SettlementProperties;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 지급 배치의 서비스(44 어드민 설계서 3-3 · 60_KG이니시스_지급대행_전환_계획서.md D-1 · D-2 · 6절) — 트랜잭션 밖에서 게이트웨이를 부르고 짧은
 * 트랜잭션들({@link SettlementPayoutTransitions} · {@link SettlementPayoutGroupService})로 상태를 옮긴다.
 *
 * <p>지급은 전부 <b>묶음</b>(시뮬레이터 · 이니시스)이다 — 지시 = 영업일 X 10:00, 대상 = 지급일이 X + 리드 영업일(D)이 되는 행. 선점이 행을 묶음에
 * 붙이고 {@link SettlementPayoutGroupService} 가 합계를 올린다. 영업일 14:00 마감 전 틱({@link #cutoffTick})이 늦게 풀린 행 지시 · 수렴 · 잔액
 * 재검사를 하고, 업로드 창 안에서는 10분마다 수렴({@link #convergeBeforeCutoff})이 돈다. 결과는 폴링이 1차다(D-8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPayoutService {

    private static final int RELEASE_BATCH = 200;

    private final SettlementPayoutRepository payoutRepository;
    private final SettlementPayoutTransitions transitions;
    private final SettlementPayoutGateway gateway;
    private final SettlementPartnerSyncService partnerSync;
    private final SettlementProperties properties;
    private final SettlementNotifier notifier;
    private final CreatorAccountVerificationService accountVerification;
    private final PayoutGroupPlanner groupPlanner;
    private final SettlementPayoutGroupService groupService;

    @Transactional(readOnly = true)
    public List<Long> findSettlementIdsDue(LocalDate today, int limit) {
        return payoutRepository.findSettlementIdsDue(today, PageRequest.of(0, limit));
    }

    /**
     * 지급 배치 본체 — 마감이 지난 묶음을 먼저 정리하고, 지급일 D(= 가장 이른 지급일)가 되는 정산을 선점해 묶음에 붙인 뒤 올린다. 잔액은 알림만
     * (10:00 · D-9 — 정리는 14:00 재검사).
     *
     * @param today 호출자 호환용 — 대상일은 {@code now} 로 정한다(리드 · 마감)
     * @return 선점한 정산 수
     */
    public int distributeDue(LocalDate today, LocalDateTime now, int limit) {
        return distributeDueInGroups(now, limit, true);
    }

    /**
     * 묶음 지급 배치(D-2) — 마감이 지난 묶음을 먼저 정리하고, 지급일 D(= 가장 이른 지급일)가 되는 정산을 선점해 묶음에 붙인 뒤 이번에 붙은 묶음을
     * 올린다. 마감 뒤 · 비영업일이면 아무것도 하지 않는다(다음 틱 · 배치).
     *
     * @return 선점한 정산 수
     */
    private int distributeDueInGroups(LocalDateTime now, int limit, boolean warnBalance) {
        if (groupPlanner.hasCutoff()) {
            groupService.settleMissedCutoffs(now);
            if (!groupPlanner.isUploadWindow(now)) {
                return 0;
            }
        }
        LocalDate payDate = groupPlanner.earliestPayDate(now);
        List<Long> due = findSettlementIdsDue(payDate, limit);
        if (due.isEmpty()) {
            return 0;
        }
        if (warnBalance) {
            groupService.warnIfShort(payDate, now);
        }
        try {
            partnerSync.refreshAmlForSettlements(due);
        } catch (RuntimeException e) {
            log.error("AML 상태 재조회 실패 — 저장값으로 판정한다", e);
        }
        Set<Long> groupIds = new LinkedHashSet<>();
        int claimed = 0;
        for (Long settlementId : due) {
            try {
                SettlementPayoutTransitions.GroupClaim claim = transitions.claimForGroups(settlementId, payDate, now);
                if (claim != null) {
                    groupIds.addAll(claim.groupIds());
                    claimed++;
                }
            } catch (Exception e) {
                log.error("정산 지급 선점 실패 - settlementId: {}", settlementId, e);
            }
        }
        groupService.submitAll(groupIds, now);
        return claimed;
    }

    /**
     * 마감 전 수렴(업로드 창 안 10분마다) — 확정 합계 ≠ 현재 합계(보류 뒤 재전송 실패 · 결과 모름 · 일시 오류)인 묶음만 다시 올린다. 맞는 묶음은 PG 를
     * 부르지 않는다. 14:00 틱 하나만 믿으면 14시 이후 보류의 재전송이 실패했을 때 마감까지 고칠 기회가 없다(그대로 마감되면 빠진 행이 이전 합계로
     * 나간다). 선점 · 잔액 재검사는 하지 않는다(14:00 틱의 일). 업로드 창 밖 · 마감 없는 모드는 아무것도 하지 않는다.
     *
     * @return 전송을 시도한 묶음 수
     */
    public int convergeBeforeCutoff(LocalDateTime now) {
        if (!groupPlanner.hasCutoff() || !groupPlanner.isUploadWindow(now)) {
            return 0;
        }
        return groupService.converge(now);
    }

    /**
     * 마감 전 틱(영업일 {@code settlement.payout-cutoff-hour}시 · D-2′) — ① 10:00 뒤 보류가 풀린 행 · 대기 중인 재분배 행 지시(「해제 다음 영업일
     * 지급」 약속) ② 수렴(확정 합계 ≠ 현재 합계인 묶음을 다시 올린다 · D-1 ④) ③ 잔액 재검사(아직 모자라면 큰 묶음부터 남기고 정리 · D-9).
     * 마감이 없는 모드(시뮬레이터)는 하지 않는다.
     */
    public void cutoffTick(LocalDateTime now, int limit) {
        if (!groupPlanner.hasCutoff()) {
            return;
        }
        int claimed = distributeDueInGroups(now, limit, false);
        if (claimed > 0) {
            log.info("정산 지급 마감 전 틱 — 늦게 풀린 행 지시 {}건", claimed);
        }
        int converged = groupService.converge(now);
        if (converged > 0) {
            log.info("정산 지급 마감 전 틱 — 수렴 전송 {}건", converged);
        }
        if (gateway.trimOnShortfall() && groupPlanner.isUploadWindow(now)) {
            groupService.trimToBalance(groupPlanner.earliestPayDate(now), now);
        }
    }

    /**
     * 정산 1건의 예정일 도래 행 지급(재분배 즉시 지시 · 테스트) — 영업일 마감 전에만 즉시 묶음에 넣어 올리고, 그 밖에는 {@code SCHEDULED} 로 둔다
     * (다음 틱 · 배치가 집는다 · D-2′).
     *
     * @param today 호출자 호환용 — 대상일은 {@code now} 로 정한다
     */
    public void distribute(Long settlementId, LocalDate today, LocalDateTime now) {
        if (!groupPlanner.isUploadWindow(now)) {
            return;
        }
        SettlementPayoutTransitions.GroupClaim claim = transitions.claimForGroups(settlementId,
                groupPlanner.earliestPayDate(now), now);
        if (claim != null) {
            groupService.submitAll(claim.groupIds(), now);
        }
    }

    /**
     * 보류 재판정 — 지급 배치 앞단. ⓪ 정산 계좌 「확인 중」으로 남은 인플루언서를 다시 확인한다(50 설계서 6-4 — 확인이 끝나면 확인 훅이
     * 보류를 푼다) ① PG 서브몰 사유로 보류된 행(수취자 무관)은 서브몰 등록을 다시 시도한다(고객확인 보류는 AML 상태로만 푼다 · D-6)
     * ② 그 밖의 인플루언서 보류 행은 주민등록번호 · 세금계산서 재판정(5-4).
     */
    public int releaseBlocked(LocalDate today) {
        try {
            accountVerification.retryVerifying();
        } catch (Exception e) {
            log.error("정산 계좌 확인 재시도 실패", e);
        }
        int released = 0;
        for (SettlementPayout payout : findPartnerBlocked()) {
            try {
                if (PayoutBlockReason.KYC_REQUIRED.name().equals(payout.getFailCode()) && partnerSync.isAmlAware()) {
                    // 고객확인(AML) 보류는 AML 상태가 「이행 완료 · 유효」일 때만 푼다 — 등록 성공으로 풀면 다음 지시에서 다시 보류돼 매일 해제 ·
                    // 보류 · 알림이 반복된다(60 전환 계획서 D-6 ②). 다만 AML 상태는 <b>이 PG 의 서브몰 연결</b>이 있어야 물을 수 있다 — 예전 PG(토스)
                    // 연결뿐인 수취자(토스 KYC · 주당 한도로 보류된 행)는 연결이 없어 영영 풀리지 않는다. 그래서 서브몰부터 보장한다(이미 맞으면 PG 를
                    // 부르지 않는다). 등록이 안 되면 사유를 등록 실패 사유로 바꿔 수취자가 할 일(정산 정보 입력 등)이 보이게 한다 — 그 사유로 풀린 행도
                    // 지시 선점이 AML 상태를 다시 보고 막는다.
                    PartnerResult ensured = partnerSync.ensure(payout.getSettlementId(), payout.getPayee());
                    if (!ensured.isApproved()) {
                        if (ensured.failCode() != null && !ensured.failCode().equals(payout.getFailCode())) {
                            transitions.updateBlockReason(payout.getId(), ensured.failCode(), ensured.failReason());
                        }
                        continue;
                    }
                    if (partnerSync.isAmlClear(payout.getSettlementId(), payout.getPayee())
                            && transitions.releasePartnerBlocked(payout.getSettlementId(), payout.getId(), payout.getPayee(), today)) {
                        released++;
                    }
                    continue;
                }
                PartnerResult result = partnerSync.ensure(payout.getSettlementId(), payout.getPayee());
                if (result.isApproved()) {
                    if (transitions.releasePartnerBlocked(payout.getSettlementId(), payout.getId(), payout.getPayee(), today)) {
                        released++;
                    }
                } else if (result.failCode() != null && !result.failCode().equals(payout.getFailCode())) {
                    transitions.updateBlockReason(payout.getId(), result.failCode(), result.failReason());
                }
            } catch (Exception e) {
                log.error("PG 서브몰 보류 재판정 실패 - payoutId: {}", payout.getId(), e);
            }
        }
        for (SettlementPayout payout : findBlocked()) {
            if (payout.getFailCode() != null && PayoutBlockReason.PARTNER_CODES.contains(payout.getFailCode())) {
                continue;
            }
            try {
                if (transitions.releaseIfUnblocked(payout.getSettlementId(), payout.getId(), today)) {
                    released++;
                }
            } catch (Exception e) {
                log.error("지급 보류 재판정 실패 - payoutId: {}", payout.getId(), e);
            }
        }
        return released;
    }

    @Transactional(readOnly = true)
    public List<SettlementPayout> findBlocked() {
        return payoutRepository.findBlockedCreatorPayouts(PageRequest.of(0, RELEASE_BATCH));
    }

    @Transactional(readOnly = true)
    public List<SettlementPayout> findPartnerBlocked() {
        return payoutRepository.findBlockedWithFailCodes(PayoutBlockReason.PARTNER_CODES, PageRequest.of(0, RELEASE_BATCH));
    }

    // ------------------------------------------------------------------ 결과 조회(D-8)

    /** 지급일 ≤ 오늘인 열린 묶음의 송금 결과를 PG 에 묻어 닫는다(날짜 단위 · D-8). PG 처리 지연은 하루 1회(지급 시각 틱)만 알린다. */
    public void pollResults(LocalDateTime now) {
        groupService.pollResults(now);
        LocalDateTime kst = KstDates.toKst(now);
        if (kst.getHour() == properties.getPayoutHour() && kst.getMinute() < 30) {
            warnOrphanRequested();
        }
    }

    /**
     * 묶음 없는 지급 요청(REQUESTED) 행 — 묶음 경로에서는 생기지 않는다(선점과 같은 트랜잭션에서 묶음에 붙는다). 있으면 예전 정산별 지급(토스)으로
     * 지시하고 결과를 못 받은 행이다. 토스 결과 조회 · 웹훅이 지워져 아무것도 이 행을 닫지 않으므로(정산이 지급 예정에 멈춘다) 하루 1회 운영자를
     * 부른다 — 토스 대시보드로 대사해 정리한다(60 전환 계획서 14-8). @return 행 수
     */
    public long warnOrphanRequested() {
        long orphans = countOrphanRequested();
        if (orphans > 0) {
            log.error("묶음 없는 지급 요청 행 {}건 — 예전 토스 지시의 결과 미수신(닫는 경로 없음) · 토스 대사 후 수동 정리 필요", orphans);
            notifier.payoutOrphanRequested(orphans);
        }
        return orphans;
    }

    @Transactional(readOnly = true)
    public long countOrphanRequested() {
        return payoutRepository.countRequestedWithoutGroup();
    }

    /**
     * 지시 끝 — 오늘 올린 묶음의 건수 · 합계를 운영자에게 요약한다. 이니시스는 업로드된 미송금 데이터를 조회하는 API 가 없어 PG 대조는 송금 다음 날
     * 결과 폴링으로 한다(D-8). 시뮬레이터는 올리는 즉시 지급 완료라 요약하지 않는다.
     */
    public void reconcileRequested(LocalDate today) {
        if (!gateway.isExternal()) {
            return;
        }
        LocalDate payDate = groupPlanner.earliestPayDate(KstDates.at(today, LocalTime.of(properties.getPayoutHour(), 0)));
        long[] summary = groupService.openSummary(payDate);
        if (summary[0] > 0) {
            notifier.payoutRequestedSummary(payDate, (int) summary[0], summary[1], null, null);
        }
    }
}
