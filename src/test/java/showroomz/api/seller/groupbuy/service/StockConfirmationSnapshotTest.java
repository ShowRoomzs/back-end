package showroomz.api.seller.groupbuy.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.entity.ContractItemOption;
import showroomz.domain.groupbuy.entity.GroupBuy;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** [확보 완료] 이력의 최소 물량 스냅샷 — 옵션별 내역까지 남긴다(옵션 계획서 4절). */
class StockConfirmationSnapshotTest {

    @Test
    @DisplayName("옵션이 여럿인 상품은 합계 뒤에 옵션별 내역을 괄호로 붙이고, 옵션 없는 상품은 합계만 남긴다")
    void includesOptionBreakdownOnlyForNamedOptions() {
        ContractItem serum = item("수분진정 세럼 30ml");
        serum.replaceOptions(List.of(option("단품", 200), option("2개 세트", 100)));
        ContractItem cream = item("수분진정 크림 50ml");
        cream.replaceOptions(List.of(option(null, 150)));
        ContractItem unsaved = item("옵션 미입력 토너");
        unsaved.replaceOptions(List.of(option("단품", null), option("세트", 40)));

        Contract contract = Contract.builder().items(new ArrayList<>()).build();
        contract.replaceItems(List.of(serum, cream, unsaved));
        GroupBuy groupBuy = GroupBuy.builder().contract(contract).build();

        assertThat(SellerGroupBuyCommandService.minQuantitySnapshot(groupBuy))
                .isEqualTo("수분진정 세럼 30ml 300개(단품 200 · 2개 세트 100)"
                        + " · 수분진정 크림 50ml 150개"
                        // 합계는 모르면 0으로, 옵션은 비어 있어도 0으로 적는다 — 무엇을 확인했는지 빠짐없이 남긴다.
                        + " · 옵션 미입력 토너 0개(단품 0 · 세트 40)");
    }

    private static ContractItem item(String name) {
        return ContractItem.builder().productName(name).sortOrder(0).build();
    }

    private static ContractItemOption option(String name, Integer minQuantity) {
        return ContractItemOption.builder().variantName(name).minQuantity(minQuantity).sortOrder(0).build();
    }
}
