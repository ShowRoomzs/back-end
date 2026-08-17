package showroomz.api.creator.showroom.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;
import showroomz.api.creator.showroom.docs.CreatorShowroomControllerDocs;
import showroomz.api.creator.showroom.dto.ShowroomProfileResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileUpdateRequest;
import showroomz.api.creator.showroom.dto.ShowroomStatsResponse;
import showroomz.api.creator.showroom.service.CreatorShowroomService;
import showroomz.api.creator.showroom.service.ShowroomStatsService;
import showroomz.api.creator.showroom.type.StatsPeriod;
import showroomz.api.creator.showroom.type.TopContentSort;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * §22 쇼룸 스튜디오 GNB #8 「쇼룸 관리」 — 쇼룸 프로필 탭과 쇼룸 현황 탭.
 *
 * <p>여기에 담기는 것은 <b>소비자에게 공개되는 정보</b>뿐이다. 계정·사업자 정보·정산 계좌·활동 채널은
 * 비공개라 기본정보 관리(#9)가 맡는다. 연결코드 조회·재발급은 §13-6에서 이미 열어 둔
 * {@code /v1/creator/connections/code}를 그대로 쓴다(화면만 여기 붙는다).
 */
@RestController
@RequestMapping("/v1/creator/showroom")
@RequiredArgsConstructor
public class CreatorShowroomController implements CreatorShowroomControllerDocs {

    private final CreatorShowroomService creatorShowroomService;
    private final ShowroomStatsService showroomStatsService;

    @Override
    @GetMapping("/profile")
    public ResponseEntity<ShowroomProfileResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(creatorShowroomService.getProfile(getUserId(userPrincipal)));
    }

    @Override
    @PutMapping("/profile")
    public ResponseEntity<ShowroomProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ShowroomProfileUpdateRequest request) {
        return ResponseEntity.ok(creatorShowroomService.updateProfile(getUserId(userPrincipal), request));
    }

    @Override
    @GetMapping("/profile/check-name")
    public ResponseEntity<ShowroomNameCheckResponse> checkShowroomName(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("showroomName") String showroomName) {
        return ResponseEntity.ok(
                creatorShowroomService.checkShowroomName(getUserId(userPrincipal), showroomName));
    }

    @Override
    @GetMapping("/stats")
    public ResponseEntity<ShowroomStatsResponse> getStats(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "period", defaultValue = "DAYS_30") StatsPeriod period,
            @RequestParam(value = "topContentSort", defaultValue = "LIKES") TopContentSort topContentSort) {
        return ResponseEntity.ok(
                showroomStatsService.getStats(getUserId(userPrincipal), period, topContentSort));
    }

    private Long getUserId(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUserId();
    }
}
