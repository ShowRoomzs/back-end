package showroomz.api.seller.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.type.ContractViolation;
import showroomz.domain.contract.type.ContractViolationCode;
import showroomz.domain.contract.type.ContractWarning;
import showroomz.domain.contract.type.ContractWarningCode;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 하드 H1~H8 · 경고 W1~W6 판정(설계서 2-2·2-3).
 *
 * <p>FE 달력·드롭다운이 보장하는 것도 전부 다시 본다 — 잠금은 UI의 친절이고 규칙의 집행이 아니다.
 * 특히 H5는 기준이 「검토 요청일」이라 임시저장 때 통과한 날짜가 3주 뒤 검토 요청 시점에는
 * 위반이 된다. 그래서 판정은 반드시 검토 요청 트랜잭션 안에서 한다.
 *
 * <p>정가는 <b>스냅샷이 아니라 현재 상품 정가</b>로 H1을 본다. 옛 정가를 들고 있으면
 * 브랜드가 정가를 내린 뒤에도 H1이 통과해버린다(설계서 2-2 「정가」 행).
 */
@Component
@RequiredArgsConstructor
public class ContractValidator {

    /** 체결 후 게시물 등록·오픈 승인에 필요한 리드타임(§25-5-2). 검토 SLA 미정이라 일단 이 값으로 집행한다. */
    private static final int LEAD_TIME_DAYS = 7;
    private static final int MIN_PERIOD_DAYS = 3;
    private static final int MAX_PERIOD_DAYS = 30;
    private static final int TITLE_MIN_LENGTH = 2;
    private static final int TITLE_MAX_LENGTH = 40;
    private static final int FIXED_FEE_MAX = 10_000_000;

    private static final BigDecimal WARN_DISCOUNT_RATE = BigDecimal.valueOf(70);
    private static final BigDecimal WARN_REWARD_RATE = BigDecimal.valueOf(40);
    private static final int WARN_PERIOD_DAYS = 14;
    private static final int WARN_START_AHEAD_DAYS = 60;
    private static final int WARN_ITEM_COUNT = 10;
    private static final int WARN_FIXED_FEE = 1_000_000;

    private final ConnectionRepository connectionRepository;
    private final ProductRepository productRepository;

    /** 판정 결과. 경고는 같은 코드가 여러 항목에서 나올 수 있어 발생 건마다 한 줄씩 담는다. */
    public record Result(List<ContractViolation> violations, List<ContractWarning> warnings) {

        public boolean canSubmit() {
            return violations.isEmpty();
        }

        /** 경고 대조·저장은 코드 집합으로 한다 — 같은 코드가 상품 3건에서 나와도 확인은 한 번이다. */
        public List<String> distinctWarningCodes() {
            return warnings.stream().map(ContractWarning::code).distinct().sorted().toList();
        }
    }

    public Result validate(Contract contract, Long marketId, LocalDateTime referenceAt) {
        List<ContractViolation> violations = new ArrayList<>();
        List<ContractWarning> warnings = new ArrayList<>();

        validateCounterparty(contract, marketId, violations);
        validateTitle(contract, violations);
        validatePeriod(contract, referenceAt, violations, warnings);
        validateFixedFee(contract, violations, warnings);
        validateContent(contract, violations);
        validateSecondaryUse(contract, violations);
        validateItems(contract, marketId, violations, warnings);

        return new Result(List.copyOf(violations), List.copyOf(warnings));
    }

    private void validateCounterparty(Contract contract, Long marketId, List<ContractViolation> violations) {
        if (contract.getCreator() == null) {
            violations.add(ContractViolation.of(ContractViolationCode.COUNTERPARTY_REQUIRED, "creatorId"));
            return;
        }
        // 작성 시점에 연결됨이었어도 그 사이 해제됐을 수 있다.
        boolean connected = connectionRepository
                .findConnectedPair(marketId, contract.getCreator().getId())
                .isPresent();
        if (!connected) {
            violations.add(ContractViolation.of(ContractViolationCode.COUNTERPARTY_NOT_CONNECTED, "creatorId"));
        }
    }

    private void validateTitle(Contract contract, List<ContractViolation> violations) {
        String title = contract.getTitle();
        if (title == null || title.isBlank()) {
            violations.add(ContractViolation.of(ContractViolationCode.TITLE_REQUIRED, "title"));
            return;
        }
        int length = title.trim().length();
        if (length < TITLE_MIN_LENGTH || length > TITLE_MAX_LENGTH) {
            violations.add(ContractViolation.of(ContractViolationCode.TITLE_LENGTH, "title"));
        }
    }

