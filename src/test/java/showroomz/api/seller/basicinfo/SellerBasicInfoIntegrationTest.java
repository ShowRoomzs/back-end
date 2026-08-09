package showroomz.api.seller.basicinfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.seller.auth.refreshToken.SellerRefreshToken;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §15 파트너센터 기본정보 4개 탭 — 조회·직접 수정 통합 테스트. */
@DisplayName("[통합] 파트너센터 기본정보 관리")
class SellerBasicInfoIntegrationTest extends IntegrationTestSupport {

    private static final String BRAND_EMAIL = "brand@showroomz.test";
    private static final String BRAND_NAME = "코코브라운";

    private BrandFixture.Brand brand;
    private String token;

    @BeforeEach
    void setUpBrand() {
        brand = fixture.createBrand(BRAND_EMAIL, BRAND_NAME);
        token = sellerToken(brand.seller());
    }

    @Nested
    @DisplayName("사업자 정보 탭 (A-1)")
    class BusinessInfo {

        @Test
        @DisplayName("조회 — 사업장 주소는 상세주소까지 합친 단일 문자열이고, 심사 서류는 등록된 것만 내려간다")
        void getBusinessInfo() throws Exception {
            mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.brandName").value(BRAND_NAME))
                    .andExpect(jsonPath("$.marketName").value(BRAND_NAME))
                    .andExpect(jsonPath("$.brandStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.businessType").value("일반과세자"))
                    .andExpect(jsonPath("$.representativeName").value("김대표"))
                    .andExpect(jsonPath("$.companyName").value("주식회사 " + BRAND_NAME))
                    .andExpect(jsonPath("$.businessRegistrationNumber").value("123-45-67890"))
                    .andExpect(jsonPath("$.businessCondition").value("도소매업"))
                    .andExpect(jsonPath("$.businessAddress").value("서울특별시 강남구 테헤란로 123, 4층 401호"))
                    .andExpect(jsonPath("$.mailOrderRegNumber").value("2026-강남-01234"))
                    .andExpect(jsonPath("$.taxEmail").value("tax@" + BRAND_NAME + ".com"))
                    .andExpect(jsonPath("$.brandSiteUrl").value("https://" + BRAND_NAME + ".com"))
                    .andExpect(jsonPath("$.reviewDocuments", hasSize(3)))
                    .andExpect(jsonPath("$.reviewDocuments[0].documentType").value("BUSINESS_LICENSE"))
                    .andExpect(jsonPath("$.reviewDocuments[0].label").value("사업자등록증"))
                    .andExpect(jsonPath("$.reviewDocuments[0].extension").value("jpg"))
                    .andExpect(jsonPath("$.reviewDocuments[2].documentType").value("BANKBOOK"))
                    .andExpect(jsonPath("$.reviewDocuments[2].extension").value("pdf"))
                    // 변경 요청 이력이 없으면 배너 객체 자체가 없다(§A-5)
                    .andExpect(jsonPath("$.changeRequest").value(nullValue()));
        }

        @Test
        @DisplayName("조회 — 첨부하지 않은 서류는 목록에서 빠진다")
        void getBusinessInfoWithoutSomeDocuments() throws Exception {
            Seller seller = sellerRepository.findByEmail(BRAND_EMAIL).orElseThrow();
            seller.setMailOrderRegImageUrl(null);
            seller.setBankbookImageUrl("   ");
            sellerRepository.save(seller);

            mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reviewDocuments", hasSize(1)))
                    .andExpect(jsonPath("$.reviewDocuments[0].documentType").value("BUSINESS_LICENSE"));
        }

        @Test
        @DisplayName("직접 수정 — tax 이메일은 갱신되고, 빈 브랜드 사이트 링크는 빈 문자열이 아니라 null로 저장된다")
        void updateBusinessInfo() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/business")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("taxEmail", "newtax@brand.com", "brandSiteUrl", ""))))
                    .andExpect(status().isNoContent());

            Seller seller = sellerRepository.findByEmail(BRAND_EMAIL).orElseThrow();
            assertThat(seller.getTaxEmail()).isEqualTo("newtax@brand.com");
            assertThat(marketRepository.findById(brand.marketId()).orElseThrow().getBrandSiteUrl()).isNull();
        }

        @Test
        @DisplayName("직접 수정 — http(s)로 시작하지 않는 브랜드 사이트 링크는 400")
        void rejectMalformedBrandSiteUrl() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/business")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("taxEmail", "tax@brand.com", "brandSiteUrl", "brand.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("직접 수정 — tax 이메일은 필수다")
        void rejectBlankTaxEmail() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/business")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("taxEmail", "", "brandSiteUrl", "https://brand.com"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("정산 계좌 탭 (A-2)")
    class SettlementInfo {

