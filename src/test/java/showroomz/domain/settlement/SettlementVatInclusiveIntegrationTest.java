package showroomz.domain.settlement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.SettlementCalculator;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.config.properties.SettlementProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리워드 = 부가세 포함(대표 결정 2026-11-10 · 수정요청 A-5 · 세무 확인 필요) — 운영 기본값. 테스트 공통 설정은 기존 시나리오 숫자 때문에
 * 끄고 있어(IntegrationTest) 여기서 켠다. 정산 행 스냅샷 · 산식 · 세금계산서 공급가 · 부가세 분리를 본다.
 */
@DisplayName("정산 — 리워드 부가세 포함(2026-11-10)")
class SettlementVatInclusiveIntegrationTest extends SettlementTestSupport {

    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 10, 2, 15, 0);
    private static final BigDecimal VAT = new BigDecimal("0.10");

    @Autowired private SettlementProperties settlementProperties;
    @Autowired private SettlementTaxDocumentRepository documentRepository;

    @BeforeEach
    void inclusiveOn() {
        settlementProperties.setRewardVatInclusive(true);
    }

    @AfterEach
    void inclusiveOff() {
        settlementProperties.setRewardVatInclusive(false);
    }

    @Test
    @DisplayName("사업자 — 스냅샷 true · 브랜드 별도 부가세 0 · 지급액 = 리워드 · 브랜드 · 인플루언서 세금계산서는 리워드를 공급가 + 부가세로 나눈다")
    void businessCreator() {
        makeCreatorBusiness();
        confirmedGroup(creamVariant, 2);
        confirmedGroup(serumVariant, 1);
        endGroupBuy();

        Settlement s = generate(GENERATED_AT);
        long reward = s.getRewardAmount();
        long vat = SettlementCalculator.includedVat(reward, VAT);

        assertThat(s.isRewardVatInclusive()).isTrue();
        assertThat(s.getRewardVatAmount()).isZero();
        assertThat(s.getCreatorPayoutAmount()).isEqualTo(reward);
        assertThat(s.getCreatorVatAmount()).isEqualTo(vat);
        assertThat(s.getWithholdingAmount()).isZero();
        assertThat(s.getBrandPayoutAmount()).isEqualTo(s.getConfirmedSalesAmount() - s.getPgFeeAmount()
                - s.getPlatformFeeAmount() - reward + s.getConsumerDeliveryFeeAmount());
        assertThat(s.getBrandPayoutAmount() + s.getCreatorPayoutAmount() + s.getWithholdingAmount()
                + s.getPlatformShareAmount() + s.getPgFeeAmount())
                .isEqualTo(s.getConfirmedSalesAmount() + s.getConsumerDeliveryFeeAmount());

        Settlement confirmed = confirm(s);
        SettlementTaxDocument brand = documentRepository
                .findBySettlementIdAndTypeAndClawbackIdIsNull(confirmed.getId(), TaxDocumentType.BRAND_TAX_INVOICE)
                .orElseThrow();
        assertThat(brand.getSupplyAmount()).isEqualTo(reward - vat);
        assertThat(brand.getVatAmount()).isEqualTo(vat);
        SettlementTaxDocument creatorInvoice = documentRepository
                .findBySettlementIdAndTypeAndClawbackIdIsNull(confirmed.getId(), TaxDocumentType.CREATOR_TAX_INVOICE)
                .orElseThrow();
        assertThat(creatorInvoice.getSupplyAmount()).isEqualTo(reward - vat);
        assertThat(creatorInvoice.getVatAmount()).isEqualTo(vat);
    }

    @Test
    @DisplayName("비사업자 — 원천징수 3.3% 는 리워드 기준 · 브랜드 별도 부가세 0 · 설정을 끄면(기존 정산) 별도 부담 산식")
    void individualCreator() {
        confirmedGroup(creamVariant, 1);
        endGroupBuy();

        Settlement s = generate(GENERATED_AT);
        long reward = s.getRewardAmount();
        assertThat(s.isRewardVatInclusive()).isTrue();
        assertThat(s.getRewardVatAmount()).isZero();
        assertThat(s.getWithholdingAmount()).isEqualTo(reward * 3 / 100 + reward * 3 / 1000);
        assertThat(s.getCreatorPayoutAmount()).isEqualTo(reward - s.getWithholdingAmount());
        assertThat(s.getPlatformShareAmount()).isEqualTo(s.getPlatformFeeAmount());
    }
}
