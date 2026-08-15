package showroomz.api.creator.showroom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.auth.DTO.ShowroomNameCheckResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileResponse;
import showroomz.api.creator.showroom.dto.ShowroomProfileUpdateRequest;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.ShowroomNamePolicy;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * §22-1 쇼룸 프로필 — 소비자에게 공개되는 정보만 다룬다.
 *
 * <p>계정·사업자 정보·정산 계좌·활동 채널은 기본정보 관리(#9)가 맡는다. 두 메뉴를 가르는 기준은
 * "소비자가 보느냐" 하나뿐이라, 여기에 비공개 값을 얹기 시작하면 분리가 무너진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorShowroomService {

    private static final int INTRODUCTION_MAX_LENGTH = 50;
    private static final String REQUIRED_URL_SCHEME = "https";

    private final CreatorRepository creatorRepository;

    @Value("${app.base-url:https://www.showroomz.co.kr}")
    private String baseUrl;

    public ShowroomProfileResponse getProfile(Long userId) {
        Creator creator = getMyCreator(userId);
        return ShowroomProfileResponse.of(creator, buildShowroomUrl(creator));
    }

    /**
     * §22-1 저장 — 쇼룸명·프로필 이미지·소개글·인스타그램 URL만 바뀐다.
     *
     * <p>쇼룸 주소는 요청에 없고 여기서도 건드리지 않는다. 쇼룸명을 바꿔도 주소가 따라 바뀌면
     * 인플루언서가 인스타그램 프로필·스토리에 뿌려둔 링크가 전부 죽기 때문이다.
     *
     * <p>여기서 바꾼 값은 소비자 앱 계정(닉네임·프로필 이미지)에 전파되지 않는다 — 쇼룸은 판매 채널의
     * 간판이고 앱 계정은 개인 소비 계정이라, 같은 값을 강제하면 어느 한쪽이 반드시 부적절해진다.
     */
    @Transactional
    public ShowroomProfileResponse updateProfile(Long userId, ShowroomProfileUpdateRequest request) {
        Creator creator = getMyCreator(userId);

        String showroomName = trimToNull(request.getShowroomName());
        String introduction = trimToNull(request.getIntroduction());
        String instagramUrl = trimToNull(request.getInstagramUrl());
        String profileImageUrl = trimToNull(request.getProfileImageUrl());

        validateShowroomName(creator, showroomName);
        validateIntroduction(introduction);
        validateInstagramUrl(instagramUrl);

        creator.updateShowroomProfile(showroomName, introduction, instagramUrl);
        creator.changeProfileImage(profileImageUrl);

        return ShowroomProfileResponse.of(creator, buildShowroomUrl(creator));
    }

    /**
     * §22-2 쇼룸명 중복 확인 — 저장 전에 필드 아래 문구로 알려주기 위한 조회.
     * 자기 자신의 현재 쇼룸명은 중복이 아니다(바꾸지 않고 저장하는 경우가 있다).
     */
    public ShowroomNameCheckResponse checkShowroomName(Long userId, String showroomName) {
        Creator creator = getMyCreator(userId);
        String candidate = trimToNull(showroomName);

        if (candidate == null) {
            return new ShowroomNameCheckResponse(false, "INVALID_FORMAT", "쇼룸명은 필수 입력값입니다.");
        }
        if (!ShowroomNamePolicy.isValidFormat(candidate)) {
            return new ShowroomNameCheckResponse(false, "INVALID_FORMAT", ShowroomNamePolicy.FORMAT_MESSAGE);
        }
        if (creatorRepository.existsByShowroomNameAndIdNot(candidate, creator.getId())) {
            return new ShowroomNameCheckResponse(false, "DUPLICATE", "이미 사용 중인 쇼룸명입니다. 다른 이름을 입력해주세요.");
        }
        return new ShowroomNameCheckResponse(true, "AVAILABLE", "사용 가능한 쇼룸명입니다.");
    }

    private void validateShowroomName(Creator creator, String showroomName) {
        if (!ShowroomNamePolicy.isValidFormat(showroomName)) {
            throw new BusinessException(ErrorCode.INVALID_SHOWROOM_NAME_FORMAT);
        }
        if (creatorRepository.existsByShowroomNameAndIdNot(showroomName, creator.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SHOWROOM_NAME);
        }
    }

    private void validateIntroduction(String introduction) {
        if (introduction != null && introduction.length() > INTRODUCTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.SHOWROOM_INTRODUCTION_TOO_LONG);
        }
    }

    /** §22-1 — 소비자에게 노출되는 링크라 스킴까지 확인한다(`instagram.com/...`처럼 스킴 없는 값은 거른다). */
    private void validateInstagramUrl(String instagramUrl) {
        if (instagramUrl == null) {
            return;
        }
        try {
            URI uri = new URI(instagramUrl);
            if (!REQUIRED_URL_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new BusinessException(ErrorCode.INVALID_INSTAGRAM_URL);
            }
        } catch (URISyntaxException e) {
            // 공백이 섞인 주소 등 URI로 해석되지 않는 값
            throw new BusinessException(ErrorCode.INVALID_INSTAGRAM_URL);
        }
    }

    /** 쇼룸 주소는 핸들만 저장하고, 소비자에게 보여줄 전체 URL은 조회 시 조립한다. */
    private String buildShowroomUrl(Creator creator) {
        if (creator.getShowroomAddress() == null) {
            return null;
        }
        return baseUrl + "/@" + creator.getShowroomAddress();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Creator getMyCreator(Long userId) {
        return creatorRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
