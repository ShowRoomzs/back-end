package showroomz.api.admin.home.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.home.docs.AdminHomeControllerDocs;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.api.admin.home.service.AdminHomeAssembler;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminHomeController implements AdminHomeControllerDocs {

    private final AdminHomeAssembler homeAssembler;

    @Override
    @GetMapping("/home")
    public ResponseEntity<AdminHomeDto.Home> getHome() {
        return ResponseEntity.ok(homeAssembler.getHome());
    }

    @Override
    @GetMapping("/badges")
    public ResponseEntity<AdminHomeDto.Badges> getBadges() {
        return ResponseEntity.ok(homeAssembler.getBadges());
    }
}
