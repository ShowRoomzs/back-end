package showroomz.domain.inquiry.support;

import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 문의번호 QNA-YYYYMMDD-NNN (§23-3) — NNN은 등록 일자 내 순번이다. */
public final class ProductInquiryNumber {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ProductInquiryNumber() {
    }

    public static String of(ProductInquiry inquiry, ProductInquiryRepository repository) {
        LocalDateTime createdAt = inquiry.getCreatedAt();
        LocalDateTime dayStart = createdAt.toLocalDate().atStartOfDay();
        long sequence = repository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIdLessThanEqual(
                dayStart, dayStart.plusDays(1), inquiry.getId());
        return "QNA-" + createdAt.format(DATE) + "-" + String.format("%03d", sequence);
    }
}