        @Test
        @DisplayName("조회 — 파트너센터 응답의 계좌번호는 뒤 6자리만 노출한다")
        void getSettlementInfo() throws Exception {
            mockMvc.perform(get("/v1/seller/basic-info/settlement").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bankName").value("신한은행"))
                    .andExpect(jsonPath("$.maskedAccountNumber").value("******456789"))
                    .andExpect(jsonPath("$.accountHolder").value("주식회사 " + BRAND_NAME))
                    .andExpect(jsonPath("$.changeRequest").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("담당자·CS 탭 (A-3)")
    class ManagerInfo {

        @Test
        @DisplayName("조회 — 담당자·고객센터·반품 수취 주소를 함께 내려준다")
        void getManagerInfo() throws Exception {
            mockMvc.perform(get("/v1/seller/basic-info/manager").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.managerName").value("김담당"))
                    .andExpect(jsonPath("$.managerContact").value("010-1111-2222"))
                    .andExpect(jsonPath("$.csNumber").value("02-1234-5678"))
                    .andExpect(jsonPath("$.returnAddress.recipientName").value("김담당"))
                    .andExpect(jsonPath("$.returnAddress.address").value("서울특별시 강남구 테헤란로 123"))
                    .andExpect(jsonPath("$.returnAddress.detailAddress").value("1층 물류센터"));
        }

        @Test
        @DisplayName("일괄 저장 — 7개 필드가 SELLER·MARKET 양쪽에 반영된다")
        void updateManagerInfo() throws Exception {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("managerName", "박담당");
            request.put("managerContact", "010-3333-4444");
            request.put("csNumber", "1588-0000");
            request.put("recipientName", "최수취");
            request.put("recipientContact", "016-333-4444");
            request.put("address", "부산광역시 해운대구 센텀중앙로 90");
            request.put("detailAddress", "10층 1002호");

            mockMvc.perform(put("/v1/seller/basic-info/manager")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isNoContent());

            Seller seller = sellerRepository.findByEmail(BRAND_EMAIL).orElseThrow();
            assertThat(seller.getName()).isEqualTo("박담당");
            assertThat(seller.getPhoneNumber()).isEqualTo("010-3333-4444");

            var market = marketRepository.findById(brand.marketId()).orElseThrow();
            assertThat(market.getCsNumber()).isEqualTo("1588-0000");
            assertThat(market.getShippingRecipientName()).isEqualTo("최수취");
            assertThat(market.getShippingContact()).isEqualTo("016-333-4444");
            assertThat(market.getShippingAddress()).isEqualTo("부산광역시 해운대구 센텀중앙로 90");
            assertThat(market.getShippingDetailAddress()).isEqualTo("10층 1002호");
        }

        @Test
        @DisplayName("일괄 저장 — 담당자 연락처는 010 11자리만 허용한다")
        void rejectNonMobileManagerContact() throws Exception {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("managerName", "박담당");
            request.put("managerContact", "016-333-4444");
            request.put("csNumber", "1588-0000");
            request.put("recipientName", "최수취");
            request.put("recipientContact", "010-3333-4444");
            request.put("address", "부산광역시 해운대구 센텀중앙로 90");
            request.put("detailAddress", "10층 1002호");

            mockMvc.perform(put("/v1/seller/basic-info/manager")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest());

            // 검증 실패 시 다른 필드도 저장되지 않아야 한다(일괄 저장)
            assertThat(sellerRepository.findByEmail(BRAND_EMAIL).orElseThrow().getName()).isEqualTo("김담당");
        }
    }

    @Nested
    @DisplayName("계정 탭 (A-4) — 비밀번호")
    class Password {

        @Test
        @DisplayName("변경 성공 — 새 비밀번호로 해시가 교체된다")
        void changePassword() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/account/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "currentPassword", RAW_PASSWORD,
                                    "newPassword", "NewPassw0rd!",
                                    "newPasswordConfirm", "NewPassw0rd!"))))
                    .andExpect(status().isNoContent());

            String encoded = sellerRepository.findByEmail(BRAND_EMAIL).orElseThrow().getPassword();
            assertThat(passwordEncoder.matches("NewPassw0rd!", encoded)).isTrue();
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 401")
        void rejectWrongCurrentPassword() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/account/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "currentPassword", "Wrongpass0!",
                                    "newPassword", "NewPassw0rd!",
                                    "newPasswordConfirm", "NewPassw0rd!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

            assertThat(passwordEncoder.matches(RAW_PASSWORD,
                    sellerRepository.findByEmail(BRAND_EMAIL).orElseThrow().getPassword())).isTrue();
        }

        @Test
        @DisplayName("새 비밀번호 확인이 다르면 400")
        void rejectConfirmMismatch() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/account/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "currentPassword", RAW_PASSWORD,
                                    "newPassword", "NewPassw0rd!",
                                    "newPasswordConfirm", "OtherPassw0rd!"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NEW_PASSWORD_CONFIRM_MISMATCH"));
        }

        @Test
        @DisplayName("영문·숫자·특수문자 8~16자 조합이 아니면 400")
        void rejectWeakNewPassword() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/account/password")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "currentPassword", RAW_PASSWORD,
                                    "newPassword", "onlyletters",
                                    "newPasswordConfirm", "onlyletters"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("계정 탭 (A-4) — 로그인 이메일 월 1회 변경")
    class LoginEmail {