    private void validatePeriod(Contract contract, LocalDateTime referenceAt,
                                List<ContractViolation> violations, List<ContractWarning> warnings) {
        LocalDateTime startAt = contract.getGroupBuyStartAt();
        LocalDateTime endAt = contract.getGroupBuyEndAt();

        if (startAt == null || endAt == null) {
            violations.add(ContractViolation.of(ContractViolationCode.PERIOD_REQUIRED, "groupBuyStartAt"));
            return;
        }
        if (!endAt.isAfter(startAt)) {
            violations.add(ContractViolation.of(ContractViolationCode.PERIOD_ORDER, "groupBuyEndAt"));
            return;
        }

        Integer days = contract.periodDays();
        if (days != null && (days < MIN_PERIOD_DAYS || days > MAX_PERIOD_DAYS)) {
            violations.add(ContractViolation.of(ContractViolationCode.H4, "groupBuyEndAt"));
        }
        if (startAt.isBefore(referenceAt.plusDays(LEAD_TIME_DAYS))) {
            violations.add(ContractViolation.of(ContractViolationCode.H5, "groupBuyStartAt"));
        }

        if (days != null && days > WARN_PERIOD_DAYS) {
            warnings.add(ContractWarning.of(ContractWarningCode.W3,
                    "공구 기간이 %d일로 14일을 넘습니다.".formatted(days)));
        }
        if (startAt.isAfter(referenceAt.plusDays(WARN_START_AHEAD_DAYS))) {
            warnings.add(ContractWarning.of(ContractWarningCode.W4));
        }
    }

    private void validateFixedFee(Contract contract, List<ContractViolation> violations,
                                  List<ContractWarning> warnings) {
        Integer amount = contract.getFixedFeeAmount();

        if (amount == null) {
            violations.add(ContractViolation.of(ContractViolationCode.FIXED_FEE_REQUIRED, "fixedFeeAmount"));
        } else {
            // 상한은 시안 힌트 문구에만 있고 하드 목록에 없다(§26-6 #6) — 서버는 집행한다.
            if (amount > FIXED_FEE_MAX) {
                violations.add(ContractViolation.of(ContractViolationCode.FIXED_FEE_LIMIT, "fixedFeeAmount"));
            }
            // H8 — 0원이면 고지 블록 자체가 뜨지 않으므로 체크를 요구하지 않는다.
            if (amount > 0 && contract.getFixedFeeNoticeAgreedAt() == null) {
                violations.add(ContractViolation.of(ContractViolationCode.H8, "fixedFeeNoticeAgreed"));
            }
            if (amount > WARN_FIXED_FEE) {
                warnings.add(ContractWarning.of(ContractWarningCode.W6,
                        "고정 지급비가 %,d원으로 1,000,000원을 넘습니다.".formatted(amount)));
            }
        }

        // 시안은 금액이 0원이어도 지급 시점에 필수 표시(*)를 붙인다 — 계약서에 기재되는 항목이다.
        if (contract.getFixedFeeTrigger() == null) {
            violations.add(ContractViolation.of(ContractViolationCode.FIXED_FEE_TRIGGER_REQUIRED, "fixedFeeTrigger"));
        }
    }

    private void validateContent(Contract contract, List<ContractViolation> violations) {
        if (contract.totalContentCount() < 1) {
            violations.add(ContractViolation.of(ContractViolationCode.H6, "contentFeedCount"));
        }
        if (contract.getContentDueDate() == null) {
            violations.add(ContractViolation.of(ContractViolationCode.CONTENT_DUE_DATE_REQUIRED, "contentDueDate"));
        }
    }

    private void validateSecondaryUse(Contract contract, List<ContractViolation> violations) {
        boolean allowed = Boolean.TRUE.equals(contract.getSecondaryUseAllowed());
        if (allowed
                && contract.getSecondaryUsePeriodType() == SecondaryUsePeriodType.FIXED
                && contract.getSecondaryUseMonths() == null) {
            violations.add(ContractViolation.of(
                    ContractViolationCode.SECONDARY_USE_MONTHS_REQUIRED, "secondaryUseMonths"));
        }
    }

