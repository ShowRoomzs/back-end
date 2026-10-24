package showroomz.api.creator.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;
import showroomz.api.creator.account.docs.CreatorAccountStatusControllerDocs;
import showroomz.api.creator.account.service.CreatorAccountStatusService;
import showroomz.api.creator.common.CreatorResolver;

@RestController
@RequestMapping("/v1/creator/account-status")
@RequiredArgsConstructor
public class CreatorAccountStatusController implements CreatorAccountStatusControllerDocs {

    private final CreatorAccountStatusService accountStatusService;

    @Override
    @GetMapping
    public ResponseEntity<MemberAccountStatusResponse> getAccountStatus() {
        return ResponseEntity.ok(accountStatusService.getStatus(CreatorResolver.currentUserId()));
    }
}
