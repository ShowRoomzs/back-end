package showroomz.api.app.showroom.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.app.showroom.DTO.ShowroomVisitRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.showroom.entity.ShowroomVisit;
import showroomz.domain.showroom.repository.ShowroomVisitRepository;
import showroomz.domain.showroom.type.ShowroomVisitSource;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShowroomVisitServiceTest {

    private static final long SHOWROOM_ID = 5L;
    private static final String USERNAME = "visitor@example.com";

    @Mock
    private ShowroomVisitRepository showroomVisitRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ShowroomVisitService showroomVisitService;

    private final Creator showroom = Creator.builder().id(SHOWROOM_ID).showroomName("뷰티 소연").build();

    private ShowroomVisitRequest request(String source, String visitorId) {
        ShowroomVisitRequest request = new ShowroomVisitRequest();
        request.setSource(source);
        request.setVisitorId(visitorId);
        return request;
    }

    private Users user(long id) {
        Users user = new Users();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("로그인 방문은 사용자 기준으로 센다 — 디바이스 식별자는 무시된다")
    void loggedInVisitIsKeyedByUser() {
        given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(showroom));
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user(7L)));
        given(showroomVisitRepository.existsByCreator_IdAndVisitorKeyAndVisitedAtAfter(
                anyLong(), anyString(), any())).willReturn(false);

        showroomVisitService.recordVisit(USERNAME, SHOWROOM_ID, request("ig", "device-abc"));

        ArgumentCaptor<ShowroomVisit> captor = ArgumentCaptor.forClass(ShowroomVisit.class);
        verify(showroomVisitRepository).save(captor.capture());
        assertThat(captor.getValue().getVisitorKey()).isEqualTo("u:7");
        assertThat(captor.getValue().getSource()).isEqualTo(ShowroomVisitSource.INSTAGRAM_LINK);
    }

    @Test
    @DisplayName("30분 세션 안의 재방문은 순방문으로 새로 세지 않는다")
    void revisitWithinSessionIsNotCounted() {
        given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(showroom));
        given(showroomVisitRepository.existsByCreator_IdAndVisitorKeyAndVisitedAtAfter(
                anyLong(), anyString(), any())).willReturn(true);

        showroomVisitService.recordVisit(null, SHOWROOM_ID, request("ig", "device-abc"));

        verify(showroomVisitRepository, never()).save(any());
    }

    @Test
    @DisplayName("소스 값이 없는 방문은 직접 유입으로 집계된다")
    void missingSourceFallsBackToDirect() {
        given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(showroom));
        given(showroomVisitRepository.existsByCreator_IdAndVisitorKeyAndVisitedAtAfter(
                anyLong(), anyString(), any())).willReturn(false);

        showroomVisitService.recordVisit(null, SHOWROOM_ID, request(null, "device-abc"));

        ArgumentCaptor<ShowroomVisit> captor = ArgumentCaptor.forClass(ShowroomVisit.class);
        verify(showroomVisitRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(ShowroomVisitSource.DIRECT);
        assertThat(captor.getValue().getVisitorKey()).isEqualTo("d:device-abc");
        assertThat(captor.getValue().getUser()).isNull();
    }

    @Test
    @DisplayName("비로그인인데 디바이스 식별자가 없으면 방문자 수가 부풀므로 거절한다")
    void anonymousVisitWithoutDeviceIdIsRejected() {
        given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.of(showroom));

        assertThatThrownBy(() -> showroomVisitService.recordVisit(null, SHOWROOM_ID, request("ig", " ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(showroomVisitRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 쇼룸이면 404를 낸다")
    void unknownShowroomIsRejected() {
        given(creatorRepository.findById(SHOWROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> showroomVisitService.recordVisit(null, SHOWROOM_ID, request("ig", "device-abc")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SHOWROOM_NOT_FOUND);
    }
}
