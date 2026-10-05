package showroomz.domain.product.service;

import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.entity.ProductVariant;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 옵션(variant)의 화면 이름 — 「용량: 30ml」·「색상: 베이지 / 사이즈: M」. 장바구니 · 주문 항목 · 교환 옵션이 같은 형식을
 * 쓴다. 옵션 그룹이 없는 단일 옵션은 옵션명(있으면)이다.
 */
public final class VariantOptionNames {

    private VariantOptionNames() {
    }

    public static String of(ProductVariant variant) {
        List<ProductOption> options = variant.getOptions();
        if (options == null || options.isEmpty()) {
            return variant.getName();
        }
        return options.stream()
                .sorted(Comparator.comparing(option -> option.getOptionGroup() != null
                        ? option.getOptionGroup().getOptionGroupId() : 0L))
                .map(option -> {
                    String groupName = option.getOptionGroup() != null ? option.getOptionGroup().getName() : null;
                    return (groupName != null ? groupName : "옵션") + ": " + option.getName();
                })
                .collect(Collectors.joining(" / "));
    }
}
