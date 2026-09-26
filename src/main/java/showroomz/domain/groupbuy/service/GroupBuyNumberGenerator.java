package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.repository.GroupBuyNumberSequenceRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 공구번호 GB-YYYYMMDD-NNN 발급(설계서 1-10). 날짜는 공구 생성일(= 체결일)이다.
 *
 * <p>{@code MANDATORY} — 생성 트랜잭션 밖에서 번호를 태우지 않는다. 체결이 롤백되면 번호도 함께 돌아간다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyNumberGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GroupBuyNumberSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String generate(LocalDate date) {
        sequenceRepository.increment(date);
        Integer seq = sequenceRepository.findLastSeq(date);
        return "GB-%s-%03d".formatted(date.format(DATE_PART), seq);
    }
}
