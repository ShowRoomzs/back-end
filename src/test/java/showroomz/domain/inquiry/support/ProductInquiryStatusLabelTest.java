package showroomz.domain.inquiry.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 축을 한 열에 합쳐 보여줄 때의 라벨 (§23-1).
 *
 * <p>화면은 한 열이지만 내부 값은 답변 축·노출 축 둘이다. 합칠 때 <b>노출 축이 앞선다</b> —
 * 삭제 요청이 들어온 답변완료 건을 "답변완료"로 표시하면 운영자가 검토할 건을 목록에서 찾지 못한다.
 * 라벨이 어느 축을 이기는지는 이 함수 한 곳에만 있으므로 여기서 못 박는다.
 */
class ProductInquiryStatusLabelTest {

    @Test
    @DisplayName("정상 노출이면 답변 축을 그대로 보여준다")
    void normalExposureShowsAnswerAxis() {
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.WAITING, InquiryExposureStatus.NORMAL))
                .isEqualTo("답변대기");
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.ANSWERED, InquiryExposureStatus.NORMAL))
                .isEqualTo("답변완료");
    }

    /** 답변이 달렸든 안 달렸든 검토 대기 중임이 먼저 보여야 운영자가 처리할 건을 놓치지 않는다. */
    @Test
    @DisplayName("삭제 요청 중이면 답변 여부와 무관하게 삭제 요청으로 보인다")
    void deleteRequestedOutranksAnswerAxis() {
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.WAITING, InquiryExposureStatus.DELETE_REQUESTED))
                .isEqualTo(InquiryExposureStatus.DELETE_REQUESTED.getDescription());
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.ANSWERED, InquiryExposureStatus.DELETE_REQUESTED))
                .isEqualTo(InquiryExposureStatus.DELETE_REQUESTED.getDescription());
    }

    @Test
    @DisplayName("삭제 집행된 건은 답변 여부와 무관하게 삭제로 보인다")
    void deletedOutranksAnswerAxis() {
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.ANSWERED, InquiryExposureStatus.DELETED))
                .isEqualTo(InquiryExposureStatus.DELETED.getDescription());
    }

    /** 노출 축은 not-null 컬럼이지만 라벨이 NPE로 화면을 깨뜨릴 이유는 없다. */
    @Test
    @DisplayName("노출 축이 비어 있으면 답변 축으로 판단한다")
    void nullExposureFallsBackToAnswerAxis() {
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.ANSWERED, null)).isEqualTo("답변완료");
        assertThat(ProductInquiryStatusLabel.of(InquiryStatus.WAITING, null)).isEqualTo("답변대기");
    }

    @Test
    @DisplayName("모든 노출 상태가 라벨을 갖는다 — 상태가 늘어도 빈 칸이 생기지 않는다")
    void everyExposureStatusHasLabel() {
        for (InquiryExposureStatus exposureStatus : InquiryExposureStatus.values()) {
            assertThat(ProductInquiryStatusLabel.of(InquiryStatus.WAITING, exposureStatus)).isNotBlank();
        }
    }
}
