package showroomz.auth.exception;

import lombok.Getter;
import showroomz.global.error.exception.ErrorCode;

@Getter
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}