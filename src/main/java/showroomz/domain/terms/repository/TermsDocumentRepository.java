package showroomz.domain.terms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.terms.entity.TermsDocument;
import showroomz.domain.terms.type.TermsTarget;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, Long>, TermsDocumentRepositoryCustom {

    boolean existsByNameAndTarget(String name, TermsTarget target);
}
