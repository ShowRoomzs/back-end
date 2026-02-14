package showroomz.api.admin.notice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.docs.AdminNoticeControllerDocs;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.api.admin.notice.service.AdminNoticeService;

import java.net.URI;

@RestController
@RequestMapping("/v1/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController implements AdminNoticeControllerDocs {

    private final AdminNoticeService adminNoticeService;

    @Override
    @PostMapping
    public ResponseEntity<Void> registerNotice(@Valid @RequestBody AdminNoticeRegisterRequest request) {
        Long noticeId = adminNoticeService.registerNotice(request);
        return ResponseEntity.created(URI.create("/v1/user/notices/" + noticeId)).build();
    }
}
