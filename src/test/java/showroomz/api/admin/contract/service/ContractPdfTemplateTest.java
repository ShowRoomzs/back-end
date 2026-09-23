package showroomz.api.admin.contract.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.type.*;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.seller.entity.Seller;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ContractPdfTemplateTest {
    static ContractPdfTemplate template() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new org.thymeleaf.spring6.SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        return new ContractPdfTemplate(engine);
    }

    static Contract fixture(int items, int fee, boolean review, boolean note, boolean secondary, boolean fixed, boolean business) {
        Seller seller = new Seller("brand@test.local", "", "김담당", "010-1234-5678", LocalDateTime.now());
        seller.setCompanyName("브랜드 회사"); seller.setRepresentativeName("김대표"); seller.setBusinessRegistrationNumber("123-45-67890");
        seller.setBusinessAddress("서울특별시 강남구"); seller.setDetailAddress("123호");
        Creator creator = Creator.builder().realName("김서명").showroomName("인플루언서 쇼룸").showroomAddress("not-a-postal-address")
                .businessType(business ? CreatorBusinessType.BUSINESS : CreatorBusinessType.INDIVIDUAL)
                .businessRegistrationNumber("987-65-43210").businessEmail("creator@test.local").instagramUrl("https://instagram.com/test").build();
        Contract c = Contract.builder().market(new Market(seller, "브랜드", "02-1234-5678")).creator(creator)
                .title("가을 공동구매").contractNumber("CTR-20260923-001").reviewRequestedAt(LocalDateTime.of(2026,9,23,10,0))
                .groupBuyStartAt(LocalDateTime.of(2026,10,1,10,0)).groupBuyEndAt(LocalDateTime.of(2026,10,14,23,59))
                .fixedFeeAmount(fee).fixedFeeTrigger(FixedFeeTrigger.GROUP_BUY_ENDED)
                .contentFeedCount(2).contentReelsCount(1).contentStoryCount(3).contentDueDate(LocalDate.of(2026,10,14))
                .brandPreReview(review).note(note ? "첫째 줄 <script>alert(1)</script>\n" + "계약 비고 ".repeat(75) : null)
                .secondaryUseAllowed(secondary).secondaryUsePeriodType(fixed ? SecondaryUsePeriodType.FIXED : SecondaryUsePeriodType.UNLIMITED)
                .secondaryUseMonths(fixed ? 12 : null).build();
        List<ContractItem> rows = new ArrayList<>();
        for (int i = 0; i < items; i++) rows.add(ContractItem.builder().productName("계약 상품 " + (i + 1)).regularPrice(32000)
                .groupBuyPrice(28010).rewardRate(new BigDecimal("15.5")).minQuantity(300).build());
        c.replaceItems(rows);
        return c;
    }

    @ParameterizedTest
    @CsvSource({"true,true,true,true,true", "false,false,false,false,false", "true,false,true,false,false", "false,true,true,false,true"})
    void rendersConditionsWithoutSamplesOrUnsafeMarkup(boolean fee, boolean preReview, boolean secondary, boolean fixed, boolean business) {
        String html = template().render(fixture(10, fee ? 1500000 : 0, preReview, true, secondary, fixed, business));
        var doc = Jsoup.parse(html);
        assertThat(doc.select("script")).isEmpty();
        assertThat(doc.text()).contains("4,341", "CTR-20260923-001", "미확정: 주소 수집 필요").doesNotContain("not-a-postal-address", "CTR-20260813-017", "글로우랩", "전 6면");
        assertThat(doc.select("tr").stream().filter(e -> e.text().contains("계약 상품")).count()).isEqualTo(10);
        assertThat(doc.select(".ack").size()).isEqualTo(fee ? 1 : 0);
        assertThat(doc.text().contains("초안을 플랫폼")).isEqualTo(preReview);
        assertThat(doc.text().contains("활용 기간이 만료된 후")).isEqualTo(secondary && fixed);
        assertThat(doc.text().contains("기간의 정함 없이 활용할 수 있다")).isEqualTo(secondary && !fixed);
        assertThat(doc.text().contains("해당 없음 (개인)")).isEqualTo(!business);
        assertThat(doc.select(".page").last().text()).contains("서 명 란");
    }

    @Test void zeroFeeAndBlankNoteRemoveCorrespondingClauses() {
        var doc = Jsoup.parse(template().render(fixture(1, 0, false, false, false, true, false)));
        assertThat(doc.text()).contains("본 계약에는 고정 지급비가 없다.", "허용하지 아니한다").doesNotContain("초안을 플랫폼", "활용 기간이 만료된 후");
        assertThat(doc.select(".ack")).isEmpty();
    }

    @Test void spellsAmountsWithoutRounding() {
        assertThat(ContractPdfTemplate.koreanAmount(0)).isEqualTo("영");
        assertThat(ContractPdfTemplate.koreanAmount(1500000)).isEqualTo("일백오십만");
        assertThat(ContractPdfTemplate.koreanAmount(10000000)).isEqualTo("일천만");
        assertThat(ContractPdfTemplate.koreanAmount(10001)).isEqualTo("일만일");
    }
}
