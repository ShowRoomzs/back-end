package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.entity.SettlementPayoutGroup;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.repository.SettlementPayoutGroupRepository;
import showroomz.domain.settlement.repository.SettlementPayoutRepository;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 지급 묶음의 날짜 계산 · 묶음 배정(60_KG이니시스_지급대행_전환_계획서.md D-1 · D-2) — PG 호출이 없는 계산과 선점 트랜잭션 안의 배정만 둔다.
 * 전송 · 결과 · 수렴은 {@link SettlementPayoutGroupService} 가 한다(이 클래스를 지시 선점 · 보류 전이가 의존성 순환 없이 쓰게 나눴다).
 *
 * <ul>
 *   <li><b>가장 이른 지급일</b> — 마감(이니시스 15:00) 전이면 업로드일 = 오늘, 지났거나 비영업일이면 다음 영업일. 지급일 = 업로드일 + 리드 영업일.
 *   시뮬레이터(마감 없음 · 리드 0)는 오늘.</li>
 *   <li><b>수정 마감</b> — 지급일 D 묶음은 「D − 리드 영업일」의 마감 시각까지 고칠 수 있다. 지나면 묶음은 고정이다(빠질 행은 지급 뒤 차감 · D-7).</li>
 *   <li><b>배정</b> — 같은 키(수취자 · 지급일 · 차수)의 OPEN 묶음에 합류한다. 바깥 PG 는 서브몰 계좌가 하나라 <b>열린 묶음은 한 계좌를 공유</b>해야
 *   한다 — 계좌가 다른 행은 그 묶음의 지급일 다음 지급일 묶음으로 간다(D-1 ⑤). 시뮬레이터는 계좌 제약이 없어 같은 날 새 차수로 간다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutGroupPlanner {

    private final SettlementPayoutGroupRepository groupRepository;
    private final SettlementPayoutRepository payoutRepository;
    private final SettlementPayoutGateway gateway;
    private final BusinessCalendar businessCalendar;

    /** 묶음 지급 경로인가(시뮬레이터 · 이니시스). */
    public boolean enabled() {
        return gateway.groupsByPayee();
    }

    /** 지금 지시하면 가장 이른 지급일(D-2). */
    public LocalDate earliestPayDate(LocalDateTime now) {
        LocalDateTime kst = KstDates.toKst(now);
        LocalDate today = kst.toLocalDate();
        Optional<LocalTime> cutoff = gateway.uploadCutoff();
        if (cutoff.isEmpty()) {
            return today;
        }
        LocalDate uploadDay = businessCalendar.isBusinessDay(today) && kst.toLocalTime().isBefore(cutoff.get())
                ? today : businessCalendar.addBusinessDays(today, 1);
        return businessCalendar.addBusinessDays(uploadDay, gateway.leadBusinessDays());
    }

    /** 지급일 D 묶음의 수정 마감(서버 시각) — empty = 마감 없음(올리는 즉시 확정 · 시뮬레이터). */
    public Optional<LocalDateTime> deadline(LocalDate payDate) {
        return gateway.uploadCutoff().map(cutoff -> KstDates.at(uploadDay(payDate), cutoff));
    }

    /** 지급일 D 의 업로드일 — D 에서 리드 영업일만큼 앞. */
    public LocalDate uploadDay(LocalDate payDate) {
        LocalDate day = payDate;
        for (int i = 0; i < gateway.leadBusinessDays(); i++) {
            day = businessCalendar.previousBusinessDay(day);
        }
        return day;
    }

    /** 지급일 D 묶음을 아직 고칠 수 있는가 — 마감이 없는 모드는 false(고칠 일이 없다). */
    public boolean isEditable(LocalDate payDate, LocalDateTime now) {
        return deadline(payDate).map(now::isBefore).orElse(false);
    }

    /**
     * 지금 바로 묶음에 넣고 올릴 수 있는 시간인가 — 영업일 마감 전(D-2′). 마감 뒤 · 비영업일의 재분배는 {@code SCHEDULED} 로만 두고 다음 틱 · 배치가
     * 집는다. 마감이 없는 모드는 언제나 true.
     */
    public boolean isUploadWindow(LocalDateTime now) {
        Optional<LocalTime> cutoff = gateway.uploadCutoff();
        if (cutoff.isEmpty()) {
            return true;
        }
        LocalDateTime kst = KstDates.toKst(now);
        return businessCalendar.isBusinessDay(kst.toLocalDate()) && kst.toLocalTime().isBefore(cutoff.get());
    }

    /** 마감이 있는(묶음을 나중에 고치는) 모드인가 — 마감 전 틱 · 빼기(D-7)가 이때만 돈다. */
    public boolean hasCutoff() {
        return gateway.uploadCutoff().isPresent();
    }

    public LocalDate nextPayDate(LocalDate payDate) {
        return businessCalendar.addBusinessDays(payDate, 1);
    }

    /**
     * 지시 선점 트랜잭션 안에서 REQUESTED 행을 묶음에 붙인다 — 묶음을 잠그고(같은 묶음의 동시 전송 직렬화 · D-1 ③) 합류하거나 새로 만든다.
     *
     * @return 붙인 묶음 id — 붙이지 못했으면(행이 이미 REQUESTED 가 아님) null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long attach(Long payoutId, SettlementPayee payee, Long payeeRefId, String partnerExternalId, String accountHash,
                       LocalDate payDate, LocalDateTime now) {
        String provider = gateway.provider();
        String scheduleType = gateway.scheduleType();
        boolean oneAccount = gateway.isExternal();
        LocalDate date = payDate;
        if (oneAccount) {
            // 서브몰 계좌는 하나다 — 계좌가 다른 열린 묶음이 있으면 그 지급일 다음으로(송금 시점 계좌로 나갈 수 있다 · 1-3 · Q3).
            for (SettlementPayoutGroup open : groupRepository.findOpenForPayee(provider, payee, payeeRefId)) {
                LocalDate after = nextPayDate(open.getPayDate());
                if (!open.getAccountHash().equals(accountHash) && after.isAfter(date)) {
                    date = after;
                }
            }
        }
        for (int guard = 0; guard < 30; guard++) {
            List<SettlementPayoutGroup> sameKey = groupRepository.findByKeyForUpdate(provider, payee, payeeRefId, date, scheduleType);
            SettlementPayoutGroup target = sameKey.stream()
                    .filter(g -> g.getStatus().isOpen() && g.getAccountHash().equals(accountHash))
                    .findFirst().orElse(null);
            if (target == null && oneAccount && sameKey.stream().anyMatch(g -> g.getStatus().isOpen())) {
                date = nextPayDate(date);
                continue;
            }
            if (target == null) {
                int seq = sameKey.isEmpty() ? 0 : sameKey.get(0).getSeq() + 1;
                target = groupRepository.save(SettlementPayoutGroup.open(provider, payee, payeeRefId, date, scheduleType, seq,
                        partnerExternalId, accountHash, now));
            } else if (target.getPgPartnerId() == null && partnerExternalId != null) {
                target.linkPartner(partnerExternalId, now);
                groupRepository.save(target);
            }
            Long groupId = target.getId();
            if (payoutRepository.attachToGroup(payoutId, groupId, gateway.isExternal() ? provider : null, date, scheduleType) != 1) {
                return null;
            }
            if (!date.equals(payDate)) {
                log.info("지급 묶음 — 계좌가 다른 열린 묶음이 있어 지급일을 미룬다 - payoutId: {}, {} → {}", payoutId, payDate, date);
            }
            return groupId;
        }
        throw new IllegalStateException("지급 묶음 배정 실패 — 30영업일 안에 들어갈 묶음이 없다 · payoutId " + payoutId);
    }
}
