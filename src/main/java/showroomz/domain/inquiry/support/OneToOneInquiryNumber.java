package showroomz.domain.inquiry.support;

import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 1:1 문의번호 INQ-YYYYMMDD-NNN (§17-3) — NNN은 접수 일자 내 순번이다. 어드민 1:1 문의 관리와 소비자 상세 「최근 문의」가 같은 번호를 보여야
 * CS 가 대조할 수 있어 한 곳에 둔다(55 계획서 M3-2 · 상품 문의는 {@link ProductInquiryNumber}).
 */
public final class OneToOneInquiryNumber {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OneToOneInquiryNumber() {
    }

    public static String of(OneToOneInquiry inquiry, OneToOneInquiryRepository repository) {
        LocalDateTime createdAt = inquiry.getCreatedAt();
        LocalDateTime dayStart = KstDates.startOfDay(KstDates.toKstDate(createdAt));
        long sequence = repository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIdLessThanEqual(
                dayStart, dayStart.plusDays(1), inquiry.getId());
        return "INQ-" + KstDates.toKstDate(createdAt).format(DATE) + "-" + String.format("%03d", sequence);
    }
}
