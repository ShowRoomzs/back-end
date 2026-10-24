package showroomz.api.admin.changerequest.service;

import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.type.ChangeRequesterType;

/**
 * 어드민 변경 요청 상세의 요청자별 조립 전략(정합 계획서 R3-1) — 브랜드(M1 · M2) · 인플루언서(사업자 구분 변경)가 하나씩 구현한다. 서비스는
 * {@link #requesterType()}로 고른다 — 요청자 분기를 서비스 곳곳에 두지 않는다.
 */
public interface ChangeRequestDetailAssembler {

    ChangeRequesterType requesterType();

    ChangeRequestDetailParts assemble(ChangeRequest request);
}
