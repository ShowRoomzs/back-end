package showroomz.api.creator.home.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.creator.common.CreatorResolver;
import showroomz.api.creator.home.docs.CreatorHomeControllerDocs;
import showroomz.api.creator.home.dto.CreatorHomeDto;
import showroomz.api.creator.home.service.CreatorHomeAssembler;
import showroomz.api.creator.home.service.CreatorHomeCalendarService;
import showroomz.api.creator.home.service.CreatorHomeLiveService;

@RestController
@RequestMapping("/v1/creator/home")
@RequiredArgsConstructor
public class CreatorHomeController implements CreatorHomeControllerDocs {

    private final CreatorResolver creatorResolver;
    private final CreatorHomeAssembler homeAssembler;
    private final CreatorHomeLiveService liveService;
    private final CreatorHomeCalendarService calendarService;

    @Override
    @GetMapping
    public ResponseEntity<CreatorHomeDto.Home> getHome() {
        return ResponseEntity.ok(homeAssembler.getHome(CreatorResolver.currentUserId()));
    }

    @Override
    @GetMapping("/live")
    public ResponseEntity<CreatorHomeDto.Live> getLive() {
        return ResponseEntity.ok(liveService.read(creatorResolver.currentCreator().getId()));
    }

    @Override
    @GetMapping("/calendar")
    public ResponseEntity<CreatorHomeDto.Calendar> getCalendar(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(calendarService.read(creatorResolver.currentCreator().getId(), month));
    }
}
