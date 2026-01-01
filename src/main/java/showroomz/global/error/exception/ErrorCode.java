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

    /* * 2. 소셜 로그인 (Social Login)
     */
    MISSING_TOKEN(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "token은 필수 입력값입니다."),
    MISSING_PROVIDER_TYPE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "providerType은 필수 입력값입니다."),
    INVALID_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "INVALID_SOCIAL_PROVIDER", "지원하지 않는 소셜 공급자입니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 액세스 토큰입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 다른 계정에서 사용 중인 이메일입니다."),

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

    /* * 5. 로그아웃 & 탈퇴 (Logout & Withdraw) 
     */
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 유효하지 않습니다."),
    MISSING_REFRESH_TOKEN_LOGOUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Refresh Token이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "아이디 또는 비밀번호가 올바르지 않습니다."),

    /* * 6. 마켓 (Market) 
     */
    DUPLICATE_MARKET_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_MARKET_NAME", "이미 사용 중인 마켓명입니다."),

    /* * 7. 이미지 (Image)
     */
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "유효하지 않은 이미지 타입입니다. (PROFILE, REVIEW, PRODUCT)"),
    EMPTY_FILE_EXCEPTION(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "업로드할 파일이 존재하지 않습니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "이미지 파일(jpg, png, jpeg, gif)만 업로드 가능합니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_SIZE_EXCEEDED", "이미지 파일은 최대 10MB까지만 업로드 가능합니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "파일명이 올바르지 않습니다."),
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "파일 업로드 중 오류가 발생했습니다.");
    
    private final HttpStatus status;
    private final String code;
    private final String message;
}

