package showroomz.api.seller.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.auth.DTO.SellerCompleteRegistrationRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀러 가입 요청 검증. 반품비 · 교환비(보강 시나리오 ON-05 · 앱 클레임 설계서 1-8)는 반품·교환 금액이 기본 배송비 기준이 되면서
 * 선택값이다 — 생략하면 서비스가 3,000 / 6,000 을 넣는다. 발송 기한 · 기본 배송비 · 제주 추가비는 06 설계서 OB-04 ~ OB-07.
 */
@DisplayName("셀러 가입 요청 — 필수 · 선택 · 문구")
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
    @DisplayName("[ON-05] 반품비 · 교환비는 생략해도 검증을 통과한다")
    void optional() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();

        assertThat(validator.validateProperty(request, "returnFee")).isEmpty();
        assertThat(validator.validateProperty(request, "exchangeFee")).isEmpty();
    }

    @Test
    @DisplayName("[ON-05] 반품비 · 교환비를 보내면 0 이상이어야 한다")
    void notNegative() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        request.setReturnFee(-1);
        request.setExchangeFee(-1);

        assertThat(validator.validateProperty(request, "returnFee")).hasSize(1);
        assertThat(validator.validateProperty(request, "exchangeFee")).hasSize(1);
    }

    @Test
    @DisplayName("[OB-04] 발송 기한을 빼면 「발송 기한을 입력해 주세요.」")
    void shippingLeadDaysRequired() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();

        assertThat(messages(validator.validateProperty(request, "shippingLeadDays")))
                .containsExactly("발송 기한을 입력해 주세요.");
    }

    @Test
    @DisplayName("[OB-05] 발송 기한 0 · 8 은 「1~7영업일」 · 1 · 7 은 통과")
    void shippingLeadDaysRange() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        for (int days : new int[]{0, 8}) {
            request.setShippingLeadDays(days);
            assertThat(messages(validator.validateProperty(request, "shippingLeadDays")))
                    .containsExactly("발송 기한은 1~7영업일로 입력해 주세요.");
        }
        for (int days : new int[]{1, 7}) {
            request.setShippingLeadDays(days);
            assertThat(validator.validateProperty(request, "shippingLeadDays")).isEmpty();
        }
    }

    @Test
    @DisplayName("[OB-06] 기본 배송비를 빼면 「기본 배송비를 입력해 주세요.」")
    void defaultDeliveryFeeRequired() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();

        assertThat(messages(validator.validateProperty(request, "defaultDeliveryFee")))
                .containsExactly("기본 배송비를 입력해 주세요.");
    }

    @Test
    @DisplayName("[OB-07] 제주 추가비 — 생략은 통과 · 음수는 「제주 추가비는 0 이상이어야 합니다.」")
    void jejuSurcharge() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        assertThat(validator.validateProperty(request, "jejuSurcharge")).isEmpty();

        request.setJejuSurcharge(-1);
        assertThat(messages(validator.validateProperty(request, "jejuSurcharge")))
                .containsExactly("제주 추가비는 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("무료배송 기준금액은 선택값이다 — 비우면 무료배송 없음")
    void freeShippingThresholdOptional() {
        assertThat(validator.validateProperty(new SellerCompleteRegistrationRequest(), "freeShippingThreshold")).isEmpty();
    }

    @Test
    @DisplayName("[OB-06] 기본 배송비 -1 은 「기본 배송비는 0 이상이어야 합니다.」 · 0 은 통과")
    void defaultDeliveryFeeNotNegative() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        request.setDefaultDeliveryFee(-1);
        assertThat(messages(validator.validateProperty(request, "defaultDeliveryFee")))
                .containsExactly("기본 배송비는 0 이상이어야 합니다.");

        request.setDefaultDeliveryFee(0);
        assertThat(validator.validateProperty(request, "defaultDeliveryFee")).isEmpty();
    }

    @Test
    @DisplayName("[OV-01] 도서산간 추가비 · 무료배송 기준금액 음수는 각자의 문구로 거부한다")
    void surchargeAndThresholdNotNegative() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        request.setRemoteAreaSurcharge(-1);
        request.setFreeShippingThreshold(-1);

        assertThat(messages(validator.validateProperty(request, "remoteAreaSurcharge")))
                .containsExactly("도서산간 추가비는 0 이상이어야 합니다.");
        assertThat(messages(validator.validateProperty(request, "freeShippingThreshold")))
                .containsExactly("무료배송 기준금액은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("[OV-03] 수취인 이름 — 한글 · 영문 · 공백만 · 이모지 · 특수문자는 거부 · 공백만이면 「입력해 주세요」")
    void recipientName() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        for (String valid : List.of("김담당", "Kim Dam", "김 Dam")) {
            request.setRecipientName(valid);
            assertThat(validator.validateProperty(request, "recipientName")).as(valid).isEmpty();
        }
        for (String invalid : List.of("김담당😀", "김담당!", "김담당1")) {
            request.setRecipientName(invalid);
            assertThat(messages(validator.validateProperty(request, "recipientName"))).as(invalid)
                    .containsExactly("수취인 이름은 한글·영문·공백만 입력할 수 있습니다.");
        }
        request.setRecipientName("   ");
        assertThat(messages(validator.validateProperty(request, "recipientName")))
                .containsExactly("수취인 이름을 입력해 주세요.");
    }

    @Test
    @DisplayName("[OV-04] 수취인 연락처 — 010/011/016~019 · 10~11자리 · 하이픈 포함만 통과(§6-3 · 담당자 연락처와 다른 규칙)")
    void recipientContact() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();
        for (String valid : List.of("010-1234-5678", "011-123-4567", "016-1234-5678", "019-123-4567")) {
            request.setContact(valid);
            assertThat(validator.validateProperty(request, "contact")).as(valid).isEmpty();
        }
        for (String invalid : List.of("012-1234-5678", "01012345678", "010-12", "010-1234-56789", "02-123-4567")) {
            request.setContact(invalid);
            assertThat(messages(validator.validateProperty(request, "contact"))).as(invalid)
                    .containsExactly("올바른 휴대폰 번호 형식으로 입력해 주세요. (예: 010-1234-5678)");
        }
        request.setContact(null);
        assertThat(messages(validator.validateProperty(request, "contact")))
                .containsExactly("수취인 연락처를 입력해 주세요.");
    }

    @Test
    @DisplayName("[OV-05] 주소 · 상세 주소를 빼면 각자의 문구")
    void addressRequired() {
        SellerCompleteRegistrationRequest request = new SellerCompleteRegistrationRequest();

        assertThat(messages(validator.validateProperty(request, "address"))).containsExactly("주소를 검색해 입력해 주세요.");
        assertThat(messages(validator.validateProperty(request, "detailAddress"))).containsExactly("상세 주소를 입력해 주세요.");
    }

    private static Set<String> messages(Set<ConstraintViolation<SellerCompleteRegistrationRequest>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }
}
