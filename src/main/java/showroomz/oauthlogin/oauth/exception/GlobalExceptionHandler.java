package showroomz.oauthlogin.oauth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import showroomz.oauthlogin.auth.AuthController;
import showroomz.oauthlogin.auth.DTO.ErrorResponse;
import showroomz.oauthlogin.auth.DTO.ValidationErrorResponse;
import showroomz.oauthlogin.user.UserController;

import java.util.stream.Collectors;

@RestControllerAdvice(annotations = {RestController.class}, basePackageClasses = {AuthController.class, UserController.class})
public class GlobalExceptionHandler {

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
                    .body(new ValidationErrorResponse("INVALID_INPUT", "입력값이 올바르지 않습니다.", fieldErrors));
        }
        
        // 다른 경우 기본 ErrorResponse 반환
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값 검증에 실패했습니다.");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", message));
    }
}

