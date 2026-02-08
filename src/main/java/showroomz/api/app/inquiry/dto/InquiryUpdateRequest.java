package showroomz.api.app.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.inquiry.type.InquiryType;

import java.util.List;

@Getter
@NoArgsConstructor
public class InquiryUpdateRequest {

    @NotNull(message = "문의 유형을 선택해주세요.")
    private InquiryType type;

    @NotBlank(message = "상세 유형을 입력해주세요.")
    @Size(max = 50, message = "상세 유형은 50자 이내로 입력해주세요.")
    private String category;

    @NotBlank(message = "문의 내용을 입력해주세요.")
    private String content;

    @Size(max = 10, message = "이미지는 최대 10장까지 첨부 가능합니다.")
    private List<String> imageUrls;
}
