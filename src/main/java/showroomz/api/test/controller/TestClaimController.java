package showroomz.api.test.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.test.docs.TestClaimControllerDocs;
import showroomz.api.test.dto.TestClaimDto;
import showroomz.api.test.service.TestClaimService;

/**
 * 테스트 전용: 클레임 상태 강제 전이. 인증 불필요.
 */
@RestController
@RequestMapping("/test/claims")
@RequiredArgsConstructor
public class TestClaimController implements TestClaimControllerDocs {

    private final TestClaimService testClaimService;

    @Override
    @PostMapping("/{claimId}/reship-delivered")
    public ResponseEntity<TestClaimDto.ReshipDeliveredResponse> markReshipDelivered(
            @PathVariable("claimId") Long claimId) {
        return ResponseEntity.ok(testClaimService.markReshipDelivered(claimId));
    }
}