        private static final String NEW_EMAIL = "brand-new@showroomz.test";

        @Test
        @DisplayName("조회 — 변경 이력이 없으면 즉시 변경 가능하다")
        void getAccountInfo() throws Exception {
            mockMvc.perform(get("/v1/seller/basic-info/account").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginEmail").value(BRAND_EMAIL))
                    .andExpect(jsonPath("$.emailChangeable").value(true))
                    .andExpect(jsonPath("$.lastEmailChangedAt").value(nullValue()))
                    .andExpect(jsonPath("$.nextEmailChangeableAt").value(nullValue()));
        }

        @Test
        @DisplayName("변경 성공 — 이메일 교체·구 이메일 통지·리프레시 토큰 정리가 한 번에 일어난다")
        void changeEmail() throws Exception {
            sellerRefreshTokenRepository.save(new SellerRefreshToken(BRAND_EMAIL, "stale-refresh-token"));

            mockMvc.perform(patch("/v1/seller/basic-info/account/email")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("currentPassword", RAW_PASSWORD, "newEmail", NEW_EMAIL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginEmail").value(NEW_EMAIL))
                    .andExpect(jsonPath("$.emailChangeable").value(false))
                    .andExpect(jsonPath("$.lastEmailChangedAt").value(notNullValue()))
                    .andExpect(jsonPath("$.nextEmailChangeableAt").value(notNullValue()));

            Seller seller = sellerRepository.findById(brand.seller().getId()).orElseThrow();
            assertThat(seller.getEmail()).isEqualTo(NEW_EMAIL);
            assertThat(seller.getEmailChangedAt()).isNotNull();

            // 이메일이 리프레시 토큰 키라서, 남겨두면 구 키로 갱신이 조용히 깨진다
            assertThat(sellerRefreshTokenRepository.findByAdminEmail(BRAND_EMAIL)).isNull();
            verify(mailService).sendLoginEmailChangedNotice(eq(BRAND_EMAIL), eq(NEW_EMAIL), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("직전 변경으로부터 1개월이 지나지 않으면 400 — 29일 경과는 아직 막힌다")
        void rejectSecondChangeWithinOneMonth() throws Exception {
            fixture.backdateEmailChangedAt(brand.seller().getId(), LocalDateTime.now().minusDays(29));

            mockMvc.perform(patch("/v1/seller/basic-info/account/email")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("currentPassword", RAW_PASSWORD, "newEmail", NEW_EMAIL))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_LIMIT_EXCEEDED"));

            assertThat(sellerRepository.findById(brand.seller().getId()).orElseThrow().getEmail()).isEqualTo(BRAND_EMAIL);
            verify(mailService, never()).sendLoginEmailChangedNotice(any(), any(), any());
        }

        @Test
        @DisplayName("1개월 롤링이 지나면 다시 변경할 수 있다 — 1개월 + 1일 경과")
        void allowChangeAfterOneMonth() throws Exception {
            fixture.backdateEmailChangedAt(brand.seller().getId(), LocalDateTime.now().minusMonths(1).minusDays(1));

            mockMvc.perform(patch("/v1/seller/basic-info/account/email")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("currentPassword", RAW_PASSWORD, "newEmail", NEW_EMAIL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginEmail").value(NEW_EMAIL));
        }

        @Test
        @DisplayName("다른 브랜드가 쓰는 이메일로는 변경할 수 없다")
        void rejectDuplicateEmail() throws Exception {
            fixture.createBrand("other@showroomz.test", "오하브라운");

            mockMvc.perform(patch("/v1/seller/basic-info/account/email")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("currentPassword", RAW_PASSWORD,
                                    "newEmail", "other@showroomz.test"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        }

        @Test
        @DisplayName("현재 이메일과 같은 값은 변경으로 취급하지 않는다")
        void rejectSameEmail() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/account/email")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("currentPassword", RAW_PASSWORD, "newEmail", BRAND_EMAIL))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("비밀번호가 틀리면 이메일도 바뀌지 않는다")
        void rejectWrongPassword() throws Exception {
            mockMvc.perform(patch("/v1/seller/basic-info/account/email")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of("currentPassword", "Wrongpass0!", "newEmail", NEW_EMAIL))))
                    .andExpect(status().isUnauthorized());

            assertThat(sellerRepository.findById(brand.seller().getId()).orElseThrow().getEmail()).isEqualTo(BRAND_EMAIL);
        }
    }

    @Nested
    @DisplayName("접근 권한")
    class Authorization {

        @Test
        @DisplayName("토큰이 없으면 401")
        void rejectAnonymous() throws Exception {
            mockMvc.perform(get("/v1/seller/basic-info/business"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("어드민 토큰으로는 파트너센터 API에 접근할 수 없다")
        void rejectAdminToken() throws Exception {
            Seller admin = fixture.createAdmin("ops@showroomz.test", "운영자");

            mockMvc.perform(get("/v1/seller/basic-info/business").header(HttpHeaders.AUTHORIZATION, adminToken(admin)))
                    .andExpect(status().isForbidden());
        }
    }
}
