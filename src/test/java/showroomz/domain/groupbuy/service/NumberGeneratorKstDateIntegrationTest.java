package showroomz.domain.groupbuy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.contract.repository.ContractNumberSequenceRepository;
import showroomz.domain.contract.service.ContractNumberGenerator;
import showroomz.domain.groupbuy.repository.GroupBuyNumberSequenceRepository;
import showroomz.domain.order.repository.OrderNumberSequenceRepository;
import showroomz.domain.order.service.OrderNumberGenerator;
import showroomz.support.IntegrationTestSupport;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("[통합] 계약번호·공구번호·주문번호 — 날짜 부분과 일련번호 시퀀스 키는 한국 날짜다")
class NumberGeneratorKstDateIntegrationTest extends IntegrationTestSupport {

    @Autowired ContractNumberGenerator contractNumberGenerator;
    @Autowired GroupBuyNumberGenerator groupBuyNumberGenerator;
    @Autowired ContractNumberSequenceRepository contractSequenceRepository;
    @Autowired GroupBuyNumberSequenceRepository groupBuySequenceRepository;
    @Autowired OrderNumberGenerator orderNumberGenerator;
    @Autowired OrderNumberSequenceRepository orderSequenceRepository;

    /** 서버 벽시계({@code LocalDateTime.now()})로 읽은 시각 — JVM 시간대와 무관하게 같은 순간이다. */
    private static LocalDateTime serverClockAt(String instant) {
        return LocalDateTime.ofInstant(Instant.parse(instant), ZoneId.systemDefault());
    }

    @Test
    @DisplayName("KST 00:35 검토 요청 — CTR-20260928이고, 09:10 요청과 같은 날짜 키로 이어 붙는다")
    void contractNumberUsesKoreanDate() {
        String first = inTransaction(() -> contractNumberGenerator.generate(serverClockAt("2026-09-27T15:35:00Z")));
        String second = inTransaction(() -> contractNumberGenerator.generate(serverClockAt("2026-09-28T00:10:00Z")));

        assertThat(first).isEqualTo("CTR-20260928-001");
        assertThat(second).isEqualTo("CTR-20260928-002");
        assertThat(contractSequenceRepository.findLastSeq(LocalDate.of(2026, 9, 28))).isEqualTo(2);
        assertThat(contractSequenceRepository.findLastSeq(LocalDate.of(2026, 9, 27))).isNull();
    }

    @Test
    @DisplayName("KST 08:59 체결 — GB-20260928이고 시퀀스도 한국 날짜 키로 증가한다")
    void groupBuyNumberUsesKoreanDate() {
        String first = inTransaction(() -> groupBuyNumberGenerator.generate(serverClockAt("2026-09-27T23:59:00Z")));
        String second = inTransaction(() -> groupBuyNumberGenerator.generate(serverClockAt("2026-09-28T01:00:00Z")));

        assertThat(first).isEqualTo("GB-20260928-001");
        assertThat(second).isEqualTo("GB-20260928-002");
        assertThat(groupBuySequenceRepository.findLastSeq(LocalDate.of(2026, 9, 28))).isEqualTo(2);
        assertThat(groupBuySequenceRepository.findLastSeq(LocalDate.of(2026, 9, 27))).isNull();
    }

    @Test
    @DisplayName("KST 02:10 주문 — 20260928-000001이고 시퀀스도 한국 날짜 키로 증가한다(자체 트랜잭션으로 커밋)")
    void orderNumberUsesKoreanDate() {
        String first = orderNumberGenerator.generate(serverClockAt("2026-09-27T17:10:00Z"));
        String second = orderNumberGenerator.generate(serverClockAt("2026-09-28T03:00:00Z"));

        assertThat(first).isEqualTo("20260928-000001");
        assertThat(second).isEqualTo("20260928-000002");
        assertThat(orderSequenceRepository.findLastSeq(LocalDate.of(2026, 9, 28))).isEqualTo(2);
        assertThat(orderSequenceRepository.findLastSeq(LocalDate.of(2026, 9, 27))).isNull();
    }
}
