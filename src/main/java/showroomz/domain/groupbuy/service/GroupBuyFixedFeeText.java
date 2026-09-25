package showroomz.domain.groupbuy.service;

import showroomz.domain.contract.entity.Contract;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 고정 지급비 표준 표기 — 3서피스 문자 단위 동일(§29-9). FE 3개가 각자 조립하면 한 곳의 가운뎃점·띄어쓰기가
 * 반드시 어긋나므로 이 문자열 하나는 예외적으로 서버가 짓는다. 파트너·스튜디오가 이 메서드 하나를 쓴다.
 */
public final class GroupBuyFixedFeeText {

    private GroupBuyFixedFeeText() {
    }

    /** 「고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급」 — 고정 지급비가 없으면 null. */
    public static String of(Contract contract) {
        if (!contract.hasFixedFee() || contract.getFixedFeeTrigger() == null) {
            return null;
        }
        return "고정 지급비 %s원 · 지급 시점: %s · 브랜드 직접 지급".formatted(
                NumberFormat.getNumberInstance(Locale.KOREA).format(contract.getFixedFeeAmount()),
                contract.getFixedFeeTrigger().getLabel());
    }
}
