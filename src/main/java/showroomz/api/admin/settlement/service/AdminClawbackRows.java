package showroomz.api.admin.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.settlement.dto.AdminSettlementDto;
import showroomz.api.common.settlement.service.SettlementClawbackViews;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementClawback;
import showroomz.domain.settlement.repository.SettlementClawbackRepository;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.type.ClawbackSide;
import showroomz.domain.settlement.type.ClawbackStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 07a 차감 탭(44 어드민 설계서 7-2 · L6) — 행 = 차감 번호 1건. 측별 행(이월 seq 포함)을 한 줄로 합치고 측별 상태는 마지막 seq 의
 * 상태다(이월 중이면 PENDING). 검색은 {@code CLW-} · 원 정산번호 · 주문번호 · 브랜드명 · 쇼룸명.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminClawbackRows {

    private final SettlementClawbackRepository clawbackRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementClawbackViews clawbackViews;

    public record Rows(List<AdminSettlementDto.ClawbackItem> items, long unrecoverableCount, long unrecoverableAmount) {
    }

    public Rows build(String keyword) {
        List<SettlementClawbackViews.Row> rows = clawbackViews.rows(clawbackRepository.findAllLatestFirst());
        Map<String, List<SettlementClawbackViews.Row>> byNumber = new LinkedHashMap<>();
        rows.forEach(row -> byNumber.computeIfAbsent(row.clawback().getClawbackNumber(), k -> new ArrayList<>()).add(row));
        Map<Long, Settlement> applied = settlementRepository.findAllById(rows.stream()
                        .map(row -> row.clawback().getAppliedSettlementId()).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Settlement::getId, Function.identity()));

        List<AdminSettlementDto.ClawbackItem> items = new ArrayList<>();
        long unrecoverableCount = 0;
        long unrecoverableAmount = 0;
        for (Map.Entry<String, List<SettlementClawbackViews.Row>> entry : byNumber.entrySet()) {
            List<SettlementClawbackViews.Row> group = entry.getValue();
            SettlementClawbackViews.Row head = group.stream()
                    .min(Comparator.comparing(row -> row.clawback().getId())).orElseThrow();
            SettlementClawback brand = latest(group, ClawbackSide.BRAND);
            SettlementClawback creator = latest(group, ClawbackSide.CREATOR);
            long unrecoverable = group.stream().map(SettlementClawbackViews.Row::clawback)
                    .filter(c -> c.getStatus() == ClawbackStatus.UNRECOVERABLE).mapToLong(SettlementClawback::getAmount).sum();
            if (unrecoverable > 0) {
                unrecoverableCount++;
                unrecoverableAmount += unrecoverable;
            }
            SettlementClawback lastApplied = group.stream().map(SettlementClawbackViews.Row::clawback)
                    .filter(c -> c.getAppliedSettlementId() != null)
                    .max(Comparator.comparing(SettlementClawback::getId)).orElse(null);
            String unrecoverableReason = group.stream().map(SettlementClawbackViews.Row::clawback)
                    .filter(c -> c.getUnrecoverableReason() != null)
                    .map(c -> c.getUnrecoverableReason().getLabel()).findFirst().orElse(null);
            Settlement origin = head.origin();
            items.add(new AdminSettlementDto.ClawbackItem(entry.getKey(),
                    head.clawback().getOriginSettlementId(), origin == null ? null : origin.getSettlementNumber(),
                    head.orderNumber(), head.productName(), origin == null ? null : origin.getMarket().getMarketName(),
                    origin == null ? null : origin.getCreator().getShowroomName(), head.reasonLabel(),
                    head.clawback().getRefundAmount(), sum(group, ClawbackSide.BRAND), sum(group, ClawbackSide.CREATOR),
                    brand == null ? null : brand.getStatus().name(), brand == null ? null : brand.getStatus().getLabel(),
                    creator == null ? null : creator.getStatus().name(),
                    creator == null ? null : creator.getStatus().getLabel(),
                    lastApplied == null || applied.get(lastApplied.getAppliedSettlementId()) == null ? null
                            : applied.get(lastApplied.getAppliedSettlementId()).getSettlementNumber(),
                    unrecoverableReason, head.clawback().getCreatedAt()));
        }
        String pattern = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        if (pattern != null) {
            items = items.stream().filter(item -> matches(item, pattern)).toList();
        }
        return new Rows(items, unrecoverableCount, unrecoverableAmount);
    }

    private static boolean matches(AdminSettlementDto.ClawbackItem item, String pattern) {
        return contains(item.clawbackNumber(), pattern) || contains(item.originSettlementNumber(), pattern)
                || contains(item.orderNumber(), pattern) || contains(item.brandName(), pattern)
                || contains(item.showroomName(), pattern);
    }

    private static boolean contains(String value, String pattern) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(pattern);
    }

    private static SettlementClawback latest(List<SettlementClawbackViews.Row> group, ClawbackSide side) {
        return group.stream().map(SettlementClawbackViews.Row::clawback).filter(c -> c.getSide() == side)
                .max(Comparator.comparingInt(SettlementClawback::getSeq)).orElse(null);
    }

    private static long sum(List<SettlementClawbackViews.Row> group, ClawbackSide side) {
        return group.stream().map(SettlementClawbackViews.Row::clawback).filter(c -> c.getSide() == side)
                .mapToLong(SettlementClawback::getAmount).sum();
    }
}
