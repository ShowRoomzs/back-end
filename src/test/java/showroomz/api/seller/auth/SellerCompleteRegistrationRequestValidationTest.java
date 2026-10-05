package showroomz.api.seller.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.auth.DTO.SellerCompleteRegistrationRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀러 가입의 반품비 · 교환비(보강 시나리오 ON-05 · 앱 클레임 설계서 1-8) — 반품·교환 금액이 기본 배송비 기준이 되면서
 * 두 필드는 선택값이다. 생략하면 서비스가 3,000 / 6,000 을 넣는다(기존 동작).
 */
@DisplayName("셀러 가입 요청 — 반품비 · 교환비 선택값")
class SellerCompleteRegistrationRequestValidationTest {

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
    @DisplayName("[ON-05] 생략해도 검증을 통과한다")
    void optional() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();

        assertThat(validator.validateProperty(request, "returnFee")).isEmpty();
        assertThat(validator.validateProperty(request, "exchangeFee")).isEmpty();
    }

    @Test
    @DisplayName("[ON-05] 보내면 0 이상이어야 한다")
    void notNegative() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        request.setReturnFee(-1);
        request.setExchangeFee(-1);

        assertThat(validator.validateProperty(request, "returnFee")).hasSize(1);
        assertThat(validator.validateProperty(request, "exchangeFee")).hasSize(1);
    }
}
