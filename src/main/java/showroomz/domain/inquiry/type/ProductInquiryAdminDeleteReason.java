package showroomz.domain.inquiry.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 운영자의 문의 삭제 사유 (§18-5) — 내부 기록용이며 작성자·브랜드에게 통지하지 않는다.
 * 브랜드가 요청 시 고르는 {@link ProductInquiryDeleteReason}과는 다른 목록이다.
 */
@Getter
@AllArgsConstructor
public enum ProductInquiryAdminDeleteReason {

    ADVERTISEMENT("광고·홍보성 게시물"),
    ABUSE("비방·욕설"),
    PRIVACY_EXPOSURE("개인정보 노출"),
    ETC("기타(직접 입력)");

    private final String description;

    /** 기타는 상세 사유가 필수다 (§18-5) */
    public boolean requiresDetail() {
        return this == ETC;
    }
}
