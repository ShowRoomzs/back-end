package showroomz.api.seller.inquiry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
@AllArgsConstructor
public class SellerInquiryListResponse {

    private long totalCount;
    private long waitingCount;
    private Page<SellerInquiryDto> inquiries;
}
