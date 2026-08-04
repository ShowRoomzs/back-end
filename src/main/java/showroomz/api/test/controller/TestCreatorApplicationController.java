package showroomz.api.test.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.test.docs.TestCreatorApplicationControllerDocs;
import showroomz.domain.history.repository.CreatorApplicationHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Map;

/**
 * 테스트 전용: 크리에이터 신청서(반려 기록 포함) 삭제. 인증 불필요.
 */
@RestController
@RequestMapping("/test/creator-applications")
@RequiredArgsConstructor
public class TestCreatorApplicationController implements TestCreatorApplicationControllerDocs {

    private final CreatorApplicationRepository creatorApplicationRepository;
    private final CreatorApplicationHistoryRepository applicationHistoryRepository;

    @Override
    @DeleteMapping("/{applicationId}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteApplication(
            @PathVariable("applicationId") Long applicationId) {
        if (!creatorApplicationRepository.existsById(applicationId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        applicationHistoryRepository.deleteByApplication_Id(applicationId);
        creatorApplicationRepository.deleteById(applicationId);

        return ResponseEntity.ok(Map.of(
                "message", "크리에이터 신청서(반려 기록)가 삭제되었습니다.",
                "applicationId", String.valueOf(applicationId)
        ));
    }
}
