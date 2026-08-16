package showroomz.global.error.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    /* * 1. 공통 (Common)
     */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
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
    UNDER_MIN_AGE(HttpStatus.FORBIDDEN, "UNDER_MIN_AGE", "만 14세 미만은 가입할 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 회원입니다."),
    CREATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "CREATOR_NOT_FOUND", "존재하지 않는 크리에이터입니다."),
    
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
    // C15-4 탈퇴 확인 — 진행 중 주문이 있으면 배송·교환·환불이 끝날 때까지 탈퇴를 막는다
    WITHDRAWAL_BLOCKED_BY_ORDER(HttpStatus.CONFLICT, "WITHDRAWAL_BLOCKED_BY_ORDER", "진행 중인 주문이 있어 지금은 탈퇴할 수 없습니다."),
    WITHDRAWAL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "WITHDRAWAL_CONSENT_REQUIRED", "계정과 활동 기록이 삭제되는 데 동의해야 합니다."),
    // C15-2 회원정보 변경 — 재인증은 가입 시 동의와 별개의 새 수집 행위라 매번 다시 동의를 받는다
    IDENTITY_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "IDENTITY_CONSENT_REQUIRED", "본인확인을 위한 개인정보 수집·이용에 동의해야 합니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 유효하지 않습니다."),
    MISSING_REFRESH_TOKEN_LOGOUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Refresh Token이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "아이디 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_NOT_REGISTERED(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_REGISTERED", "등록되지 않은 이메일입니다."),
    LOGIN_PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "해당 리소스에 대한 접근 권한이 없습니다."),
    
    // 승인 대기 중 로그인 시도 에러
    ACCOUNT_NOT_APPROVED(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_APPROVED", "관리자 승인 대기 중인 계정입니다."),
    ACCOUNT_REJECTED(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED", "가입 승인이 반려된 계정입니다."),
    ACCOUNT_REJECTED_WITH_REASON(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED_WITH_REASON", "가입 승인이 반려된 계정입니다."),
    ACCOUNT_NOT_PENDING(HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_PENDING", "승인 대기 상태인 계정만 처리할 수 있습니다."),
    ACCOUNT_ROLE_MISMATCH(HttpStatus.BAD_REQUEST, "ACCOUNT_ROLE_MISMATCH", "해당 계정의 유형이 올바르지 않습니다."),
    DUPLICATE_APPLICATION(HttpStatus.BAD_REQUEST, "DUPLICATE_APPLICATION", "이미 검수 대기 중인 신청이 있습니다."),
    APPLICATION_REAPPLY_COOLDOWN(HttpStatus.BAD_REQUEST, "APPLICATION_REAPPLY_COOLDOWN", "반려일로부터 14일이 지나야 다시 신청할 수 있습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "존재하지 않는 신청입니다."),
    INVALID_APPLICATION_STATUS(HttpStatus.BAD_REQUEST, "INVALID_APPLICATION_STATUS", "검수 대기 상태인 신청만 처리할 수 있습니다."),

    /* * 6. 마켓 (Market) 
     */
    DUPLICATE_MARKET_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_MARKET_NAME", "이미 사용 중인 마켓명입니다."),
    DUPLICATE_SHOWROOM_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_SHOWROOM_NAME", "이미 사용 중인 쇼룸명입니다."),
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND, "MARKET_NOT_FOUND", "존재하지 않는 마켓입니다."),
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "존재하지 않는 판매자입니다."),

    /* * 7. 이미지 (Image)
     */
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, INQUIRY, POST, PRODUCT, MARKET, CATEGORY, SIGNUP_DOCUMENT, CREATOR_DOCUMENT, CHANGE_REQUEST_DOCUMENT, SHOWROOM_PROFILE)"),
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
    PRODUCT_NOT_OWNED_BY_SELLER(HttpStatus.FORBIDDEN, "PRODUCT_NOT_OWNED_BY_SELLER", "해당 판매자 소유의 상품이 아닙니다."),
    PRODUCT_LIST_EMPTY(HttpStatus.NOT_FOUND, "PRODUCT_LIST_EMPTY", "해당 상품이 존재하지 않습니다."),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "존재하지 않는 옵션입니다."),
    VARIANT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "VARIANT_NOT_AVAILABLE", "노출되지 않는 옵션입니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
    INVALID_VARIANT_OPTIONS(HttpStatus.BAD_REQUEST, "INVALID_VARIANT_OPTIONS", "옵션 조합이 올바르지 않습니다."),
    PRODUCT_EDIT_RESTRICTED(HttpStatus.BAD_REQUEST, "PRODUCT_EDIT_RESTRICTED",
            "진열 중이며 공구 진행 중인 상품은 옵션·재고만 수정할 수 있습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다."),
    CART_ITEM_NOT_PURCHASABLE(HttpStatus.BAD_REQUEST, "CART_ITEM_NOT_PURCHASABLE", "마감되었거나 품절되어 주문할 수 없는 상품입니다."),
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
    INQUIRY_ALREADY_ANSWERED(HttpStatus.BAD_REQUEST, "INQUIRY_ALREADY_ANSWERED", "이미 답변이 등록된 문의입니다. 답변은 1회만 등록할 수 있습니다."),
    INVALID_INQUIRY_TYPE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "올바르지 않은 문의 유형입니다. (DELIVERY, CANCEL_EXCHANGE_RETURN, ORDER_PAYMENT, SERVICE, ACCOUNT)"),

    /* 11-1. 상품 문의 (§23 파트너센터 문의 관리)
     */
    INQUIRY_NOT_ANSWERED(HttpStatus.BAD_REQUEST, "INQUIRY_NOT_ANSWERED", "아직 답변이 등록되지 않은 문의입니다."),
    INQUIRY_UNDER_DELETE_REVIEW(HttpStatus.BAD_REQUEST, "INQUIRY_UNDER_DELETE_REVIEW", "삭제 요청을 운영자가 검토 중인 문의입니다. 검토 결과가 나올 때까지 조작할 수 없습니다."),
    INQUIRY_DELETE_ALREADY_REQUESTED(HttpStatus.BAD_REQUEST, "INQUIRY_DELETE_ALREADY_REQUESTED", "이미 삭제를 요청한 문의입니다. 요청은 취소할 수 없습니다."),
    INQUIRY_DELETE_NOT_REQUESTED(HttpStatus.BAD_REQUEST, "INQUIRY_DELETE_NOT_REQUESTED", "삭제 요청이 없는 문의입니다."),
    INQUIRY_DELETE_REASON_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "기타(직접 입력) 사유는 상세 설명이 필요합니다."),
    INQUIRY_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "INQUIRY_ALREADY_DELETED", "이미 삭제된 문의입니다."),

    /* 12. 쿠폰 (Coupon)
     */
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않거나 유효하지 않은 쿠폰 코드입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료되었거나 아직 사용 기간이 아닌 쿠폰입니다."),
    COUPON_QUANTITY_EXHAUSTED(HttpStatus.BAD_REQUEST, "COUPON_QUANTITY_EXHAUSTED", "발급 가능한 쿠폰 수량이 모두 소진되었습니다."),
    COUPON_ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_REGISTERED", "이미 등록된 쿠폰입니다."),
    COUPON_CODE_DUPLICATE(HttpStatus.BAD_REQUEST, "COUPON_CODE_DUPLICATE", "이미 사용 중인 쿠폰 코드입니다."),
    USER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_COUPON_NOT_FOUND", "보유하지 않은 사용자 쿠폰입니다."),
    COUPON_MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_AMOUNT_NOT_MET", "최소 주문 금액을 충족하지 않습니다."),
    INVALID_COUPON_VALIDITY_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALIDITY_PERIOD", "유효 시작 일시는 유효 종료 일시보다 이전이어야 합니다."),

     /* 13. 리뷰 (Review)
     */
    ORDER_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_PRODUCT_NOT_FOUND", "주문 상품을 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "REVIEW_ALREADY_EXISTS", "이미 해당 주문 상품에 리뷰를 작성하셨습니다."),
    ORDER_PRODUCT_NOT_WRITABLE(HttpStatus.BAD_REQUEST, "ORDER_PRODUCT_NOT_WRITABLE", "리뷰 작성이 가능한 상태가 아닙니다. (구매 확정 완료 상품만 리뷰 작성 가능)"),
    ORDER_PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_PRODUCT_ACCESS_DENIED", "해당 주문 상품에 대한 권한이 없습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "REVIEW_ACCESS_DENIED", "해당 리뷰에 대한 수정/삭제 권한이 없습니다."),

   /* 14. 게시글 (Post)
     */
   POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "존재하지 않는 게시글입니다."),
   POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_ACCESS_DENIED", "해당 게시글에 대한 권한이 없습니다."),

   /* 14-1. 쇼룸 포스트 (§24)
    * 사진·본문 미입력은 FE에서 버튼 비활성으로만 표현되지만(§24-3), API 직접 호출로 빈 게시물이
    * 생기는 것을 막기 위해 서버도 거절한다. 문구가 화면에 뜰 일은 없다.
    */
   POST_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "POST_IMAGE_REQUIRED", "게시하려면 사진이 최소 1장 필요합니다."),
   POST_EMPTY(HttpStatus.BAD_REQUEST, "POST_EMPTY", "사진 또는 본문 중 하나는 입력해야 합니다."),
   POST_IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "POST_IMAGE_LIMIT_EXCEEDED", "사진은 게시물당 최대 20장까지 등록할 수 있습니다."),
   POST_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "POST_CONTENT_TOO_LONG", "본문은 최대 2,000자까지 입력할 수 있습니다."),
   POST_ASPECT_RATIO_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "POST_ASPECT_RATIO_OUT_OF_RANGE", "사진 비율은 1.91:1 ~ 4:5 범위여야 합니다."),
   POST_NOT_EDITABLE(HttpStatus.CONFLICT, "POST_NOT_EDITABLE", "노출 중지·심사 중인 게시물은 수정할 수 없습니다."),
   POST_NOT_DELETABLE(HttpStatus.CONFLICT, "POST_NOT_DELETABLE", "이의 심사 중인 게시물은 삭제할 수 없습니다."),
   POST_ALREADY_PUBLISHED(HttpStatus.CONFLICT, "POST_ALREADY_PUBLISHED", "이미 게시된 게시물입니다."),
   POST_NOT_SUSPENDED(HttpStatus.CONFLICT, "POST_NOT_SUSPENDED", "노출 중지된 게시물이 아닙니다."),
   POST_SUSPENSION_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "POST_SUSPENSION_DETAIL_REQUIRED", "기타 사유를 선택한 경우 상세 사유는 필수입니다."),
   POST_APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_APPEAL_NOT_FOUND", "존재하지 않는 이의 신청입니다."),
   POST_APPEAL_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "POST_APPEAL_ALREADY_SUBMITTED", "이의 신청은 게시물당 1회만 가능합니다."),
   POST_APPEAL_DEADLINE_PASSED(HttpStatus.CONFLICT, "POST_APPEAL_DEADLINE_PASSED", "이의 신청 기한이 지났습니다."),
   POST_APPEAL_ALREADY_REVIEWED(HttpStatus.CONFLICT, "POST_APPEAL_ALREADY_REVIEWED", "이미 심사가 끝난 이의 신청입니다."),
   POST_ORIGINAL_DOWNLOAD_UNAVAILABLE(HttpStatus.FORBIDDEN, "POST_ORIGINAL_DOWNLOAD_UNAVAILABLE", "원본을 내려받을 수 있는 기간이 아닙니다."),

   /* 15. 위시리스트 (Wishlist)
    */
   WISHLIST_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "WISHLIST_ALREADY_EXISTS", "이미 위시리스트에 추가된 항목입니다."),

   /* 16. 상품 공지 (ProductAnnouncement) */
   PRODUCT_ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_ANNOUNCEMENT_NOT_FOUND", "상품 공지사항을 찾을 수 없습니다."),
   PRODUCT_ANNOUNCEMENT_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "PRODUCT_ANNOUNCEMENT_TARGET_REQUIRED", "지정 노출(SPECIFIC)인 경우 대상 상품 ID가 1개 이상 필요합니다."),
   PRODUCT_ANNOUNCEMENT_TARGET_NOT_ALLOWED_FOR_ALL(HttpStatus.BAD_REQUEST, "PRODUCT_ANNOUNCEMENT_TARGET_NOT_ALLOWED_FOR_ALL", "전체 노출(ALL)인 경우 대상 상품 ID를 지정할 수 없습니다."),
   PRODUCT_NOT_FOUND_FOR_ANNOUNCEMENT(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_FOUND_FOR_ANNOUNCEMENT", "존재하지 않는 상품 ID가 포함되어 있습니다."),

    /* * 17. 연결·소통 (Connection)
     */
    CONNECTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CONNECTION_ALREADY_EXISTS", "이미 연결되었거나 요청중인 상대입니다."),
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONNECTION_NOT_FOUND", "존재하지 않는 연결 요청입니다."),
    CONNECTION_CODE_INVALID(HttpStatus.BAD_REQUEST, "CONNECTION_CODE_INVALID", "연결코드는 영문 대문자와 숫자로만 입력할 수 있습니다."),
    CONNECTION_INVALID_STATUS(HttpStatus.CONFLICT, "CONNECTION_INVALID_STATUS", "처리할 수 없는 연결 상태입니다."),
    CONNECTION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CONNECTION_ACCESS_DENIED", "해당 연결 요청에 대한 권한이 없습니다."),
    CONNECTION_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "CONNECTION_TARGET_REQUIRED", "creatorId 또는 connectionCode 중 하나는 필수입니다."),
    CONNECTION_TARGET_AMBIGUOUS(HttpStatus.BAD_REQUEST, "CONNECTION_TARGET_AMBIGUOUS", "creatorId와 connectionCode는 동시에 지정할 수 없습니다."),
    CONNECTION_TARGET_NOT_CONNECTABLE(HttpStatus.BAD_REQUEST, "CONNECTION_TARGET_NOT_CONNECTABLE", "연결할 수 없는 상대입니다."),

    /* * 18. 소통 스레드 (Message)
     */
    THREAD_NOT_FOUND(HttpStatus.NOT_FOUND, "THREAD_NOT_FOUND", "존재하지 않는 스레드입니다."),
    THREAD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "THREAD_ACCESS_DENIED", "해당 스레드에 대한 권한이 없습니다."),
    THREAD_DORMANT(HttpStatus.CONFLICT, "THREAD_DORMANT", "연결이 해제된 스레드입니다. 열람만 가능합니다."),
    MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "MESSAGE_EMPTY", "메시지 내용 또는 첨부 중 하나는 필요합니다."),
    ATTACHMENT_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "ATTACHMENT_COUNT_EXCEEDED", "첨부는 메시지 1건당 최대 20개까지 가능합니다."),
    ATTACHMENT_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "ATTACHMENT_SIZE_EXCEEDED", "첨부 총 용량은 메시지 1건당 500MB를 초과할 수 없습니다."),
    ATTACHMENT_EXTENSION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "ATTACHMENT_EXTENSION_NOT_ALLOWED", "허용되지 않는 파일 형식입니다."),
    ATTACHMENT_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "ATTACHMENT_NOT_UPLOADED", "업로드가 완료되지 않은 첨부입니다."),
    ATTACHMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ATTACHMENT_ACCESS_DENIED", "해당 첨부에 대한 권한이 없습니다."),
    ATTACHMENT_ALREADY_ATTACHED(HttpStatus.CONFLICT, "ATTACHMENT_ALREADY_ATTACHED", "이미 다른 메시지에 연결된 첨부입니다."),

    /* * 19. 브랜드 기본정보 · 변경 요청 (Brand Basic Info / Change Request, §15·§16)
     */
    CHANGE_REQUEST_ALREADY_PENDING(HttpStatus.CONFLICT, "CHANGE_REQUEST_ALREADY_PENDING", "이미 검토 중인 변경 요청이 있습니다."),
    CHANGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "CHANGE_REQUEST_NOT_FOUND", "존재하지 않는 변경 요청입니다."),
    CHANGE_REQUEST_NOT_PENDING(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_NOT_PENDING", "검토 대기 상태인 요청만 처리할 수 있습니다."),
    CHANGE_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHANGE_REQUEST_ACCESS_DENIED", "해당 변경 요청에 대한 권한이 없습니다."),
    CHANGE_REQUEST_FIELD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_FIELD_NOT_ALLOWED", "요청할 수 없는 항목입니다."),
    CHANGE_REQUEST_VALUE_UNCHANGED(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_VALUE_UNCHANGED", "현재 값과 동일한 항목은 요청할 수 없습니다."),
    CHANGE_REQUEST_ITEMS_REQUIRED(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_ITEMS_REQUIRED", "변경 항목을 선택해주세요."),
    CHANGE_REQUEST_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_REASON_REQUIRED", "변경 사유를 입력해주세요."),
    CHANGE_REQUEST_EVIDENCE_REQUIRED(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_EVIDENCE_REQUIRED", "증빙 서류를 첨부해주세요."),
    CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH", "해당 유형에 사용할 수 없는 반려 사유입니다."),
    CHANGE_REQUEST_REJECT_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "CHANGE_REQUEST_REJECT_DETAIL_REQUIRED", "기타 사유를 선택한 경우 상세 사유는 필수입니다."),
    EMAIL_CHANGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "EMAIL_CHANGE_LIMIT_EXCEEDED", "로그인 이메일은 월 1회만 변경할 수 있습니다."),
    NEW_PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "NEW_PASSWORD_CONFIRM_MISMATCH", "비밀번호가 일치하지 않습니다."),

    /* * 20. 쇼룸 관리 (Showroom, §22)
     */
    INVALID_SHOWROOM_NAME_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_SHOWROOM_NAME_FORMAT", "쇼룸명은 2~20자, 한글·영문·숫자·공백만 사용할 수 있습니다."),
    SHOWROOM_INTRODUCTION_TOO_LONG(HttpStatus.BAD_REQUEST, "SHOWROOM_INTRODUCTION_TOO_LONG", "쇼룸 소개글은 최대 50자까지 입력할 수 있습니다."),
    INVALID_INSTAGRAM_URL(HttpStatus.BAD_REQUEST, "INVALID_INSTAGRAM_URL", "https://로 시작하는 올바른 URL을 입력해 주세요."),
    SHOWROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOWROOM_NOT_FOUND", "존재하지 않는 쇼룸입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