    private void validateItems(Contract contract, Long marketId,
                               List<ContractViolation> violations, List<ContractWarning> warnings) {
        List<ContractItem> items = contract.getItems();
        if (items.isEmpty()) {
            violations.add(ContractViolation.of(ContractViolationCode.H7, "items"));
            return;
        }
        if (items.size() > WARN_ITEM_COUNT) {
            warnings.add(ContractWarning.of(ContractWarningCode.W5,
                    "상품이 %d건으로 10건을 넘습니다.".formatted(items.size())));
        }

        Map<Long, Product> products = loadProducts(items);

        for (int index = 0; index < items.size(); index++) {
            validateItem(items.get(index), index, marketId, products, violations, warnings);
        }
    }

    private Map<Long, Product> loadProducts(List<ContractItem> items) {
        List<Long> productIds = items.stream()
                .map(ContractItem::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private void validateItem(ContractItem item, int index, Long marketId, Map<Long, Product> products,
                              List<ContractViolation> violations, List<ContractWarning> warnings) {
        String prefix = "items[%d]".formatted(index);

        Product product = item.getProductId() == null ? null : products.get(item.getProductId());
        if (product == null) {
            violations.add(ContractViolation.of(ContractViolationCode.ITEM_PRODUCT_REQUIRED, prefix + ".productId"));
        } else {
            if (product.getMarket() == null || !marketId.equals(product.getMarket().getId())) {
                violations.add(ContractViolation.of(
                        ContractViolationCode.ITEM_PRODUCT_NOT_OWNED, prefix + ".productId"));
            }
            if (product.getDisplayStatus() != ProductDisplayStatus.DISPLAY) {
                violations.add(ContractViolation.of(
                        ContractViolationCode.ITEM_PRODUCT_NOT_DISPLAYED, prefix + ".productId"));
            }
        }

        Integer groupBuyPrice = item.getGroupBuyPrice();
        if (groupBuyPrice == null) {
            violations.add(ContractViolation.of(
                    ContractViolationCode.ITEM_GROUP_BUY_PRICE_REQUIRED, prefix + ".groupBuyPrice"));
        } else {
            if (groupBuyPrice % 10 != 0) {
                violations.add(ContractViolation.of(ContractViolationCode.H2, prefix + ".groupBuyPrice"));
            }
            // 스냅샷이 아니라 현재 정가를 본다 — 그 사이 브랜드가 정가를 내렸을 수 있다.
            Integer regularPrice = product != null ? product.getRegularPrice() : item.getRegularPrice();
            if (regularPrice != null && groupBuyPrice > regularPrice) {
                violations.add(ContractViolation.of(ContractViolationCode.H1, prefix + ".groupBuyPrice"));
            }
            if (regularPrice != null && regularPrice > 0) {
                BigDecimal discountRate = discountRate(regularPrice, groupBuyPrice);
                if (discountRate.compareTo(WARN_DISCOUNT_RATE) > 0) {
                    warnings.add(ContractWarning.of(ContractWarningCode.W1,
                            "%s의 할인율이 %s%%입니다(정가 %,d원 → %,d원)".formatted(
                                    productNameOf(item, product), discountRate.toPlainString(),
                                    regularPrice, groupBuyPrice)));
                }
            }
        }

        BigDecimal rewardRate = item.getRewardRate();
        if (rewardRate == null) {
            violations.add(ContractViolation.of(
                    ContractViolationCode.ITEM_REWARD_RATE_REQUIRED, prefix + ".rewardRate"));
        } else {
            if (rewardRate.compareTo(BigDecimal.ZERO) < 0 || rewardRate.compareTo(BigDecimal.valueOf(90)) > 0) {
                violations.add(ContractViolation.of(ContractViolationCode.H3, prefix + ".rewardRate"));
            }
            if (rewardRate.compareTo(WARN_REWARD_RATE) > 0) {
                warnings.add(ContractWarning.of(ContractWarningCode.W2,
                        "%s의 리워드율이 %s%%로 40%%를 넘습니다.".formatted(
                                productNameOf(item, product), rewardRate.stripTrailingZeros().toPlainString())));
            }
        }
    }

    /** 할인율(%) — 소수점 없이 버림. 화면 문구("할인율이 72%입니다")와 W1 판정이 같은 값을 본다. */
    private BigDecimal discountRate(int regularPrice, int groupBuyPrice) {
        return BigDecimal.valueOf(regularPrice - (long) groupBuyPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(regularPrice), 0, RoundingMode.DOWN);
    }

    /** 경고 문구에는 지금 화면에 보이는 이름을 쓴다 — 상품이 사라졌으면 스냅샷 이름으로 물러선다. */
    private String productNameOf(ContractItem item, Product product) {
        if (product != null && product.getName() != null) {
            return product.getName();
        }
        return item.getProductName() == null ? "상품" : item.getProductName();
    }
}
