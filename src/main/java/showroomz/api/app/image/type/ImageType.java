package showroomz.api.app.image.type;

import java.util.EnumSet;
import java.util.Set;

public enum ImageType {
    PROFILE,
    REVIEW,
    INQUIRY,
    POST,
    PRODUCT,
    MARKET,
    CATEGORY,
    SIGNUP_DOCUMENT, // 판매자 회원가입 증빙 서류
    CREATOR_DOCUMENT, // 크리에이터 추가 정보(사업자등록증·통장사본 등)
    CHANGE_REQUEST_DOCUMENT; // 브랜드 기본정보 변경 요청 증빙(§15-6)

    public static final Set<ImageType> USER_ALLOWED_TYPES =
            EnumSet.of(PROFILE, REVIEW, INQUIRY);

    public static final Set<ImageType> SELLER_ALLOWED_TYPES =
            EnumSet.of(MARKET, PRODUCT, CHANGE_REQUEST_DOCUMENT);

    public static final Set<ImageType> CREATOR_ALLOWED_TYPES =
            EnumSet.of(POST, PRODUCT, MARKET);

    public static final Set<ImageType> ADMIN_ALLOWED_TYPES =
            EnumSet.of(CATEGORY);

    /** 비로그인(공개) 업로드 허용 타입 */
    public static final Set<ImageType> PUBLIC_ALLOWED_TYPES =
            EnumSet.of(SIGNUP_DOCUMENT, CREATOR_DOCUMENT);
}
