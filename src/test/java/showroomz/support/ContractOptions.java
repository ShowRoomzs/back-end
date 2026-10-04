package showroomz.support;

import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.entity.ContractItemOption;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;

import java.util.List;

/**
 * 계약 상품 옵션 픽스처 — 계약·공구 테스트 지원 클래스 5곳이 같은 규칙으로 옵션 행을 만든다.
 *
 * <p>계약 항목의 최소 물량은 옵션별로만 저장되므로(옵션 계획서 2-3) 「상품 하나에 물량 N」이라는 옛 픽스처는
 * 상품의 <b>첫 옵션에 전량</b>을 약속하는 옵션 행으로 옮긴다. 옵션이 둘 이상인 상품은 나머지 옵션이 0이다.
 */
public final class ContractOptions {

    private ContractOptions() {
    }

    /** 옵션 없는 상품의 기본 옵션 1행 — 셀러 상품 등록이 만드는 모양(이름 null · 대표)과 같다. */
    public static ProductVariant defaultVariant(ProductVariantRepository repository, Product product, int stock) {
        return repository.save(new ProductVariant(
                product, null, product.getRegularPrice(), product.getRegularPrice(), stock, true));
    }

    public static List<ProductVariant> variantsOf(ProductVariantRepository repository, Product product) {
        return repository.findByProductIdsOrderByVariantId(List.of(product.getProductId()));
    }

    /** 임시저장 요청의 옵션 배열 — 첫 옵션에 전량, 나머지 0. {@code minQuantity}가 null이면 전부 null. */
    public static List<ContractUpdateRequest.Option> request(List<ProductVariant> variants, Integer minQuantity) {
        return variants.stream()
                .map(variant -> new ContractUpdateRequest.Option(
                        variant.getVariantId(), quantityFor(variants, variant, minQuantity)))
                .toList();
    }

    /** 직접 적재하는 계약 항목에 옵션 행을 붙인다 — 같은 배분 규칙. */
    public static ContractItem attach(ContractItem item, List<ProductVariant> variants, Integer minQuantity) {
        item.replaceOptions(variants.stream()
                .map(variant -> ContractItemOption.snapshotOf(variant, quantityFor(variants, variant, minQuantity)))
                .toList());
        return item;
    }

    private static Integer quantityFor(List<ProductVariant> variants, ProductVariant variant, Integer minQuantity) {
        if (minQuantity == null) {
            return null;
        }
        return variant == variants.get(0) ? minQuantity : 0;
    }
}
