package showroomz.api.seller.home.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.seller.common.SellerMarketResolver;
import showroomz.api.seller.home.docs.SellerHomeControllerDocs;
import showroomz.api.seller.home.dto.SellerHomeDto;
import showroomz.api.seller.home.service.SellerHomeAssembler;
import showroomz.api.seller.home.service.SellerHomeCalendarService;
import showroomz.api.seller.home.service.SellerHomeGuideService;
import showroomz.api.seller.home.service.SellerHomeLiveService;
import showroomz.api.seller.home.service.SellerPendingTaskReader;

@RestController
@RequestMapping("/v1/seller")
@RequiredArgsConstructor
public class SellerHomeController implements SellerHomeControllerDocs {

    private final SellerMarketResolver marketResolver;
    private final SellerHomeAssembler homeAssembler;
    private final SellerHomeLiveService liveService;
    private final SellerHomeCalendarService calendarService;
    private final SellerHomeGuideService guideService;
    private final SellerPendingTaskReader pendingTaskReader;

    @Override
    @GetMapping("/home")
    public ResponseEntity<SellerHomeDto.Home> getHome() {
        return ResponseEntity.ok(homeAssembler.getHome(SellerMarketResolver.currentEmail()));
    }

    @Override
    @GetMapping("/home/live")
    public ResponseEntity<SellerHomeDto.Live> getLive() {
        return ResponseEntity.ok(liveService.read(marketResolver.currentMarket().getId()));
    }

    @Override
    @GetMapping("/home/calendar")
    public ResponseEntity<SellerHomeDto.Calendar> getCalendar(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(calendarService.read(marketResolver.currentMarket().getId(), month));
    }

    @Override
    @PatchMapping("/home/guide")
    public ResponseEntity<SellerHomeDto.Guide> updateGuide(@Valid @RequestBody SellerHomeDto.GuideRequest request) {
        return ResponseEntity.ok(guideService.updateHidden(marketResolver.currentMarket().getId(), request.hidden()));
    }

    @Override
    @GetMapping("/badges")
    public ResponseEntity<SellerHomeDto.Badges> getBadges() {
        return ResponseEntity.ok(pendingTaskReader.badges(marketResolver.currentMarket()));
    }
}
