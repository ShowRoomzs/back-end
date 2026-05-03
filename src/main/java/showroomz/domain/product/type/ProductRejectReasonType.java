package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductRejectReasonType {
    INFO_MISMATCH("상품 정보 불일치"),
    IMAGE_POLICY("이미지/콘텐츠 기준 미달"),
    POLICY_VIOLATION("운영 정책 위반"),
    DUPLICATE_OR_FALSE("중복/허위 등록 의심"),
    OTHER("기타(직접 작성)");

    private final String description;
}
