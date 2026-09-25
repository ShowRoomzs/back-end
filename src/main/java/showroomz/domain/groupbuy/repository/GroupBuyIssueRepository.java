package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;

import java.util.Optional;

public interface GroupBuyIssueRepository extends JpaRepository<GroupBuyIssue, Long> {

    Optional<GroupBuyIssue> findFirstByGroupBuyIdAndStatus(Long groupBuyId, GroupBuyIssueStatus status);

    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, GroupBuyIssueStatus status);
}
