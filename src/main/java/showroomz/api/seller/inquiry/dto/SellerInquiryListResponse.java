package showroomz.api.seller.inquiry.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;
import showroomz.global.dto.PaginationInfo;

import java.util.List;

@Getter
public class SellerInquiryListResponse {

    private final long totalCount;
    private final long waitingCount;
    private final List<SellerInquiryDto> content;
    private final PaginationInfo pageInfo;

    public SellerInquiryListResponse(long totalCount, long waitingCount, Page<SellerInquiryDto> page) {
        this.totalCount = totalCount;
        this.waitingCount = waitingCount;
        this.content = page.getContent();
        this.pageInfo = new PaginationInfo(page);
    }
}
