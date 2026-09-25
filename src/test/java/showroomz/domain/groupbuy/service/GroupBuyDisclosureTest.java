package showroomz.domain.groupbuy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("대가관계 표시 — 조사는 브랜드명 받침으로 고른다(31 설계 2-8)")
class GroupBuyDisclosureTest {

    @Test
    @DisplayName("받침 있음 → 으로부터 · 받침 없음·ㄹ받침 → 로부터 · 한글로 끝나지 않으면 (으)로부터")
    void particle() {
        assertThat(GroupBuyDisclosure.fromParticle("글로우랩")).isEqualTo("으로부터");
        assertThat(GroupBuyDisclosure.fromParticle("○○ 브랜드")).isEqualTo("로부터");
        assertThat(GroupBuyDisclosure.fromParticle("스킨필")).isEqualTo("로부터");
        assertThat(GroupBuyDisclosure.fromParticle("Glow Lab")).isEqualTo("(으)로부터");
    }

    @Test
    @DisplayName("문구 전체")
    void text() {
        assertThat(GroupBuyDisclosure.text("○○ 브랜드"))
                .isEqualTo("유료 광고 포함 · ○○ 브랜드로부터 대가를 받아 진행하는 공동구매입니다");
    }
}
