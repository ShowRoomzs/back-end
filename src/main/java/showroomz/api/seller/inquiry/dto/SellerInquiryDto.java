package showroomz.api.seller.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class SellerInquiryDto {

    private Long inquiryId;
    private String source;
    private String inquiryType;
    private String content;
    private String customerName;
    private String productName;
    private LocalDateTime createdAt;
    private InquiryStatus status;
}
