package showroomz.api.creator.performance.service;

import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.global.utils.Percent;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 상품 하이라이트 3장(§49-2 · 47-2) — 상품별 표 <b>아래</b>에 두는 요약이다(표가 정본).
 *
 * <p>근거 수치와 분모를 문장에 함께 적는다 — 「조회 4,280회 중 251건 · 5.9%」. 「주력 상품」 같은 평가어는 쓰지 않는다. 「전환율이 높은
 * 상품」의 분모는 상품 상세 조회(회)다 — 상품 단위는 쇼룸 방문으로 나눌 수 없어 문장이 분모를 직접 말한다(49 설계서 7-1 #3).
 * 값이 모두 0 이면 그 카드는 null 이다(근거 없는 「1위」를 만들지 않는다).
 */
public final class CreatorPerformanceHighlights {

    private CreatorPerformanceHighlights() {
    }

    public static CreatorPerformanceDto.Highlights of(List<CreatorPerformanceDto.ProductRow> rows, boolean estimated) {
        long totalViews = rows.stream().mapToLong(CreatorPerformanceDto.ProductRow::detailViews).sum();
        long totalReward = rows.stream().mapToLong(CreatorPerformanceDto.ProductRow::rewardAmount).sum();

        CreatorPerformanceDto.Highlight mostViewed = rows.stream()
                .filter(r -> r.detailViews() > 0)
                .max(Comparator.comparingLong(CreatorPerformanceDto.ProductRow::detailViews))
                .map(r -> new CreatorPerformanceDto.Highlight(r.productId(), r.productName(),
                        "상세 조회 %s회 · 전체의 %s%%".formatted(number(r.detailViews()),
                                wholePercent(r.detailViews(), totalViews))))
                .orElse(null);

        CreatorPerformanceDto.Highlight bestConversion = rows.stream()
                .filter(r -> r.detailViews() > 0 && r.orders() > 0)
                .max(Comparator.comparingDouble(r -> (double) r.orders() / r.detailViews()))
                .map(r -> new CreatorPerformanceDto.Highlight(r.productId(), r.productName(),
                        "조회 %s회 중 %s건 · %s%%".formatted(number(r.detailViews()), number(r.orders()),
                                Percent.oneDecimal(r.orders(), r.detailViews()))))
                .orElse(null);

        String rewardBase = estimated ? "판매 리워드 예정액" : "계약 기준 판매 리워드";
        CreatorPerformanceDto.Highlight topReward = rows.stream()
                .filter(r -> r.rewardAmount() > 0)
                .max(Comparator.comparingLong(CreatorPerformanceDto.ProductRow::rewardAmount))
                .map(r -> new CreatorPerformanceDto.Highlight(r.productId(), r.productName(),
                        "%s원 · %s의 %s%%".formatted(number(r.rewardAmount()), rewardBase,
                                wholePercent(r.rewardAmount(), totalReward))))
                .orElse(null);

        return new CreatorPerformanceDto.Highlights(mostViewed, bestConversion, topReward);
    }

    static String number(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(value);
    }

    private static long wholePercent(long part, long total) {
        Long percent = Percent.whole(part, total);
        return percent == null ? 0 : percent;
    }
}
