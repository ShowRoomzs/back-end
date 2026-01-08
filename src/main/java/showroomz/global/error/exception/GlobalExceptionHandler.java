package showroomz.global.error.exception;

import io.sentry.Sentry; // Sentry import 추가
import lombok.extern.slf4j.Slf4j;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;
import showroomz.api.app.auth.exception.BusinessException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice(basePackages = "showroomz")
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BusinessException e) {
        // 비즈니스 로직 예외(400 등)는 보통 Sentry에 보낼 필요 없음 (로그만 남김)
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();

        // 커스텀 메시지가 있으면 사용, 없으면 ErrorCode의 기본 메시지 사용
        String message = e.getMessage() != null && !e.getMessage().equals(errorCode.getMessage()) 
                ? e.getMessage() 
                : errorCode.getMessage();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // RegisterRequest, AdminSignUpRequest의 경우 ValidationErrorResponse 반환
        String requestType = e.getParameter().getParameterType().getSimpleName();
        if ("RegisterRequest".equals(requestType) || "AdminSignUpRequest".equals(requestType)) {
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

    @ExceptionHandler({JpaSystemException.class, DataAccessException.class})
    public ResponseEntity<ErrorResponse> handleDataAccessException(Exception e) {
        // 1. Sentry에 예외 전송 (데이터베이스 오류는 중요하므로 Sentry에 보고)
        Sentry.captureException(e);
        
        // 2. 서버 로그에도 남기기
        log.error("데이터베이스 오류 발생", e);
        
        // SQLException의 원인 메시지 확인
        Throwable rootCause = e.getCause();
        if (rootCause != null && rootCause.getCause() instanceof SQLException) {
            SQLException sqlException = (SQLException) rootCause.getCause();
            String sqlMessage = sqlException.getMessage();
            
            // 사용자 친화적인 메시지로 변환
            if (sqlMessage != null && sqlMessage.contains("doesn't have a default value")) {
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ErrorResponse(
                                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                                "데이터 저장 중 오류가 발생했습니다. 관리자에게 문의해주세요."));
            }
        }
        
        // 기타 데이터베이스 오류
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "데이터베이스 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("접근 권한 없음: {}", e.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        ErrorCode.FORBIDDEN.getCode(),
                        ErrorCode.FORBIDDEN.getMessage()));
    }

    // ★ [수정 완료] 예상치 못한 시스템 예외 (500 에러) 처리 부분
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        
        // 1. Sentry에 예외 전송 (이 한 줄이 핵심입니다!)
        Sentry.captureException(e);
        
        // 2. 서버 로그에도 남기기
        log.error("Unhandled Exception 발생: ", e);

        // 3. 클라이언트 응답
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}

