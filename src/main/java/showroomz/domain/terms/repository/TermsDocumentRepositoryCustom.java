package showroomz.domain.terms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.type.TermsType;

import java.util.Map;

public interface TermsDocumentRepositoryCustom {

    /** 어드민 목록 — 문서 1건 = 1행 (기획 §21-3) */
    Page<TermsDocument> findAdminDocumentList(TermsType type, String keyword, Pageable pageable);

    /** 유형 탭 건수 (검색어 적용 기준) */
    Map<TermsType, Long> countByTypeGroup(String keyword);

    /** 툴바의 "시행 예정 N건" — 시행중 버전이 없고 시행 예정 버전만 있는 문서 (기획 §21-3) */
    long countScheduledDocuments(TermsType type, String keyword);

    /** 툴바의 "구버전 N건" — 후속 문서로 대체된 문서 (기획 §21-3) */
    long countSupersededDocuments(TermsType type, String keyword);
}
