package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import showroomz.domain.contract.entity.*;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.global.utils.RewardCalculator;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ContractPdfTemplate {
    private final TemplateEngine templates;

    public String render(Contract c) {
        Map<String, String> values = new HashMap<>();
        var seller = c.getMarket().getSeller();
        var creator = c.getCreator();
        boolean business = creator.getBusinessType() == CreatorBusinessType.BUSINESS;
        put(values, "계약번호", c.getContractNumber());
        put(values, "제출본일시", format(c.getReviewRequestedAt()));
        put(values, "서명기한", "전자서명 솔루션에 설정된 기한");
        put(values, "브랜드_상호", seller.getCompanyName());
        put(values, "브랜드_대표자", seller.getRepresentativeName());
        put(values, "브랜드_사업자등록번호", seller.getBusinessRegistrationNumber());
        put(values, "브랜드_주소", text(seller.getBusinessAddress()) + " " + text(seller.getDetailAddress()));
        put(values, "브랜드_담당자명", seller.getName());
        put(values, "브랜드_연락처", seller.getPhoneNumber());
        put(values, "브랜드_이메일", seller.getEmail());
        put(values, "인플루언서_성명", creator.getRealName());
        put(values, "인플루언서_활동명", creator.getShowroomName());
        put(values, "인플루언서_사업자등록번호", business ? creator.getBusinessRegistrationNumber() : "해당 없음 (개인)");
        put(values, "인플루언서_주소", "[미확정: 주소 수집 필요]");
        put(values, "인플루언서_연락처", creator.getPhoneNumber());
        put(values, "인플루언서_이메일", creator.getBusinessEmail());
        put(values, "인플루언서_인스타그램", creator.getInstagramUrl());
        put(values, "공구명", c.getTitle());
        put(values, "공구_시작일시", format(c.getGroupBuyStartAt()));
        put(values, "공구_종료일시", format(c.getGroupBuyEndAt()));
        put(values, "상품수", c.getItems().size());
        put(values, "고정지급비_금액", number(c.getFixedFeeAmount()));
        put(values, "고정지급비_금액_한글", koreanAmount(c.getFixedFeeAmount() == null ? 0 : c.getFixedFeeAmount()));
        put(values, "고정지급비_지급시점", c.getFixedFeeTrigger() == null ? "" : c.getFixedFeeTrigger().getLabel());
        put(values, "게시_피드수", c.getContentFeedCount());
        put(values, "게시_릴스수", c.getContentReelsCount());
        put(values, "게시_스토리수", c.getContentStoryCount());
        put(values, "게시_합계", c.totalContentCount());
        put(values, "게시완료기한", c.getContentDueDate() == null ? "" : c.getContentDueDate().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")));
        put(values, "브랜드사전검수", Boolean.TRUE.equals(c.getBrandPreReview()) ? "있음" : "없음");
        put(values, "2차활용권", Boolean.TRUE.equals(c.getSecondaryUseAllowed()) ? "허용한다" : "허용하지 아니한다");
        put(values, "2차활용기간", c.getSecondaryUsePeriodType() == SecondaryUsePeriodType.FIXED ? c.getSecondaryUseMonths() + "개월" : "기간의 정함 없이");
        put(values, "비고", c.getNote());
        put(values, "플랫폼수수료율", 2);
        put(values, "PG수수료_문구", "[자문대기-PG] 결제대행사가 정한 요율");
        put(values, "원천징수_문구", business ? "을이 사업자이므로 세금계산서 발행 후 공제 없이 지급한다."
                : "[자문대기-세무] 을이 사업자가 아니므로 소득세법에 따라 3.3%를 공제한 후 지급한다.");
        put(values, "관할법원", "갑의 주소지를 관할하는 법원");
        Context context = new Context(Locale.KOREAN);
        context.setVariable("values", values);
        context.setVariable("items", c.getItems().stream().map(this::item).toList());
        context.setVariable("hasFixedFee", c.hasFixedFee());
        context.setVariable("preReview", Boolean.TRUE.equals(c.getBrandPreReview()));
        boolean hasNote = c.getNote() != null && !c.getNote().isBlank();
        context.setVariable("hasNote", hasNote);
        context.setVariable("secondaryAllowed", Boolean.TRUE.equals(c.getSecondaryUseAllowed()));
        context.setVariable("secondaryFixed", c.getSecondaryUsePeriodType() == SecondaryUsePeriodType.FIXED);
        context.setVariable("secondaryPeriodClause", c.getSecondaryUsePeriodType() == SecondaryUsePeriodType.FIXED
                ? "활용 기간은 게시일부터 " + c.getSecondaryUseMonths() + "개월로 한다." : "기간의 정함 없이 활용할 수 있다.");
        context.setVariable("secondaryScopeClause", (hasNote ? "활용 범위는 제8조 제" + (Boolean.TRUE.equals(c.getBrandPreReview()) ? 5 : 4)
                + "항의 비고에 기재된 범위로 한다. 비고에 범위가 기재되지 아니한 경우 " : "활용 범위는 ")
                + "갑의 자사 운영 채널로 한정하며, 유상 광고 소재로의 사용은 을의 별도 동의를 받아야 한다.");
        return templates.process("contracts/draft", context);
    }

    private Map<String, String> item(ContractItem i) {
        return Map.of("상품명", text(i.getProductName()), "정가", number(i.getRegularPrice()),
                "공구가", number(i.getGroupBuyPrice()), "리워드율", text(i.getRewardRate()),
                "개당리워드", number(RewardCalculator.calcUnitReward(i.getGroupBuyPrice(), i.getRewardRate())),
                "최소물량", number(i.getMinQuantity()));
    }
    private void put(Map<String, String> values, String key, Object value) { values.put(key, text(value)); }
    private String text(Object value) { return value == null ? "" : value.toString(); }
    private String number(Number value) { return value == null ? "0" : NumberFormat.getIntegerInstance(Locale.KOREA).format(value); }
    private String format(LocalDateTime time) { return time == null ? "" : time.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm")); }

    static String koreanAmount(long amount) {
        if (amount == 0) return "영";
        String[] digits = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
        String[] units = {"", "십", "백", "천"};
        String[] groups = {"", "만", "억", "조", "경"};
        StringBuilder result = new StringBuilder();
        for (int group = 0; amount > 0; group++, amount /= 10000) {
            int part = (int) (amount % 10000);
            if (part == 0) continue;
            StringBuilder text = new StringBuilder();
            for (int pos = 0; part > 0; pos++, part /= 10) if (part % 10 != 0) text.insert(0, digits[part % 10] + units[pos]);
            result.insert(0, text + groups[group]);
        }
        return result.toString();
    }
}
