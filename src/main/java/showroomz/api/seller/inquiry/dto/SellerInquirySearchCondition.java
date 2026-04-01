package showroomz.api.seller.inquiry.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import showroomz.api.seller.inquiry.type.MarketInquiryFilterType;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SellerInquirySearchCondition {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private List<MarketInquiryFilterType> inquiryTypes;

    private InquiryStatus status;

    private String keyword;
}
