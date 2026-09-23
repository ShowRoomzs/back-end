package showroomz.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.contract.repository.ContractNumberSequenceRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 계약번호 CTR-YYYYMMDD-NNN 발급(설계서 1-7).
 *
 * <p>부여 시점은 <b>검토 요청</b>이다 — 초안을 만들 때마다 번호를 태우면 버려진 초안이 번호를 먹어
 * 일련번호에 구멍이 생긴다.
 *
 * <p>별도 트랜잭션으로 떼지 않는다. 번호 부여가 전이와 같은 트랜잭션 안에 있어야
 * 번호 없는 REVIEW_PENDING이 생기지 않는다(설계서 4-3).
 */
@Component
@RequiredArgsConstructor
public class ContractNumberGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ContractNumberSequenceRepository sequenceRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public String generate(LocalDate date) {
        sequenceRepository.increment(date);
        Integer seq = sequenceRepository.findLastSeq(date);
        return "CTR-%s-%03d".formatted(date.format(DATE_PART), seq);
    }
}
