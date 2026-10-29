package showroomz.api.app.inquiry.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("공개 상품 문의 작성자 표기 — 닉네임 앞 절반만(시안 C7 · 58 계획 2-3)")
class PublicProductInquiryMaskTest {

    @Test
    @DisplayName("짝수는 절반 · 홀수는 가리는 쪽을 더 많이 · 한 글자는 전부 가린다 · 없으면 「알 수 없음」")
    void maskHalf() {
        assertThat(PublicProductInquiryResponse.maskHalf("수민초록")).isEqualTo("수민**");
        assertThat(PublicProductInquiryResponse.maskHalf("지은맑")).isEqualTo("지**");
        assertThat(PublicProductInquiryResponse.maskHalf("현지")).isEqualTo("현*");
        assertThat(PublicProductInquiryResponse.maskHalf("윤")).isEqualTo("*");
        assertThat(PublicProductInquiryResponse.maskHalf(" ")).isEqualTo("알 수 없음");
        assertThat(PublicProductInquiryResponse.maskHalf(null)).isEqualTo("알 수 없음");
    }
}
