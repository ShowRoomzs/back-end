package showroomz.api.app.auth.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;

/**
 * C0-2 본인인증(PASS) 처리.
 *
 * <p>PASS 연동은 아직 구현 전이므로, 지금은 고정된 임시 인증 결과를 반환한다.
 * 실제 연동 시 {@link #verify(String)}만 PASS 응답(실명·생년월일·성별·연락처·CI/DI)으로 교체하면 되고,
 * 호출부(회원가입)의 만 14세 판별 로직은 그대로 사용할 수 있다.
 *
 * <p>TODO(PASS 연동): 인증 요청/콜백 API 분리, 인증 결과 토큰 발급, CI/DI 저장(중복 가입 방지),
 * 인증 실패·타임아웃(C0 1e) 분기, 재인증 주기 정책.
 */
@Service
public class IdentityVerificationService {

    /** 가입 가능 최소 연령 (정보통신망법상 만 14세) */
    public static final int MIN_AGE = 14;

    // --- PASS 연동 전까지 사용하는 임시 인증 데이터 ---
    private static final String TEMP_NAME = "홍길동";
    private static final String TEMP_BIRTHDAY = "1998-04-12";
    private static final String TEMP_GENDER = "FEMALE";
    private static final String TEMP_PHONE_NUMBER = "01000000000";

    /**
     * 본인인증 결과를 조회한다.
     * PASS 연동 전까지는 username과 무관하게 임시 데이터를 돌려준다.
     */
    public IdentityVerification verify(String username) {
        return new IdentityVerification(
                TEMP_NAME,
                TEMP_BIRTHDAY,
                TEMP_GENDER,
                TEMP_PHONE_NUMBER,
                LocalDateTime.now()
        );
    }

    @Getter
    @AllArgsConstructor
    public static class IdentityVerification {
        private final String name;        // 실명
        private final String birthday;    // "YYYY-MM-DD"
        private final String gender;      // "MALE", "FEMALE"
        private final String phoneNumber; // 숫자만
        private final LocalDateTime verifiedAt;

        /**
         * 인증 결과 생년월일 기준 만 14세 미만 여부.
         * 생년월일을 해석할 수 없으면 가입을 막지 않고 false를 반환한다(인증 단계에서 이미 걸러진 값이므로).
         */
        public boolean isUnderMinAge() {
            if (birthday == null || birthday.isEmpty()) {
                return false;
            }
            try {
                return Period.between(LocalDate.parse(birthday), LocalDate.now()).getYears() < MIN_AGE;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
    }
}
