package showroomz.api.creator.profile.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.profile.dto.MyShowroomResponse;
import showroomz.api.creator.profile.dto.ShowroomNameResponse;
import showroomz.api.creator.profile.docs.CreatorProfileControllerDocs;
import showroomz.api.creator.profile.service.CreatorProfileService;

@RestController
@RequestMapping("/v1/creator/profile")
@RequiredArgsConstructor
public class CreatorProfileController implements CreatorProfileControllerDocs {

    private final CreatorProfileService creatorProfileService;

    @Override
    @GetMapping
    public ResponseEntity<MyShowroomResponse> getMyShowroom(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(creatorProfileService.getMyShowroom(userPrincipal.getUserId()));
    }

    @Override
    @GetMapping("/showroom-name")
    public ResponseEntity<ShowroomNameResponse> getMyShowroomName(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(creatorProfileService.getMyShowroomName(userPrincipal.getUserId()));
    }
}
