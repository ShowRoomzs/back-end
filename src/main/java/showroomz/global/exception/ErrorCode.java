package showroomz.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common (공통 에러)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 에러가 발생했습니다."),

    // User / Auth (회원 관련)
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "U001", "이미 사용 중인 이메일입니다."),
    DUPLICATE_USERNAME(HttpStatus.BAD_REQUEST, "U002", "이미 사용 중인 아이디입니다."),
    DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "U003", "이미 사용 중인 닉네임입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "U004", "비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U005", "존재하지 않는 회원입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "U006", "유효하지 않은 토큰입니다."),
    INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST, "U007", "닉네임 형식이 올바르지 않습니다."),
    PROFANITY_DETECTED(HttpStatus.BAD_REQUEST, "U008", "부적절한 닉네임입니다."),
    
    // Market / Brand (마켓/브랜드 관련)
    DUPLICATE_MARKET_NAME(HttpStatus.BAD_REQUEST, "M001", "이미 사용 중인 마켓명입니다."),
    DUPLICATE_BRAND_NAME(HttpStatus.BAD_REQUEST, "B001", "이미 사용 중인 브랜드명입니다.");

    private final HttpStatus status;
    private final String code;    // 프론트엔드가 식별할 코드 (예: U001)
    private final String message; // 사용자에게 보여줄 메시지
}

