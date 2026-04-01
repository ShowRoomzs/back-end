package showroomz.domain.inquiry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.inquiry.entity.AnswerTemplate;

public interface AnswerTemplateRepository extends JpaRepository<AnswerTemplate, Long> {
}
