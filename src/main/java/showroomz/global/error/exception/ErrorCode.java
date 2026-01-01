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
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."), // 닉네임 형식, 비속어, 생년월일 검증용

    /* * 2. 소셜 로그인 (Social Login) 
     */
    MISSING_TOKEN(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "token은 필수 입력값입니다."),
    MISSING_PROVIDER_TYPE(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "providerType은 필수 입력값입니다."),
    INVALID_SOCIAL_PROVIDER(HttpStatus.BAD_REQUEST, "INVALID_SOCIAL_PROVIDER", "지원하지 않는 소셜 공급자입니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효하지 않은 액세스 토큰입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 다른 계정에서 사용 중인 이메일입니다."),

    /* * 3. 회원가입 (Register) 
     */
    REGISTER_EXPIRED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "회원가입 유효 시간이 만료되었습니다. 다시 로그인해주세요."),
    DUPLICATE_USERNAME(HttpStatus.BAD_REQUEST, "DUPLICATE_USERNAME", "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL_SIGNUP(HttpStatus.BAD_REQUEST, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."), // 동적 메시지일 경우 예외 발생 시 메시지 오버라이딩 필요
    INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "닉네임 형식이 올바르지 않습니다."),
    PROFANITY_DETECTED(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "부적절한 닉네임입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "비밀번호가 일치하지 않습니다."),
    ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "ALREADY_REGISTERED", "이미 회원가입이 완료된 사용자입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다."),

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
    DUPLICATE_MARKET_NAME(HttpStatus.BAD_REQUEST, "DUPLICATE_MARKET_NAME", "이미 사용 중인 마켓명입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

