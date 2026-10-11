package showroomz.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.settlement.repository.ClawbackNumberSequenceRepository;
import showroomz.domain.settlement.repository.SettlementNumberSequenceRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 정산번호 STL-YYMM-NNN · 차감번호 CLW-NNNN 발급(44 어드민 설계서 1-9) — 체계는 §46 C-12 임의라 바뀌면 이 클래스만 고친다.
 *
 * <p>월별 리셋 · 3자리(999 를 넘으면 4자리로 늘어난다 — {@code %03d}). 월은 <b>한국 날짜</b> 기준이다(계약번호와 같은 이유 ·
 * {@link KstDates}). 생성 트랜잭션 안에서만 부른다 — 번호 없는 정산이 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementNumberGenerator {

    private static final DateTimeFormatter MONTH_PART = DateTimeFormatter.ofPattern("yyMM");

    private final SettlementNumberSequenceRepository settlementSequenceRepository;
    private final ClawbackNumberSequenceRepository clawbackSequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextSettlementNumber(LocalDateTime now) {
        String month = KstDates.toKstDate(now).format(MONTH_PART);
        settlementSequenceRepository.increment(month);
        Integer seq = settlementSequenceRepository.findLastNo(month);
        return "STL-%s-%03d".formatted(month, seq);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextClawbackNumber() {
        clawbackSequenceRepository.increment();
        return "CLW-%04d".formatted(clawbackSequenceRepository.findLastNo());
    }
}
