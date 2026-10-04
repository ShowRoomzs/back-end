package showroomz.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.order.repository.OrderNumberSequenceRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 주문번호 yyyyMMdd-NNNNNN 발급(결제 계획서 3-2 · 4-7 ①).
 *
 * <p>{@code REQUIRES_NEW} — 공구·계약 번호({@code MANDATORY})와 다르다. 시퀀스 행 잠금을 주문 생성 트랜잭션(T2)과 합치면
 * 재고 차감·INSERT가 끝날 때까지 다른 주문이 번호를 못 받아 주문 생성이 직렬화된다. 여기서 먼저 커밋하므로 T2가
 * 롤백되면 번호 하나가 비는데, 주문번호는 연속성을 약속한 적이 없다.
 *
 * <p>날짜 부분과 일련번호 시퀀스 키는 <b>한국 날짜</b>다 — 서버 시각(UTC)의 날짜를 쓰면 KST 00:00~08:59 주문이 하루 전
 * 번호를 받아 소비자가 보는 주문일과 어긋난다. 계약번호·공구번호와 같은 규칙이다({@link KstDates}).
 */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderNumberSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(LocalDateTime now) {
        LocalDate date = KstDates.toKstDate(now);
        sequenceRepository.increment(date);
        Integer seq = sequenceRepository.findLastSeq(date);
        return "%s-%06d".formatted(date.format(DATE_PART), seq);
    }
}
