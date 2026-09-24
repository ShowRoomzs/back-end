package showroomz.api.seller.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.support.BrandFixture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 설계서 2 — 검증은 서버가 전부 다시 본다.
 *
 * <p>세 지점이 서로 다른 것을 본다는 게 요점이다: 임시저장은 <b>형식만</b>, 검증 API는 판정만 하고
 * 상태를 안 바꾸며, 검토 요청은 <b>하드 전량 + 소유·상태·상대·상품 재확인</b>을 한 트랜잭션에서 한다.
 * 특히 H5(리드타임)·H1(정가)·상대 연결은 <b>임시저장 때 통과한 값이 검토 요청 시점에 위반</b>이 될 수
 * 있어서, 저장 때 한 판정을 재사용하면 안 된다.
 *
 * <p>각 테스트는 {@link #validForm}에서 항목 하나만 바꾼다 — 실패했을 때 어느 규칙이 깨졌는지
 * 테스트 이름만으로 읽히게 하려는 것이다.
 */
@DisplayName("[통합] 파트너센터 계약 검증")
class SellerContractValidationIntegrationTest extends SellerContractTestSupport {

    // ── 기간 · 리드타임 ────────────────────────────────────────────────────

    @Test
    @DisplayName("공구 기간이 3~30일 밖이면 H4다 — 달력이 잠가주는 것도 서버가 다시 본다")
    void rejectsPeriodOutOfRange() throws Exception {
        long contractId = createDraft();
        LocalDateTime startAt = baseStartAt();

        saveOk(contractId, validForm(0L).period(startAt, startAt.plusDays(1)));
        validate(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("H4")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H4')].field", hasItem("groupBuyEndAt")));

        saveOk(contractId, validForm(currentVersion(contractId)).period(startAt, startAt.plusDays(30)));
        validate(contractId).andExpect(jsonPath("$.hardViolations[*].code", hasItem("H4")));

        // 경계 — 3일(양끝 포함)과 30일은 통과한다.
        saveOk(contractId, validForm(currentVersion(contractId)).period(startAt, startAt.plusDays(2)));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));

        saveOk(contractId, validForm(currentVersion(contractId)).period(startAt, startAt.plusDays(29)));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));
    }

    @Test
    @DisplayName("리드타임 7일은 검토 요청 시각 기준이다 — 임시저장은 통과시키고 요청에서 막는다")
    void enforcesLeadTimeAtReviewRequest() throws Exception {
        long contractId = createDraft();
        LocalDateTime tooSoon = LocalDateTime.now().plusDays(6).withNano(0);

        // 저장 자체는 막지 않는다 — 날짜를 아직 고르는 중일 수 있다(설계서 0-3).
        saveOk(contractId, validForm(0L)
                .period(tooSoon, tooSoon.plusDays(9))
                .content(1, 1, 0, tooSoon.plusDays(12).toLocalDate()));

        reviewRequest(contractId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("H5")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H5')].kind", hasItem("RULE")));

        LocalDateTime justInTime = LocalDateTime.now().plusDays(7).plusHours(1).withNano(0);
        saveOk(contractId, validForm(currentVersion(contractId))
                .period(justInTime, justInTime.plusDays(9))
                .content(1, 1, 0, justInTime.plusDays(12).toLocalDate()));
        reviewRequest(contractId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("종료가 시작보다 앞서면 PERIOD_ORDER, 한쪽만 고르면 PERIOD_REQUIRED다")
    void rejectsReversedAndHalfFilledPeriod() throws Exception {
        long contractId = createDraft();
        LocalDateTime startAt = baseStartAt();

        saveOk(contractId, validForm(0L).period(startAt, startAt.minusDays(1)));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'PERIOD_ORDER')].kind", hasItem("RULE")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'PERIOD_ORDER')].field", hasItem("groupBuyEndAt")))
                // 순서가 뒤집힌 단계에서는 일수 규칙을 겹쳐 내리지 않는다 — 고칠 곳은 하나다.
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H4')]").isEmpty());

        saveOk(contractId, validForm(currentVersion(contractId)).period(startAt, null));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'PERIOD_REQUIRED')].kind", hasItem("REQUIRED")));
    }

    // ── 공구명 · 콘텐츠 · 2차 활용 ──────────────────────────────────────────

    @Test
    @DisplayName("공구명은 비면 REQUIRED, 짧으면 RULE이다 — 빈 폼과 잘못 쓴 폼은 화면이 다르다")
    void separatesMissingTitleFromBadTitle() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).title("   "));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'TITLE_REQUIRED')].kind", hasItem("REQUIRED")));

        saveOk(contractId, validForm(currentVersion(contractId)).title("가"));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'TITLE_LENGTH')].kind", hasItem("RULE")));

        // 40자 초과는 DTO가 먼저 막는다 — 검증 API까지 가지 않는다.
        save(contractId, validForm(currentVersion(contractId)).title("공".repeat(41)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("게시 포맷 수량 합계가 0이면 H6, 게시 완료 기한이 비면 CONTENT_DUE_DATE_REQUIRED다")
    void requiresContentAmountAndDueDate() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).content(0, 0, 0, baseStartAt().plusDays(12).toLocalDate()));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H6')].kind", hasItem("REQUIRED")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H6')].field", hasItem("contentFeedCount")));

        // 셋 다 미입력도 합계 0과 같은 취급이다 — null을 0으로 읽는다.
        saveOk(contractId, validForm(currentVersion(contractId)).content(null, null, null, null));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("H6")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'CONTENT_DUE_DATE_REQUIRED')].kind",
                        hasItem("REQUIRED")));
    }

    @Test
    @DisplayName("2차 활용을 기간 지정으로 두면 개월 수가 필수다 — 무기한이면 묻지 않는다")
    void requiresMonthsOnlyForFixedSecondaryUse() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).secondaryUse(true, SecondaryUsePeriodType.FIXED, null));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'SECONDARY_USE_MONTHS_REQUIRED')].field",
                        hasItem("secondaryUseMonths")));

        saveOk(contractId, validForm(currentVersion(contractId)).secondaryUse(true, SecondaryUsePeriodType.UNLIMITED, null));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));

        saveOk(contractId, validForm(currentVersion(contractId)).secondaryUse(false, SecondaryUsePeriodType.FIXED, null));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));
    }

    // ── 고정 지급비 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("고정 지급비가 0원보다 크면 고지 확인이 필수다(H8) — 0원이면 묻지 않는다")
    void requiresFixedFeeNoticeOnlyWhenFeeExists() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).fixedFee(500_000, FixedFeeTrigger.POST_REGISTERED, false));
        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H8')].kind", hasItem("REQUIRED")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H8')].field", hasItem("fixedFeeNoticeAgreed")));

        saveOk(contractId, validForm(currentVersion(contractId)).fixedFee(500_000, FixedFeeTrigger.POST_REGISTERED, true));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));

        // 0원이면 고지 블록 자체가 뜨지 않는다 — 체크를 요구하면 화면에 없는 항목을 요구하는 셈이다.
        saveOk(contractId, validForm(currentVersion(contractId)).fixedFee(0, FixedFeeTrigger.POST_REGISTERED, false));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));
    }

    @Test
    @DisplayName("지급 시점은 0원이어도 필수다 — 계약서에 기재되는 항목이다")
    void requiresFixedFeeTriggerEvenWithoutFee() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).fixedFee(0, null, false));
        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'FIXED_FEE_TRIGGER_REQUIRED')].kind",
                        hasItem("REQUIRED")));
    }

    @Test
    @DisplayName("고지 확인 시각은 최초 체크 때만 찍히고, 해제하면 지워진다")
    void keepsFirstNoticeAgreementTimestamp() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L)
                .fixedFee(500_000, FixedFeeTrigger.POST_REGISTERED, true));
        // 첫 응답은 DB에 기록되기 전 나노초 값을 담을 수 있다. 다시 읽은 저장값을 비교 기준으로 쓴다.
        String agreedAt = readString(detailOk(contractId), "$.fixedFee.noticeAgreedAt");
        assertThat(agreedAt).isNotNull();

        // 다시 저장해도 "언제 확인했는지"가 바뀌면 안 된다 — 기록의 요점이 그 시각이다.
        String second = saveOk(contractId, validForm(currentVersion(contractId))
                .fixedFee(700_000, FixedFeeTrigger.GROUP_BUY_ENDED, true));
        assertThat(readString(second, "$.fixedFee.noticeAgreedAt")).isEqualTo(agreedAt);

        // 체크를 풀면 확인 시각도 함께 지워진다 — 금액은 남고 동의만 사라지는 상태를 두지 않는다.
        saveOk(contractId, validForm(currentVersion(contractId)).fixedFee(700_000, FixedFeeTrigger.GROUP_BUY_ENDED, false));
        detail(contractId)
                .andExpect(jsonPath("$.fixedFee.amount").value(700_000))
                .andExpect(jsonPath("$.fixedFee.noticeAgreedAt").doesNotExist());
    }

    // ── 상품 항목 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("항목의 공구가·리워드율이 비면 행 index와 함께 REQUIRED로 내려간다")
    void requiresPriceAndRewardRatePerItem() throws Exception {
        long contractId = createDraft();

        saveOk(contractId, validForm(0L).items(
                item(serum, 28_000, "15.0", 300),
                new ContractUpdateRequest.Item(null, cream.getProductId(), null, null, null)));

        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_GROUP_BUY_PRICE_REQUIRED')].field",
                        hasItem("items[1].groupBuyPrice")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_REWARD_RATE_REQUIRED')].field",
                        hasItem("items[1].rewardRate")))
                // 첫 행은 멀쩡하다 — 행 단위로 갈라 내리지 않으면 FE가 어느 줄을 붉힐지 모른다.
                .andExpect(jsonPath("$.hardViolations[?(@.field == 'items[0].groupBuyPrice')]").isEmpty());
    }

    @Test
    @DisplayName("상품이 미진열로 바뀌어도 임시저장은 통과한다 — 막는 곳은 검토 요청이다")
    void blocksHiddenProductAtReviewRequestOnly() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validForm(0L));

        changeDisplayStatus(serum, ProductDisplayStatus.HIDDEN);

        // 작성 도중 잠깐 미진열이 된 상품 때문에 임시저장이 실패하면 안 된다.
        saveOk(contractId, validForm(currentVersion(contractId)));

        validate(contractId)
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_PRODUCT_NOT_DISPLAYED')].kind",
                        hasItem("RULE")))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'ITEM_PRODUCT_NOT_DISPLAYED')].field",
                        hasItem("items[0].productId")));

        reviewRequest(contractId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("ITEM_PRODUCT_NOT_DISPLAYED")));
    }

    @Test
    @DisplayName("정가가 내려가면 옛 스냅샷이 아니라 지금 정가로 H1을 본다")
    void revalidatesPriceAgainstCurrentRegularPrice() throws Exception {
        long contractId = createDraft();
        // 정가 32,000 기준으로는 멀쩡한 공구가다.
        saveOk(contractId, validForm(0L).items(item(serum, 28_000, "15.0", 300)));
        validate(contractId).andExpect(jsonPath("$.canSubmit").value(true));

        changeRegularPrice(serum, 20_000);

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'H1')].field", hasItem("items[0].groupBuyPrice")));

        reviewRequest(contractId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("H1")));

        // 막힌 요청은 스냅샷 갱신까지 통째로 되돌아간다 — 반쯤 갱신된 계약을 남기지 않는다.
        detail(contractId)
                .andExpect(jsonPath("$.items[0].regularPrice").value(32_000))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("임시저장은 남의 상품을 스냅샷으로 복사하지 않는다 — 소유는 저장 시점에 본다")
    void rejectsForeignProductOnSave() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "아더랩");
        Product foreign = createProduct(other, "남의 브랜드 세럼", 30_000);

        long contractId = createDraft();
        save(contractId, validForm(0L).items(item(foreign, 20_000, "10.0", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_PRODUCT_NOT_OWNED"));

        save(contractId, validForm(0L).items(
                new ContractUpdateRequest.Item(null, 999_999L, 20_000, new BigDecimal("10.0"), 100)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("리워드율은 소수점 첫째 자리까지다 — 둘째 자리는 임시저장에서 형식 위반으로 막는다")
    void rejectsRewardRateWithTooManyDecimals() throws Exception {
        long contractId = createDraft();

        save(contractId, validForm(0L).items(item(serum, 28_000, "15.55", 300)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.hardViolations[0].code").value("ITEM_REWARD_RATE_SCALE"))
                .andExpect(jsonPath("$.hardViolations[0].field").value("items[0].rewardRate"));

        // 15.50은 첫째 자리까지다 — 뒤에 붙은 0 때문에 막으면 안 된다.
        saveOk(contractId, validForm(0L).items(item(serum, 28_000, "15.50", 300)));

        // 상한 90%를 넘는 값은 DTO가 먼저 막아 H3까지 가지 않는다.
        save(contractId, validForm(currentVersion(contractId)).items(item(serum, 28_000, "90.1", 300)))
                .andExpect(status().isBadRequest());
    }

    // ── 상대 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("연결이 끊긴 상대는 검토 요청에서 걸린다 — 작성 시점의 연결을 믿지 않는다")
    void revalidatesConnectionAtReviewRequest() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validForm(0L));

        transactionTemplate.executeWithoutResult(tx -> {
            Connection connection = connectionRepository.findById(counterpartyConnection.getId()).orElseThrow();
            connection.markDisconnected();
            connectionRepository.save(connection);
        });

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(false))
                .andExpect(jsonPath("$.hardViolations[?(@.code == 'COUNTERPARTY_NOT_CONNECTED')].kind",
                        hasItem("RULE")));

        reviewRequest(contractId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hardViolations[*].code", hasItem("COUNTERPARTY_NOT_CONNECTED")));
    }

    @Test
    @DisplayName("연결되지 않은 상대는 임시저장 단계에서 이미 막힌다")
    void rejectsUnconnectedCounterpartyOnSave() throws Exception {
        Creator stranger = createCreator("모르는_쇼룸", "stranger");

        long contractId = createDraft();
        save(contractId, validForm(0L).creator(stranger.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_COUNTERPARTY_NOT_CONNECTED"));
    }

    // ── 경고 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("할인율 70%·리워드율 40% 초과는 막지 않고 W1·W2로 알린다")
    void warnsOnDeepDiscountAndHighRewardRate() throws Exception {
        long contractId = createDraft();
        // 정가 32,000 → 9,000 (할인율 71%) · 리워드율 45%
        saveOk(contractId, validForm(0L).items(item(serum, 9_000, "45.0", 300)));

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.warnings[*].code", hasItem("W1")))
                .andExpect(jsonPath("$.warnings[*].code", hasItem("W2")))
                // 문구에 상품명과 실제 값이 들어가야 모달이 무엇을 확인시키는지 읽힌다.
                .andExpect(jsonPath("$.warnings[?(@.code == 'W1')].message",
                        hasItem(containsString("수분진정 세럼 30ml"))))
                .andExpect(jsonPath("$.warnings[?(@.code == 'W1')].message", hasItem(containsString("71%"))))
                .andExpect(jsonPath("$.warnings[?(@.code == 'W2')].message", hasItem(containsString("45%"))));
    }

    @Test
    @DisplayName("공구 기간 14일·시작일 60일 초과는 W3·W4로 알린다")
    void warnsOnLongPeriodAndFarStart() throws Exception {
        long contractId = createDraft();
        LocalDateTime farStart = LocalDateTime.now().plusDays(61).withNano(0);

        saveOk(contractId, validForm(0L)
                .period(farStart, farStart.plusDays(14))
                .content(1, 1, 0, farStart.plusDays(20).toLocalDate()));

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.warnings[?(@.code == 'W3')].message", hasItem(containsString("15일"))))
                .andExpect(jsonPath("$.warnings[*].code", hasItem("W4")));
    }

    @Test
    @DisplayName("상품 10건·고정 지급비 100만원 초과는 W5·W6으로 알린다")
    void warnsOnManyItemsAndLargeFixedFee() throws Exception {
        long contractId = createDraft();

        List<ContractUpdateRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            items.add(item(serum, 28_000, "15.0", 300));
        }

        saveOk(contractId, validForm(0L)
                .items(items)
                .fixedFee(1_000_001, FixedFeeTrigger.POST_REGISTERED, true));

        validate(contractId)
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.warnings[?(@.code == 'W5')].message", hasItem(containsString("11건"))))
                .andExpect(jsonPath("$.warnings[?(@.code == 'W6')].message", hasItem(containsString("1,000,001원"))));
    }

    @Test
    @DisplayName("같은 코드가 여러 항목에서 나와도 확인은 한 번이다")
    void deduplicatesWarningCodesAcrossItems() throws Exception {
        long contractId = createDraft();
        saveOk(contractId, validForm(0L).items(
                item(serum, 28_000, "45.0", 300),
                item(cream, 20_000, "50.0", 100)));

        // 목록은 발생 건마다 한 줄이다 — 어느 상품이 걸렸는지 모달이 열거해야 한다.
        validate(contractId)
                .andExpect(jsonPath("$.warnings.length()").value(2))
                .andExpect(jsonPath("$.warnings[0].code").value("W2"))
                .andExpect(jsonPath("$.warnings[1].code").value("W2"));

        // 확인은 코드 집합으로 한다 — W2를 두 번 보내야 통과하는 구조면 FE가 중복 제거를 떠안는다.
        reviewRequest(contractId, "W2").andExpect(status().isOk());
    }

    @Test
    @DisplayName("경고는 전부 확인해야 통과하고, 확인한 코드가 계약과 이력에 남는다")
    void storesAcknowledgedWarningsWithHistory() throws Exception {
        long contractId = createDraft();
        LocalDateTime farStart = LocalDateTime.now().plusDays(20).withNano(0);

        // W3(기간 15일) + W6(고정 지급비 100만원 초과) 둘.
        saveOk(contractId, validForm(0L)
                .period(farStart, farStart.plusDays(14))
                .content(1, 1, 0, farStart.plusDays(20).toLocalDate())
                .fixedFee(1_500_000, FixedFeeTrigger.POST_REGISTERED, true));

        reviewRequest(contractId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_WARNING_MISMATCH"));
        reviewRequest(contractId, "W3").andExpect(status().isConflict());
        // 서버가 판정하지 않은 코드를 얹어 보내도 집합이 다르므로 막는다.
        reviewRequest(contractId, "W3", "W6", "W1").andExpect(status().isConflict());

        reviewRequest(contractId, "W6", "W3").andExpect(status().isOk());

        assertThat(contractRepository.findById(contractId).orElseThrow().getWarningFlags())
                .isEqualTo("W3,W6");

        ContractHistory reviewRequested = contractHistoryRepository
                .findByContractIdOrderByOccurredAtAscIdAsc(contractId).stream()
                .filter(entry -> entry.getEventType() == ContractEventType.REVIEW_REQUESTED)
                .findFirst().orElseThrow();
        assertThat(reviewRequested.getDetail()).isEqualTo("확인한 주의 항목: W3,W6");
        assertThat(reviewRequested.getActorDisplayName()).isEqualTo("글로우랩");
    }

    @Test
    @DisplayName("경고가 없으면 계약에 아무것도 남기지 않는다 — 빈 문자열을 저장하지 않는다")
    void leavesWarningFlagsNullWhenNothingWarned() throws Exception {
        long contractId = draftReadyForReview();
        reviewRequest(contractId).andExpect(status().isOk());

        assertThat(contractRepository.findById(contractId).orElseThrow().getWarningFlags()).isNull();
        assertThat(contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId).stream()
                .filter(entry -> entry.getEventType() == ContractEventType.REVIEW_REQUESTED)
                .findFirst().orElseThrow().getDetail()).isNull();
    }

    // ── 검증 API 자체 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("검증은 상태도 버전도 이력도 건드리지 않는다")
    void validateChangesNothing() throws Exception {
        long contractId = draftReadyForReview();
        long versionBefore = versionOf(detailOk(contractId));

        validate(contractId).andExpect(status().isOk()).andExpect(jsonPath("$.canSubmit").value(true));
        validate(contractId).andExpect(status().isOk());

        String detail = detailOk(contractId);
        assertThat(readString(detail, "$.status")).isEqualTo(ContractStatus.DRAFT.name());
        assertThat(versionOf(detail)).isEqualTo(versionBefore);
        assertThat(contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId))
                .extracting(ContractHistory::getEventType)
                .containsExactly(ContractEventType.CREATED);
    }
}
