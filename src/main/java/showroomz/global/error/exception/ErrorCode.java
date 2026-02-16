package showroomz.global.error.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    /* * 1. 공통 (Common)
     */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"),
    NOT_FOUND_DATA(HttpStatus.NOT_FOUND, "NOT_FOUND_DATA", "데이터를 찾을 수 없습니다."),

    /* * 2. 소셜 로그인 (Social Login)
     */
    MISSING_TOKEN(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "token은 필수 입력값입니다."),
    MISSING_PROVIDER_TYPE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "providerType은 필수 입력값입니다."),
    INVALID_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "INVALID_SOCIAL_PROVIDER", "지원하지 않는 소셜 공급자입니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 액세스 토큰입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 다른 계정에서 사용 중인 이메일입니다."),
    SOCIAL_LOGIN_SUSPENDED(HttpStatus.FORBIDDEN, "DISABLED_SOCIAL_VENDOR", "해당 소셜 로그인은 현재 일시 중단되었습니다."),

    /* * 3. 회원가입 & 회원 정보 (Register & User Info)
     */
    REGISTER_EXPIRED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요."),
    DUPLICATE_USERNAME(HttpStatus.BAD_REQUEST, "DUPLICATE_USERNAME", "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL_SIGNUP(HttpStatus.BAD_REQUEST, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),
    INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "닉네임 형식이 올바르지 않습니다."),
    PROFANITY_DETECTED(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "부적절한 닉네임입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "비밀번호가 일치하지 않습니다."),
    ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "ALREADY_REGISTERED", "이미 회원가입이 완료된 사용자입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 회원입니다."),
    
    // UserController 유효성 검증 에러 추가
    INVALID_AUTH_INFO(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 유효하지 않습니다. 다시 로그인해주세요."),
    INVALID_NICKNAME_LENGTH(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "닉네임은 2자 이상 10자 이하이어야 합니다."),
    INVALID_NICKNAME_CHAR(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "닉네임에 특수문자나 이모티콘을 사용할 수 없습니다."),
    PROFANITY_CONTAINS(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "부적절한 단어가 포함되어 있습니다."),
    INVALID_BIRTHDAY_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "생년월일 형식이 올바르지 않습니다."),
    INVALID_GENDER_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "성별은 MALE 또는 FEMALE만 가능합니다."),

    /* * 4. 토큰 재발급 (Refresh) 
     */
    MISSING_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "refreshToken은 필수 입력값입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요."),

    /* * 5. 로그아웃 & 탈퇴 & 권한 (Logout & Withdraw & Authorization) 
     */
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "USER_WITHDRAWN", "탈퇴한 회원입니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 유효하지 않습니다."),
    MISSING_REFRESH_TOKEN_LOGOUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Refresh Token이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "아이디 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "해당 리소스에 대한 접근 권한이 없습니다."),
    
    // 승인 대기 중 로그인 시도 에러
    ACCOUNT_NOT_APPROVED(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_APPROVED", "관리자 승인 대기 중인 계정입니다."),
    ACCOUNT_REJECTED(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED", "가입 승인이 반려된 계정입니다."),
    ACCOUNT_REJECTED_WITH_REASON(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED_WITH_REASON", "가입 승인이 반려된 계정입니다."),

    /* * 6. 마켓 (Market) 
     */
    DUPLICATE_MARKET_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_MARKET_NAME", "이미 사용 중인 마켓명입니다."),
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND, "MARKET_NOT_FOUND", "존재하지 않는 마켓입니다."),

    /* * 7. 이미지 (Image)
     */
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, PRODUCT, MARKET)"),
    EMPTY_FILE_EXCEPTION(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "업로드할 파일이 존재하지 않습니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "지원하지 않는 이미지 형식입니다"),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_EXCEEDED", "이미지 용량은 최대 20MB까지 등록 가능합니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "파일명이 올바르지 않습니다."),
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "파일 업로드 중 오류가 발생했습니다."),
    
    // 마켓 이미지 전용 검증 에러
    IMAGE_RESOLUTION_TOO_LOW(HttpStatus.BAD_REQUEST, "IMAGE_RESOLUTION_TOO_LOW", "이미지는 최소 160×160px 이상이어야 합니다."),
    IMAGE_RATIO_NOT_SQUARE(HttpStatus.BAD_REQUEST, "IMAGE_RATIO_NOT_SQUARE", "정비율의 이미지만 업로드 가능합니다."),

    /* * 8. 상품 (Product)
     */
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "존재하지 않는 카테고리입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다."),
    PRODUCT_LIST_EMPTY(HttpStatus.NOT_FOUND, "PRODUCT_LIST_EMPTY", "해당 상품이 존재하지 않습니다."),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "존재하지 않는 옵션입니다."),
    VARIANT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "VARIANT_NOT_AVAILABLE", "노출되지 않는 옵션입니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
    INVALID_VARIANT_OPTIONS(HttpStatus.BAD_REQUEST, "INVALID_VARIANT_OPTIONS", "옵션 조합이 올바르지 않습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_CATEGORY_NAME", "이미 존재하는 카테고리명입니다."),
    CATEGORY_IN_USE(HttpStatus.BAD_REQUEST, "CATEGORY_IN_USE", "사용 중인 카테고리는 삭제할 수 없습니다."),

    /* * 9. 은행 (Bank)
     */
    BANK_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_NOT_FOUND", "존재하지 않는 은행 코드입니다."),

    /* * 10. 배송지 (Address)
     */
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "존재하지 않는 배송지입니다."),
    MAX_ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "MAX_ADDRESS_LIMIT_EXCEEDED", "배송지는 최대 10개까지만 등록 가능합니다."),
    ADDRESS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ADDRESS_ACCESS_DENIED", "해당 배송지에 대한 권한이 없습니다."),
    DEFAULT_ADDRESS_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DEFAULT_ADDRESS_DELETE_NOT_ALLOWED", "기본 배송지는 삭제할 수 없습니다. 다른 배송지를 기본으로 지정 후 삭제해주세요."),

    /* 11. 1:1 문의 (Inquiry)
     */
    INQUIRY_ALREADY_ANSWERED(HttpStatus.BAD_REQUEST, "INQUIRY_ALREADY_ANSWERED", "답변이 완료된 문의는 수정하거나 삭제할 수 없습니다."),
    INVALID_INQUIRY_TYPE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "올바르지 않은 문의 타입입니다. (DELIVERY, ORDER_PAYMENT, CANCEL_REFUND_EXCHANGE, USER_INFO, PRODUCT_CHECK, SERVICE)"),

    /* 12. 쿠폰 (Coupon)
     */
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않거나 유효하지 않은 쿠폰 코드입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료되었거나 아직 사용 기간이 아닌 쿠폰입니다."),
    COUPON_ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_REGISTERED", "이미 등록된 쿠폰입니다."),
    COUPON_CODE_DUPLICATE(HttpStatus.BAD_REQUEST, "COUPON_CODE_DUPLICATE", "이미 사용 중인 쿠폰 코드입니다."),
    INVALID_COUPON_VALIDITY_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALIDITY_PERIOD", "유효 시작 일시는 유효 종료 일시보다 이전이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

