package showroomz.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import showroomz.auth.DTO.ErrorResponse;
import showroomz.auth.DTO.ValidationErrorResponse;
import showroomz.global.error.exception.ErrorCode;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "showroomz")
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // RegisterRequest의 경우 ValidationErrorResponse 반환
        String requestType = e.getParameter().getParameterType().getSimpleName();
        if ("RegisterRequest".equals(requestType)) {
            var fieldErrors = e.getBindingResult().getFieldErrors().stream()
                    .map(error -> new ValidationErrorResponse.FieldError(
                            error.getField(),
                            error.getDefaultMessage()))
                    .collect(Collectors.toList());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ValidationErrorResponse(ErrorCode.INVALID_INPUT_VALUE.getCode(), 
                            ErrorCode.INVALID_INPUT_VALUE.getMessage(), fieldErrors));
        }
        
        // 다른 경우 기본 ErrorResponse 반환
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값 검증에 실패했습니다.");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT_VALUE.getCode(), 
                        message.isEmpty() ? ErrorCode.INVALID_INPUT_VALUE.getMessage() : message));
    }
}

