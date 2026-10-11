package showroomz.global.payment.settlement.portone;

import io.portone.sdk.server.common.Bank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 표준 은행 코드 ↔ 포트원 Bank(포트원 설계서 4-5) — 시드(V23)의 전 코드가 매핑되고 되돌릴 수 있다. */
@DisplayName("포트원 은행 매핑")
class PortOneBanksTest {

    /** V23__add_bank.sql 의 전 코드. */
    private static final List<String> SEED_CODES = List.of("090", "092", "004", "088", "020", "081", "011", "089", "003",
            "023", "071", "045", "048", "007", "027", "012", "064", "050", "031", "032", "039", "034", "037", "035", "261",
            "267", "287", "238", "290", "240", "291", "278", "209", "280", "264", "271", "270", "262", "243", "269", "263",
            "279", "218", "227", "292", "247", "266");

    @Test
    @DisplayName("시드의 전 은행 코드가 포트원 Bank 로 매핑된다")
    void allSeedCodesMapped() {
        for (String code : SEED_CODES) {
            assertThat(PortOneBanks.byCode(code)).as(code).isPresent();
        }
    }

    @Test
    @DisplayName("대표 매핑 — 국민 KOOKMIN · 신한 SHINHAN · 카카오뱅크 KAKAO · 토스뱅크 TOSS · 지역농축협 LOCAL_NONGHYUP")
    void representativeValues() {
        assertThat(PortOneBanks.byCode("004")).map(Bank::getValue).contains("KOOKMIN");
        assertThat(PortOneBanks.byCode("088")).map(Bank::getValue).contains("SHINHAN");
        assertThat(PortOneBanks.byCode("090")).map(Bank::getValue).contains("KAKAO");
        assertThat(PortOneBanks.byCode("092")).map(Bank::getValue).contains("TOSS");
        assertThat(PortOneBanks.byCode("012")).map(Bank::getValue).contains("LOCAL_NONGHYUP");
    }

    @Test
    @DisplayName("모르는 코드 · null · 공백은 비어 있고, 되돌리기(Bank → 코드)는 같은 코드를 준다")
    void unknownAndReverse() {
        assertThat(PortOneBanks.byCode("999")).isEmpty();
        assertThat(PortOneBanks.byCode(null)).isEmpty();
        assertThat(PortOneBanks.byCode(" 004 ")).isPresent();
        assertThat(PortOneBanks.codeOf(Bank.Shinhan.INSTANCE)).contains("088");
        assertThat(PortOneBanks.codeOf(null)).isEmpty();
    }
}
