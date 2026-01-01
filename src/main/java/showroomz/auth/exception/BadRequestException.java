package showroomz.auth.exception;

import lombok.Getter;
import showroomz.global.error.exception.ErrorCode;

@Getter
public class BadRequestException extends RuntimeException {
    
    private final ErrorCode errorCode;

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}