package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.repository.GroupBuyNumberSequenceRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 공구번호 GB-YYYYMMDD-NNN 발급(설계서 1-10). 날짜는 공구 생성일(= 체결일)의 <b>한국 날짜</b>이고, 일련번호 시퀀스도
 * 같은 한국 날짜 키로 증가한다 — 서버 시각(UTC)의 날짜를 쓰면 KST 00:00~08:59 체결 건이 하루 전 번호를 받는다.
 *
 * <p>{@code MANDATORY} — 생성 트랜잭션 밖에서 번호를 태우지 않는다. 체결이 롤백되면 번호도 함께 돌아간다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyNumberGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GroupBuyNumberSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String generate(LocalDateTime now) {
        LocalDate date = KstDates.toKstDate(now);
        sequenceRepository.increment(date);
        Integer seq = sequenceRepository.findLastSeq(date);
        return "GB-%s-%03d".formatted(date.format(DATE_PART), seq);
    }
}
