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
    SIGNUP_DOCUMENT; // 판매자 회원가입 증빙 서류 전용 카테고리

    public static final Set<ImageType> USER_ALLOWED_TYPES =
            EnumSet.of(PROFILE, REVIEW, INQUIRY);

    public static final Set<ImageType> SELLER_ALLOWED_TYPES =
            EnumSet.of(MARKET, PRODUCT);

    public static final Set<ImageType> CREATOR_ALLOWED_TYPES =
            EnumSet.of(POST, PRODUCT, MARKET);

    public static final Set<ImageType> ADMIN_ALLOWED_TYPES =
            EnumSet.of(CATEGORY);

    /** 비로그인(공개) 업로드 허용 타입 */
    public static final Set<ImageType> PUBLIC_ALLOWED_TYPES =
            EnumSet.of(SIGNUP_DOCUMENT);
}

