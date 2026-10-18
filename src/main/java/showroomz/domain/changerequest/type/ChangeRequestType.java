package showroomz.domain.changerequest.type;

import showroomz.domain.member.creator.type.CreatorBusinessType;

/**
 * 변경 요청 유형 — 브랜드 M1(사업자 정보) · M2(정산 계좌)(§15-6) + 인플루언서 사업자 구분 변경 양방향(§16-2a · 16 설계서 1-4).
 * 인플루언서 유형은 방향을 담는다 — 받는 값 · 증빙 · 대조 기준 · 승인 반영이 방향마다 다르다.
 */
public enum ChangeRequestType {
    BUSINESS_INFO(ChangeRequesterType.BRAND, null),
    SETTLEMENT_ACCOUNT(ChangeRequesterType.BRAND, null),
    /** 비사업자 → 개인사업자(사업자 전환). */
    TO_BUSINESS(ChangeRequesterType.CREATOR, CreatorBusinessType.BUSINESS),
    /** 개인사업자 → 비사업자(비사업자 전환). */
    TO_INDIVIDUAL(ChangeRequesterType.CREATOR, CreatorBusinessType.INDIVIDUAL);

    private final ChangeRequesterType requesterType;
    private final CreatorBusinessType targetBusinessType;

    ChangeRequestType(ChangeRequesterType requesterType, CreatorBusinessType targetBusinessType) {
        this.requesterType = requesterType;
        this.targetBusinessType = targetBusinessType;
    }

    public ChangeRequesterType requesterType() {
        return requesterType;
    }

    public boolean isCreatorType() {
        return requesterType == ChangeRequesterType.CREATOR;
    }

    /** 승인 후 사업자 구분 — 브랜드 유형은 null. */
    public CreatorBusinessType targetBusinessType() {
        return targetBusinessType;
    }

    /** 요청 시점의 사업자 구분(= 목표의 반대) — 브랜드 유형은 null. */
    public CreatorBusinessType sourceBusinessType() {
        if (targetBusinessType == null) {
            return null;
        }
        return targetBusinessType == CreatorBusinessType.BUSINESS ? CreatorBusinessType.INDIVIDUAL : CreatorBusinessType.BUSINESS;
    }

    /** 「비사업자 → 개인사업자」 — 처리 이력 · 어드민 상세의 방향 표기. 브랜드 유형은 null. */
    public String directionLabel() {
        if (targetBusinessType == null) {
            return null;
        }
        return sourceBusinessType().getDescription() + " → " + targetBusinessType.getDescription();
    }

    /** 인플루언서 사업자 구분 변경 — 목표 구분으로 유형을 정한다. */
    public static ChangeRequestType toward(CreatorBusinessType target) {
        return target == CreatorBusinessType.BUSINESS ? TO_BUSINESS : TO_INDIVIDUAL;
    }
}
