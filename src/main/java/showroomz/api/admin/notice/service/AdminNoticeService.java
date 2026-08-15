package showroomz.api.admin.notice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.notice.dto.AdminNoticeDetailResponse;
import showroomz.api.admin.notice.dto.AdminNoticeListRequest;
import showroomz.api.admin.notice.dto.AdminNoticeListResponse;
import showroomz.api.admin.notice.dto.AdminNoticePageResponse;
import showroomz.api.admin.notice.dto.AdminNoticeRegisterRequest;
import showroomz.api.admin.notice.dto.AdminNoticeStatusCount;
import showroomz.api.admin.notice.dto.AdminNoticeUpdateRequest;
import showroomz.api.admin.notice.type.AdminNoticeStatusFilter;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.repository.NoticeRepository;
import showroomz.domain.notice.type.NoticeStatus;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminNoticeService {

    /** 본문 이미지 최대 장수 (기획 §20-4) */
    private static final int MAX_CONTENT_IMAGES = 3;

    /** 클라이언트 제한은 우회 가능하므로 서버에서 본문 이미지를 다시 센다 (기획 §20-4) */
    private static final Pattern CONTENT_IMAGE_TAG = Pattern.compile("<img\\b", Pattern.CASE_INSENSITIVE);

    private static final String UNKNOWN_OPERATOR = "운영자";

    private final NoticeRepository noticeRepository;
    private final SellerRepository sellerRepository;

    /** 등록 = 즉시 게시 (기획 §20-2) */
    @Transactional
    public Long registerNotice(AdminNoticeRegisterRequest request, Long operatorId) {
        validateContentImages(request.getContent());

        Notice notice = Notice.builder()
                .title(request.getTitle().trim())
                .content(request.getContent())
                .pinned(request.isPinned())
                .authorId(operatorId)
                .build();

        return noticeRepository.save(notice).getId();
    }

    public AdminNoticePageResponse getNotices(AdminNoticeListRequest request, PagingRequest pagingRequest) {
        // 정렬은 중요 고정 + 등록일 최신순 고정이라 페이징 요청의 정렬은 쓰지 않는다 (기획 §20-3)
        Pageable pageable = pagingRequest.toPageable(Sort.unsorted());
        NoticeStatus status = request.getStatus().getStatus();
        String keyword = request.getKeyword();

        Page<Notice> noticePage = noticeRepository.findAdminNoticeList(status, keyword, pageable);

        return AdminNoticePageResponse.builder()
                .content(noticePage.getContent().stream().map(AdminNoticeListResponse::from).toList())
                .pageInfo(new PaginationInfo(noticePage))
                .statusCounts(buildStatusCounts(keyword))
                .pinnedCount(noticeRepository.countPinned(status, keyword))
                .build();
    }

    public AdminNoticeDetailResponse getNotice(Long noticeId) {
        Notice notice = findNotice(noticeId);
        return AdminNoticeDetailResponse.of(notice, resolveAuthorName(notice.getAuthorId()));
    }

    /**
     * 저장은 상태를 건드리지 않는다 (기획 §20-2).
     * 게시 종료 상태의 수정도 허용한다 — 내려간 공지의 문구를 미리 손봐 두는 운영 흐름을 위해서다.
     */
    @Transactional
    public void updateNotice(Long noticeId, AdminNoticeUpdateRequest request) {
        validateContentImages(request.getContent());

        Notice notice = findNotice(noticeId);
        notice.update(request.getTitle().trim(), request.getContent(), request.isPinned());
    }

    /** 게시 종료 (기획 §20-5) — 삭제가 아니라 노출만 중단하며, 목록에는 남는다. */
    @Transactional
    public void endNotice(Long noticeId) {
        Notice notice = findNotice(noticeId);

        if (!notice.isPublished()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 게시 종료된 공지입니다.");
        }

        noticeRepository.changeStatus(noticeId, NoticeStatus.ENDED, LocalDateTime.now());
    }

    /** 재게시 (기획 §20-5) — 등록일·수정일을 갱신하지 않는다. 재게시는 새 공지가 아니다. */
    @Transactional
    public void publishNotice(Long noticeId) {
        Notice notice = findNotice(noticeId);

        if (notice.isPublished()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 게시 중인 공지입니다.");
        }

        noticeRepository.changeStatus(noticeId, NoticeStatus.PUBLISHED, null);
    }

    private Notice findNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA, "존재하지 않는 공지입니다."));
    }

    private List<AdminNoticeStatusCount> buildStatusCounts(String keyword) {
        Map<NoticeStatus, Long> counts = noticeRepository.countByStatusGroup(keyword);

        List<AdminNoticeStatusCount> statusCounts = new ArrayList<>();
        long total = 0L;
        for (AdminNoticeStatusFilter filter : AdminNoticeStatusFilter.values()) {
            if (filter.getStatus() == null) {
                continue;
            }
            long count = counts.getOrDefault(filter.getStatus(), 0L);
            total += count;
            statusCounts.add(AdminNoticeStatusCount.of(filter, count));
        }
        statusCounts.add(0, AdminNoticeStatusCount.of(AdminNoticeStatusFilter.ALL, total));

        return statusCounts;
    }

    private String resolveAuthorName(Long authorId) {
        if (authorId == null) {
            return UNKNOWN_OPERATOR;
        }
        return sellerRepository.findById(authorId).map(Seller::getName).orElse(UNKNOWN_OPERATOR);
    }

    private void validateContentImages(String content) {
        if (content == null) {
            return;
        }

        Matcher matcher = CONTENT_IMAGE_TAG.matcher(content);
        int imageCount = 0;
        while (matcher.find()) {
            imageCount++;
        }

        if (imageCount > MAX_CONTENT_IMAGES) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    String.format("본문 이미지는 최대 %d장까지 넣을 수 있습니다. (현재 %d장)", MAX_CONTENT_IMAGES, imageCount)
            );
        }
    }
}
