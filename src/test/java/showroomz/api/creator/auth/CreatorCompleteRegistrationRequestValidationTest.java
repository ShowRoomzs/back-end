package showroomz.api.creator.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.creator.auth.DTO.CreatorCompleteRegistrationRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스튜디오 온보딩(ui-studio-03 rev.3 · 상태 C) — 형식 오류 문구가 화면 문구와 같아야 한다. 화면이 실시간으로 먼저 막고 서버는 같은
 * 규칙을 다시 본다.
 */
@DisplayName("크리에이터 온보딩 요청 — 형식 오류 문구")
class CreatorCompleteRegistrationRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("사업자등록번호는 하이픈 포함 000-00-00000 — 틀리면 화면과 같은 문구")
    void businessRegistrationNumberFormat() {
        CreatorCompleteRegistrationRequest request = new CreatorCompleteRegistrationRequest();
        request.setBusinessRegistrationNumber("123456789");

        assertThat(messages(request, "businessRegistrationNumber"))
                .containsExactly("올바른 사업자등록번호 형식으로 입력해 주세요. (예: 000-00-00000)");

        request.setBusinessRegistrationNumber("123-45-67890");
        assertThat(messages(request, "businessRegistrationNumber")).isEmpty();
    }

    @Test
    @DisplayName("계좌번호는 숫자 10~16자리 — 자릿수 부족 · 초과 · 하이픈은 화면과 같은 문구")
    void accountNumberFormat() {
        CreatorCompleteRegistrationRequest request = new CreatorCompleteRegistrationRequest();

        for (String invalid : new String[]{"110123", "12345678901234567", "110-123-456789"}) {
            request.setAccountNumber(invalid);
            assertThat(messages(request, "accountNumber"))
                    .as(invalid)
                    .containsExactly("계좌번호는 10~16자리 숫자로 입력해 주세요.");
        }

        for (String valid : new String[]{"1101234567", "1234567890123456"}) {
            request.setAccountNumber(valid);
            assertThat(messages(request, "accountNumber")).as(valid).isEmpty();
        }
    }

    private static Set<String> messages(CreatorCompleteRegistrationRequest request, String property) {
        Set<ConstraintViolation<CreatorCompleteRegistrationRequest>> violations = validator.validateProperty(request, property);
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
