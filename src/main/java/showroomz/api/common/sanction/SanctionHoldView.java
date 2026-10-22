package showroomz.api.common.sanction;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;

import static showroomz.domain.contract.service.ContractSanctionHoldService.isCreatorSuspended;
import static showroomz.domain.contract.service.ContractSanctionHoldService.isMarketSuspended;

/**
 * 당사자 정지로 인한 보류 보조 배지(1017 설계서 8-4 · 8-5 · ui-partner-15 S3 · S4 · R1 · R2 · ui-studio-13 S3 · S4 · R1 · R2).
 *
 * <p>상태는 바꾸지 않고 배지만 붙인다 — 해제되면 원래 단계로 이어 간다. <b>저장하지 않고 지금 상태에서 파생한다</b> — 보류 기록과 화면이
 * 어긋날 수 없다. 내 정지면 경고(내 조치가 필요하다 · 운영팀 소명), 상대 정지면 중립(내 조치 없음)이고 <b>상대의 정지 사실 · 사유는
 * 내리지 않는다</b> — 「상대 계정 사정」으로만 쓴다.
 *
 * @param kind  MINE(내 계정 이용 정지) · COUNTERPARTY(상대 계정 사정) · ADMIN(어드민 — 당사자를 밝힌다)
 * @param party 어드민만 — BRAND · CREATOR · BOTH
 */
@Schema(description = "정지 보류 보조 배지 — 상태 옆에 붙는다. 없으면 null")
public record SanctionHoldView(
        @Schema(description = "MINE · COUNTERPARTY · ADMIN", example = "MINE") String kind,
        @Schema(description = "배지 문구", example = "체결 보류 · 계정 이용 정지") String label,
        @Schema(description = "WARNING(내 정지 · 어드민) · NEUTRAL(상대 사정 · 편집 불가)", example = "WARNING") String tone,
        @Schema(description = "어드민만 — 정지 당사자 BRAND · CREATOR · BOTH", nullable = true) String party) {

    private static final String WARNING = "WARNING";
    private static final String NEUTRAL = "NEUTRAL";

    // ------------------------------------------------------------------ 계약

    /** 파트너센터 — 내 브랜드 정지 · 상대 인플루언서 정지. 작성중 · 검토 반려는 내 정지일 때 「편집 불가」. */
    public static SanctionHoldView forBrand(Contract contract) {
        ContractStatus status = contract.getStatus();
        boolean mine = isMarketSuspended(contract.getMarket());
        if (mine && (status == ContractStatus.DRAFT || status == ContractStatus.REVIEW_REJECTED)) {
            return new SanctionHoldView("MINE", "편집 불가", NEUTRAL, null);
        }
        if (!isHoldable(status)) {
            return null;
        }
        String stage = stage(status);
        if (mine) {
            return new SanctionHoldView("MINE", stage + " · 계정 이용 정지", WARNING, null);
        }
        if (isCreatorSuspended(contract.getCreator())) {
            return new SanctionHoldView("COUNTERPARTY", stage + " · 상대 계정", NEUTRAL, null);
        }
        return null;
    }

    /** 쇼룸 스튜디오 — 내 정지 · 상대 브랜드 정지. */
    public static SanctionHoldView forCreator(Contract contract) {
        if (!isHoldable(contract.getStatus())) {
            return null;
        }
        String stage = stage(contract.getStatus());
        if (isCreatorSuspended(contract.getCreator())) {
            return new SanctionHoldView("MINE", stage + " · 계정 이용 정지", WARNING, null);
        }
        if (isMarketSuspended(contract.getMarket())) {
            return new SanctionHoldView("COUNTERPARTY", stage + " · 상대 브랜드 사정", NEUTRAL, null);
        }
        return null;
    }

    /** 어드민 — 누가 정지인지 밝힌다(운영자가 검토 · 체결을 진행하지 않을 근거). */
    public static SanctionHoldView forAdmin(Contract contract) {
        if (!isHoldable(contract.getStatus())) {
            return null;
        }
        String party = party(contract.getMarket(), contract.getCreator());
        if (party == null) {
            return null;
        }
        String stage = stage(contract.getStatus());
        return new SanctionHoldView("ADMIN", stage + " · 계정 이용 정지", WARNING, party);
    }

    /** 서명 기한이 멈춰 있는가 — 서명 진행중 ∧ 보류(「정지 중 멈춤 · 해제 후 남은 기한부터」). */
    public static boolean deadlinePaused(Contract contract) {
        return contract.getStatus() == ContractStatus.SIGNING
                && (contract.isHeldBySanction()
                || isMarketSuspended(contract.getMarket()) || isCreatorSuspended(contract.getCreator()));
    }

    // ------------------------------------------------------------------ 공구

    /** 파트너센터 — 오픈 전 공구(준비중 · 준비완료)만. 진행 중 공구는 변화 없음. */
    public static SanctionHoldView forBrand(GroupBuy groupBuy) {
        if (!isBeforeOpen(groupBuy)) {
            return null;
        }
        if (isMarketSuspended(groupBuy.getMarket())) {
            return new SanctionHoldView("MINE", "승인 보류 · 계정 이용 정지", WARNING, null);
        }
        if (isCreatorSuspended(groupBuy.getCreator())) {
            return new SanctionHoldView("COUNTERPARTY", "승인 보류 · 상대 계정", NEUTRAL, null);
        }
        return null;
    }

    public static SanctionHoldView forCreator(GroupBuy groupBuy) {
        if (!isBeforeOpen(groupBuy)) {
            return null;
        }
        if (isCreatorSuspended(groupBuy.getCreator())) {
            return new SanctionHoldView("MINE", "승인 보류 · 계정 이용 정지", WARNING, null);
        }
        if (isMarketSuspended(groupBuy.getMarket())) {
            return new SanctionHoldView("COUNTERPARTY", "오픈 보류 · 상대 브랜드 사정", NEUTRAL, null);
        }
        return null;
    }

    public static SanctionHoldView forAdmin(GroupBuy groupBuy) {
        if (!isBeforeOpen(groupBuy)) {
            return null;
        }
        String party = party(groupBuy.getMarket(), groupBuy.getCreator());
        return party == null ? null : new SanctionHoldView("ADMIN", "승인 보류 · 계정 이용 정지", WARNING, party);
    }

    // ------------------------------------------------------------------

    /**
     * 보류 단계 — 검토 대기 「검토 보류」 · 서명 진행중 「체결 보류」 · 공구 생성 대기 「공구 생성 보류」(1021 설계서 1-3 —
     * 계약은 양측 서명 완료로 이미 성립했고 운영자의 공구 생성만 멈춘다).
     */
    private static String stage(ContractStatus status) {
        return switch (status) {
            case REVIEW_PENDING -> "검토 보류";
            case CONCLUSION_PENDING -> "공구 생성 보류";
            default -> "체결 보류";
        };
    }

    private static boolean isHoldable(ContractStatus status) {
        return status == ContractStatus.REVIEW_PENDING || status == ContractStatus.SIGNING
                || status == ContractStatus.CONCLUSION_PENDING;
    }

    private static boolean isBeforeOpen(GroupBuy groupBuy) {
        return groupBuy.getStatus() == GroupBuyStatus.PREPARING || groupBuy.getStatus() == GroupBuyStatus.READY;
    }

    private static String party(Market market, Creator creator) {
        boolean brand = isMarketSuspended(market);
        boolean influencer = isCreatorSuspended(creator);
        if (brand && influencer) {
            return "BOTH";
        }
        return brand ? "BRAND" : influencer ? "CREATOR" : null;
    }
}
