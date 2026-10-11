package showroomz.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import showroomz.global.config.properties.SettlementProperties;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("정산 설정 바인딩 — settlement.* (44 어드민 설계서 9-1)")
class SettlementPropertiesBindingTest {

    @Test
    @DisplayName("키가 없으면 설계서 기본값")
    void defaults() {
        SettlementProperties properties = new SettlementProperties();

        assertThat(properties.getReviewBusinessDays()).isEqualTo(3);
        assertThat(properties.getPayoutBusinessDays()).isEqualTo(3);
        assertThat(properties.getPayoutHour()).isEqualTo(10);
        assertThat(properties.getPayoutRetryLimit()).isEqualTo(3);
        assertThat(properties.getPgFeeRate()).isEqualByComparingTo("0.03");
        assertThat(properties.getPlatformFeeRate()).isEqualByComparingTo("0");
        assertThat(properties.getRewardVatRate()).isEqualByComparingTo("0.10");
        assertThat(properties.getWithholdingIncomeRate()).isEqualByComparingTo("0.03");
        assertThat(properties.getWithholdingLocalRate()).isEqualByComparingTo("0.003");
        assertThat(properties.getPayout().getMode()).isEqualTo(SettlementProperties.PayoutMode.SIMULATED);
        assertThat(properties.getAdjustment().getDeadlineBusinessDays()).isEqualTo(10);
        assertThat(properties.getAdjustment().getDeadlineTime()).isEqualTo(LocalTime.of(23, 59, 59));
        assertThat(properties.getAdjustment().getNoticeTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(properties.getAdjustment().getReasonMaxLength()).isEqualTo(1000);
        assertThat(properties.isGenerationSchedulerEnabled()).isTrue();
    }

    @Test
    @DisplayName("yml 표기(케밥 · 시각 문자열 · enum)가 그대로 붙는다")
    void bindsYamlKeys() {
        Map<String, String> source = Map.of(
                "settlement.pg-fee-rate", "0.025",
                "settlement.payout.mode", "PG",
                "settlement.platform.business-name", "SHOWROOMZ TEST",
                "settlement.adjustment.deadline-time", "18:00:00",
                "settlement.adjustment.notice-time", "09:30",
                "settlement.generation-scheduler-enabled", "false");

        SettlementProperties properties = new Binder(new MapConfigurationPropertySource(source))
                .bind("settlement", SettlementProperties.class).get();

        assertThat(properties.getPgFeeRate()).isEqualTo(new BigDecimal("0.025"));
        assertThat(properties.getPayout().getMode()).isEqualTo(SettlementProperties.PayoutMode.PG);
        assertThat(properties.getPlatform().getBusinessName()).isEqualTo("SHOWROOMZ TEST");
        assertThat(properties.getAdjustment().getDeadlineTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(properties.getAdjustment().getNoticeTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(properties.isGenerationSchedulerEnabled()).isFalse();
        assertThat(properties.getReviewBusinessDays()).isEqualTo(3);
    }
}
