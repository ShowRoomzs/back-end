package showroomz.api.app.showroom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.showroom.DTO.BlockedShowroomResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorBlock;
import showroomz.domain.member.creator.entity.CreatorFollowEvent;
import showroomz.domain.member.creator.repository.CreatorBlockRepository;
import showroomz.domain.member.creator.repository.CreatorFollowEventRepository;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 쇼룸(크리에이터) 차단 — App Store 심사 1.2(사용자 생성 콘텐츠 앱의 차단 수단).
 *
 * <p>차단한 쇼룸은 그 소비자의 피드(전체 · 팔로잉 · 추천) · 쇼룸 목록 · 검색 · 자동완성에서 빠진다
 * ({@link CreatorBlockRepository#findCreatorIdsByUserId} → {@code creator.id NOT IN}).
 * 쇼룸 프로필 · 게시물 상세는 열린다 — 차단 해제 버튼이 거기 있다. 차단하면 팔로우도 끊는다(팔로잉 피드에 남지 않게).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ShowroomBlockService {

    private final CreatorBlockRepository creatorBlockRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final CreatorFollowEventRepository followEventRepository;
    private final CreatorRepository creatorRepository;
    private final UserRepository userRepository;

    /** 차단 — 이미 차단했으면 아무 동작도 하지 않는다. 팔로우 중이면 팔로우를 끊는다(언팔로우 이력도 남긴다). */
    public void blockShowroom(String username, Long showroomId) {
        Users user = getUser(username);
        Creator creator = getCreator(showroomId);
        if (creator.getUser() != null && user.getId().equals(creator.getUser().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "본인 쇼룸은 차단할 수 없습니다.");
        }
        if (!creatorBlockRepository.existsByUserAndCreator(user, creator)) {
            creatorBlockRepository.save(new CreatorBlock(user, creator));
        }
        if (creatorFollowRepository.existsByUserAndCreator(user, creator)) {
            creatorFollowRepository.deleteByUserAndCreator(user, creator);
            followEventRepository.save(CreatorFollowEvent.unfollow(creator.getId(), user.getId(), LocalDateTime.now()));
        }
    }

    /** 차단 해제 — 차단하지 않았으면 아무 동작도 하지 않는다. 끊은 팔로우는 되살리지 않는다. */
    public void unblockShowroom(String username, Long showroomId) {
        Users user = getUser(username);
        Creator creator = getCreator(showroomId);
        if (creatorBlockRepository.existsByUserAndCreator(user, creator)) {
            creatorBlockRepository.deleteByUserAndCreator(user, creator);
        }
    }

    /** 차단한 쇼룸 목록 — 최근 차단순. */
    @Transactional(readOnly = true)
    public PageResponse<BlockedShowroomResponse> getBlockedShowrooms(String username, PagingRequest pagingRequest) {
        Users user = getUser(username);
        Page<BlockedShowroomResponse> page = creatorBlockRepository
                .findPageByUserId(user.getId(), pagingRequest.toPageable(Sort.unsorted()))
                .map(ShowroomBlockService::toResponse);
        return new PageResponse<>(page);
    }

    /** 이 쇼룸을 내가 차단했는가 — 쇼룸 프로필의 「차단 해제」 버튼용. 비로그인은 false. */
    @Transactional(readOnly = true)
    public boolean isBlocked(Long userId, Long showroomId) {
        if (userId == null || showroomId == null) {
            return false;
        }
        return creatorBlockRepository.existsByUser_IdAndCreator_Id(userId, showroomId);
    }

    private static BlockedShowroomResponse toResponse(CreatorBlock block) {
        Creator creator = block.getCreator();
        return BlockedShowroomResponse.builder()
                .showroomId(creator.getId())
                .showroomName(creator.getShowroomName() != null ? creator.getShowroomName()
                        : creator.getUser() == null ? null : creator.getUser().getNickname())
                .showroomImageUrl(creator.getProfileImageUrl())
                .blockedAt(block.getCreatedAt())
                .build();
    }

    private Users getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Creator getCreator(Long showroomId) {
        return creatorRepository.findById(showroomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
