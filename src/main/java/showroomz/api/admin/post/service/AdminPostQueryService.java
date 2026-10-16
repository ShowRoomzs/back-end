package showroomz.api.admin.post.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.post.dto.AdminPostMonitoringDto;
import showroomz.api.admin.post.dto.AdminPostMonitoringDto.TimelineActor;
import showroomz.api.admin.post.dto.AdminPostMonitoringDto.TimelineType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorFollowRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostAppeal;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.entity.PostNotificationLog;
import showroomz.domain.post.entity.PostOriginalDownload;
import showroomz.domain.post.entity.PostReport;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostMonitoringQueryRepository;
import showroomz.domain.post.repository.PostNotificationLogRepository;
import showroomz.domain.post.repository.PostOriginalDownloadRepository;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.service.PostMetricsService;
import showroomz.domain.post.type.PostAppealStatus;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostMonitoringSort;
import showroomz.domain.post.type.PostMonitoringStatus;
import showroomz.domain.post.type.PostMonitoringTab;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostReportStatus;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.domain.post.vo.PostMonitoringCounts;
import showroomz.domain.post.vo.PostMonitoringRow;
import showroomz.domain.post.vo.PostSanctionCounts;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.MaskingUtils;
import showroomz.global.utils.NicknameMasker;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 일반 게시물 모니터링 — 목록 · 탭 건수 · 상세 (§54-2 · §54-3 · §54-4).
 *
 * <p>상세는 <b>한 번의 호출</b>로 모든 카드의 재료를 내린다. 상태별 카드 배치는 FE가 {@code monitoringStatus}로 정한다.
 * 저장 테이블을 새로 만들지 않는다 — 처리 이력은 이미 기록된 7개 원천을 시간순으로 합친 것이다(54 설계 6-2).
 *
 * <p>삭제 · 보관분도 보인다(§24-6 운영자 콘솔 전용 조회). 공구 게시물은 이 화면 대상이 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostQueryService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostMonitoringQueryRepository monitoringQueryRepository;
    private final PostReportRepository postReportRepository;
    private final PostSuspensionRepository postSuspensionRepository;
    private final PostAppealRepository postAppealRepository;
    private final PostNotificationLogRepository postNotificationLogRepository;
    private final PostOriginalDownloadRepository postOriginalDownloadRepository;
    private final CreatorFollowRepository creatorFollowRepository;
    private final PostMetricsService postMetricsService;
    private final PostAppealReviewDue appealReviewDue;
    private final AdminOperatorResolver adminOperatorResolver;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ 목록 · 요약

    public PageResponse<AdminPostMonitoringDto.ListItem> getPosts(PostMonitoringTab tab, PostMonitoringSort sort,
                                                                  String keyword, Long showroomId,
                                                                  PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        Page<PostMonitoringRow> rows = monitoringQueryRepository.findPage(tab, sort, keyword, showroomId, pageable);
        if (rows.isEmpty()) {
            return new PageResponse<>(new PageImpl<AdminPostMonitoringDto.ListItem>(List.of(), pageable,
                    rows.getTotalElements()));
        }

        List<Long> postIds = rows.getContent().stream().map(PostMonitoringRow::postId).toList();
        Map<Long, Post> posts = postRepository.findAllWithCreatorByIdIn(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, String> thumbnails = new HashMap<>();
        for (PostImage image : postImageRepository.findRepresentativesByPostIds(postIds)) {
            thumbnails.put(image.getPost().getId(), image.getImageUrl());
        }
        Map<Long, Integer> imageCounts = new HashMap<>();
        for (Object[] row : postImageRepository.countByPostIds(postIds)) {
            imageCounts.put((Long) row[0], ((Number) row[1]).intValue());
        }

        // 쿼리가 정한 순서를 그대로 지킨다 — 본체 조회(IN)는 순서를 보장하지 않는다
        List<AdminPostMonitoringDto.ListItem> items = new ArrayList<>();
        for (PostMonitoringRow row : rows.getContent()) {
            Post post = posts.get(row.postId());
            if (post == null) {
                continue; // 두 조회 사이에 파기된 행 — 다음 새로고침에서 사라진다
            }
            items.add(toListItem(post, row, thumbnails.get(post.getId()),
                    imageCounts.getOrDefault(post.getId(), 0)));
        }
        return new PageResponse<>(new PageImpl<>(items, pageable, rows.getTotalElements()));
    }

    public AdminPostMonitoringDto.SummaryResponse getSummary() {
        PostMonitoringCounts counts = monitoringQueryRepository.countByTab();
        return AdminPostMonitoringDto.SummaryResponse.builder()
                .all(counts.all())
                .reported(counts.reported())
                .suspended(counts.suspended())
                .underReview(counts.underReview())
                .closed(counts.closed())
                .actionRequired(counts.actionRequired())
                .build();
    }

    // ------------------------------------------------------------------ 상세

    public AdminPostMonitoringDto.DetailResponse getPost(Long postId) {
        Post post = postRepository.findByIdWithImages(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isGeneral()) {
            // 공구 게시물은 이 화면 대상이 아니다 — 공구 관리의 숨김으로 다룬다
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        PostMonitoringRow row = monitoringQueryRepository.findRow(postId);
        long pending = row == null ? 0 : row.pendingReportCount();
        PostMonitoringStatus monitoringStatus = PostMonitoringStatus.of(post.getStatus(), pending);

        List<PostReport> reports = postReportRepository.findAllByPostIdWithReporter(postId);
        List<PostSuspension> suspensions = postSuspensionRepository.findByPost_IdOrderBySuspendedAtDesc(postId);
        PostAppeal appeal = postAppealRepository.findByPost_Id(postId).orElse(null);
        List<PostNotificationLog> logs = postNotificationLogRepository.findByPostIdOrderBySentAtAscIdAsc(postId);
        List<PostOriginalDownload> downloads = postOriginalDownloadRepository.findByPostIdOrderByDownloadedAtAsc(postId);

        Map<Long, String> operatorNames = adminOperatorResolver.namesOf(
                operatorIds(post, reports, suspensions, appeal));
        Map<Long, PostNotificationLog> suspensionNotices = suspensionNotices(logs);
        String showroomName = AdminPostFormats.showroomName(post.getCreator());

        return AdminPostMonitoringDto.DetailResponse.builder()
                .postId(post.getId())
                .postNo(AdminPostFormats.postNo(post))
                .monitoringStatus(monitoringStatus)
                .monitoringStatusLabel(monitoringStatus.getLabel())
                .status(post.getStatus())
                .content(post.getContent())
                .aspectRatio(post.getAspectRatio())
                .aspectRatioLabel(AdminPostFormats.aspectRatioLabel(post.getAspectRatio()))
                .publishedAt(post.getPublishedAt())
                .editedAt(post.getEditedAt())
                .deletedAt(post.getDeletedAt())
                .deleteReason(post.getDeleteReason())
                .purgeAt(post.getPurgeAt())
                .author(toAuthor(post.getCreator(), showroomName))
                .images(post.getImages().stream().map(image -> toImage(image, operatorNames)).toList())
                .metrics(toMetrics(post, suspensions))
                .reports(toReports(reports, operatorNames))
                .suspensions(suspensions.stream()
                        .map(s -> toSuspension(s, appeal, operatorNames, suspensionNotices.get(s.getId())))
                        .toList())
                .appeal(appeal == null ? null : toAppeal(appeal, operatorNames))
                .deletion(post.isDeleted()
                        ? toDeletion(post, suspensions, appeal, downloads, operatorNames, showroomName) : null)
                .timeline(new TimelineBuilder(operatorNames, showroomName)
                        .build(post, reports, suspensions, appeal, logs, downloads))
                .build();
    }

    // ------------------------------------------------------------------ 목록 조립

    private AdminPostMonitoringDto.ListItem toListItem(Post post, PostMonitoringRow row, String thumbnailUrl,
                                                       int imageCount) {
        PostMonitoringStatus monitoringStatus = row.monitoringStatus();
        boolean editedAfterReport = post.getEditedAt() != null && row.firstPendingReportAt() != null
                && post.getEditedAt().isAfter(row.firstPendingReportAt());
        return AdminPostMonitoringDto.ListItem.builder()
                .postId(post.getId())
                .postNo(AdminPostFormats.postNo(post))
                .monitoringStatus(monitoringStatus)
                .monitoringStatusLabel(monitoringStatus.getLabel())
                .status(post.getStatus())
                .showroomId(post.getCreator().getId())
                .showroomName(AdminPostFormats.showroomName(post.getCreator()))
                .thumbnailUrl(thumbnailUrl)
                .contentPreview(AdminPostFormats.preview(post.getContent()))
                .imageCount(imageCount)
                .publishedAt(post.getPublishedAt())
                .editedAt(post.getEditedAt())
                .editedAfterFirstReport(editedAfterReport)
                .reportCount(row.reportCount())
                .pendingReportCount(row.pendingReportCount())
                .appealDeadline(monitoringStatus == PostMonitoringStatus.SUSPENDED ? row.openAppealDeadline() : null)
                .reviewDueAt(monitoringStatus == PostMonitoringStatus.UNDER_REVIEW
                        ? appealReviewDue.of(row.appealSubmittedAt()) : null)
                .deletedAt(post.getDeletedAt())
                .deleteReason(post.getDeleteReason())
                .purgeAt(post.getPurgeAt())
                .build();
    }

    // ------------------------------------------------------------------ 상세 조립

    private AdminPostMonitoringDto.Author toAuthor(Creator creator, String showroomName) {
        Users user = creator.getUser();
        PostSanctionCounts sanctions = postNotificationLogRepository.countSanctionEventsByCreator(creator.getId());
        return AdminPostMonitoringDto.Author.builder()
                .showroomId(creator.getId())
                .showroomName(showroomName)
                .userId(user == null ? null : user.getId())
                .profileImageUrl(user == null ? null : user.getProfileImageUrl())
                .followerCount(creatorFollowRepository.countByCreator_Id(creator.getId()))
                .suspensionCount(sanctions.suspensionCount())
                .sanctionDeleteCount(sanctions.sanctionDeleteCount())
                .build();
    }

    private static AdminPostMonitoringDto.Image toImage(PostImage image, Map<Long, String> operatorNames) {
        return AdminPostMonitoringDto.Image.builder()
                .imageId(image.getId())
                .sortOrder(image.getSortOrder())
                .imageUrl(image.getImageUrl())
                .originalUrl(image.getOriginalUrl())
                .fileName(AdminPostFormats.fileName(image.getOriginalUrl()))
                .width(image.getWidth())
                .height(image.getHeight())
                .fileSize(image.getFileSize())
                .uploadedAt(image.getCreatedAt())
                .reportFlagged(image.isReportFlagged())
                .reportFlaggedAt(image.getReportFlaggedAt())
                .reportFlaggedByName(nameOf(operatorNames, image.getReportFlaggedBy()))
                .build();
    }

    /**
     * 지표 상한 — 게시중이면 지금, 노출 중지 · 심사 중이면 조치 시각(「중지 시점까지 누적」), 삭제면 삭제 시각(「동결」).
     * 스튜디오 인사이트가 열린 조치의 시각으로 상한을 자르는 것과 같은 규칙이다.
     */
    private AdminPostMonitoringDto.Metrics toMetrics(Post post, List<PostSuspension> suspensions) {
        LocalDateTime frozenAt = null;
        if (post.getStatus() == PostStatus.DELETED) {
            frozenAt = post.getDeletedAt();
        } else if (post.getStatus() == PostStatus.SUSPENDED || post.getStatus() == PostStatus.UNDER_REVIEW) {
            frozenAt = suspensions.stream().filter(PostSuspension::isOpen)
                    .map(PostSuspension::getSuspendedAt).findFirst().orElse(null);
        }
        LocalDateTime to = frozenAt != null ? frozenAt : LocalDateTime.now();
        PostMetricsService.PostMetrics metrics = postMetricsService.measure(
                post.getId(), PostMetricsService.ALL_TIME_FROM, to);
        return AdminPostMonitoringDto.Metrics.builder()
                .impressions(metrics.impressions())
                .likes(metrics.likes())
                .likeRate(metrics.likeRate())
                .showroomVisits(metrics.showroomVisits())
                .follows(metrics.follows())
                .frozenAt(frozenAt)
                .build();
    }

    private static AdminPostMonitoringDto.Reports toReports(List<PostReport> reports, Map<Long, String> operatorNames) {
        List<PostReport> pending = reports.stream().filter(PostReport::isPending).toList();
        // 메타 「3건 · 허위 · 과장 정보 2」 — 대기 신고 기준. 대기가 없으면(조치 뒤) 종결분을 뺀 전체 기준
        List<PostReport> basis = !pending.isEmpty() ? pending
                : reports.stream().filter(r -> r.getStatus() != PostReportStatus.DISMISSED).toList();
        Map<PostReportReason, Long> byReason = new EnumMap<>(PostReportReason.class);
        basis.forEach(r -> byReason.merge(r.getReasonCode(), 1L, Long::sum));
        List<AdminPostMonitoringDto.ReasonCount> counts = byReason.entrySet().stream()
                .sorted(Map.Entry.<PostReportReason, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new AdminPostMonitoringDto.ReasonCount(e.getKey(), e.getKey().getLabel(), e.getValue()))
                .toList();

        return AdminPostMonitoringDto.Reports.builder()
                .pendingCount(pending.size())
                .totalCount(reports.size())
                .firstPendingAt(pending.isEmpty() ? null : pending.get(0).getReportedAt())
                .countsByReason(counts)
                .items(reports.stream().map(r -> AdminPostMonitoringDto.Report.builder()
                        .reportId(r.getId())
                        .reasonCode(r.getReasonCode())
                        .reasonLabel(r.getReasonCode().getLabel())
                        .reasonDetail(r.getReasonDetail())
                        .status(r.getStatus())
                        .reportedAt(r.getReportedAt())
                        .reporterUserId(r.getReporter() == null ? null : r.getReporter().getId())
                        .reporterMaskedName(maskReporter(r.getReporter()))
                        .handledAt(r.getHandledAt())
                        .handledByName(nameOf(operatorNames, r.getHandledBy()))
                        .build()).toList())
                .build();
    }

    private static AdminPostMonitoringDto.Suspension toSuspension(PostSuspension suspension, PostAppeal appeal,
                                                                  Map<Long, String> operatorNames,
                                                                  PostNotificationLog notice) {
        boolean paused = appeal != null && appeal.getSuspension().getId().equals(suspension.getId());
        return AdminPostMonitoringDto.Suspension.builder()
                .suspensionId(suspension.getId())
                .open(suspension.isOpen())
                .reasonCode(suspension.getReasonCode())
                .reasonLabel(suspension.getReasonCode().getLabel())
                .reasonDetail(suspension.getReasonDetail())
                .policyClauseCode(suspension.getPolicyClauseCode())
                .policyRef(suspension.getPolicyRef())
                .suspendedAt(suspension.getSuspendedAt())
                .suspendedBy(suspension.getSuspendedBy())
                .suspendedByName(nameOf(operatorNames, suspension.getSuspendedBy()))
                .appealDeadline(suspension.getAppealDeadline())
                .deadlinePaused(paused)
                .resolution(suspension.getResolution())
                .resolvedAt(suspension.getResolvedAt())
                .resolvedByName(nameOf(operatorNames, suspension.getResolvedBy()))
                .notifiedAt(notice == null ? null : notice.getSentAt())
                .notificationDelivered(notice == null ? null : notice.getDelivered())
                .build();
    }

    private AdminPostMonitoringDto.Appeal toAppeal(PostAppeal appeal, Map<Long, String> operatorNames) {
        return AdminPostMonitoringDto.Appeal.builder()
                .appealId(appeal.getId())
                .status(appeal.getStatus())
                .content(appeal.getContent())
                .submittedAt(appeal.getSubmittedAt())
                .reviewDueAt(appealReviewDue.of(appeal.getSubmittedAt()))
                .reviewedAt(appeal.getReviewedAt())
                .reviewedByName(nameOf(operatorNames, appeal.getReviewedBy()))
                .reviewComment(appeal.getReviewComment())
                .graceUntil(appeal.getGraceUntil())
                .build();
    }

    /**
     * 삭제 경위 (C8 · C10). 작성자 삭제는 운영자 조치가 아니라 반려 사유 · 유예가 없다.
     * 원 조치 기록은 반려 삭제와 같다 — 삭제해도 증거가 사라지지 않는다.
     */
    private static AdminPostMonitoringDto.Deletion toDeletion(Post post, List<PostSuspension> suspensions,
                                                              PostAppeal appeal, List<PostOriginalDownload> downloads,
                                                              Map<Long, String> operatorNames, String showroomName) {
        PostDeleteReason reason = post.getDeleteReason();
        AdminPostMonitoringDto.Deletion.DeletionBuilder builder = AdminPostMonitoringDto.Deletion.builder()
                .type(reason)
                .deletedAt(post.getDeletedAt())
                .deletedBySelf(reason == PostDeleteReason.SELF)
                .purgeAt(post.getPurgeAt())
                .originalDownloads(List.of());

        if (reason == PostDeleteReason.APPEAL_REJECTED && appeal != null) {
            return builder
                    .deletedByName(nameOf(operatorNames, appeal.getReviewedBy()))
                    .originSuspensionId(appeal.getSuspension().getId())
                    .rejectReason(appeal.getReviewComment())
                    .graceFrom(appeal.getReviewedAt())
                    .graceUntil(appeal.getGraceUntil())
                    .originalDownloads(downloads.stream()
                            .map(d -> new AdminPostMonitoringDto.OriginalDownload(d.getDownloadedAt(), d.getImageCount()))
                            .toList())
                    .build();
        }
        if (reason == PostDeleteReason.SELF) {
            // 노출 중지 중 본인 삭제면 그 조치가 DELETED_BY_SELF로 닫혀 있다. 게시중에 지웠다면 원 조치가 없다.
            Optional<PostSuspension> origin = suspensions.stream()
                    .filter(s -> s.getResolution() == SuspensionResolution.DELETED_BY_SELF).findFirst();
            builder.deletedByName(showroomName);
            origin.ifPresent(s -> builder
                    .originSuspensionId(s.getId())
                    .daysAfterSuspension(post.getDeletedAt() == null ? null
                            : ChronoUnit.DAYS.between(s.getSuspendedAt().toLocalDate(), post.getDeletedAt().toLocalDate()))
                    .appealDeadlineAtDeletion(s.getAppealDeadline()));
            return builder.build();
        }
        if (reason == PostDeleteReason.APPEAL_EXPIRED) {
            suspensions.stream().filter(s -> s.getResolution() == SuspensionResolution.DELETED_BY_EXPIRE)
                    .findFirst().ifPresent(s -> builder.originSuspensionId(s.getId()));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------ 보조

    private static Set<Long> operatorIds(Post post, List<PostReport> reports, List<PostSuspension> suspensions,
                                         PostAppeal appeal) {
        Set<Long> ids = new HashSet<>();
        post.getImages().forEach(image -> ids.add(image.getReportFlaggedBy()));
        reports.forEach(report -> ids.add(report.getHandledBy()));
        suspensions.forEach(s -> {
            ids.add(s.getSuspendedBy());
            ids.add(s.getResolvedBy());
        });
        if (appeal != null) {
            ids.add(appeal.getReviewedBy());
        }
        ids.remove(null);
        return ids;
    }

    /** 노출 중지 통지 — payload의 suspensionId로 조치와 짝짓는다 */
    private Map<Long, PostNotificationLog> suspensionNotices(List<PostNotificationLog> logs) {
        Map<Long, PostNotificationLog> notices = new HashMap<>();
        for (PostNotificationLog log : logs) {
            if (log.getEventType() != PostNotificationEvent.SUSPENDED) {
                continue;
            }
            Long suspensionId = readLong(log.getPayload(), "suspensionId");
            if (suspensionId != null) {
                notices.putIfAbsent(suspensionId, log);
            }
        }
        return notices;
    }

    private Long readLong(String payload, String field) {
        if (payload == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload).get(field);
            return node == null || !node.canConvertToLong() ? null : node.asLong();
        } catch (Exception e) {
            log.debug("게시물 통지 payload 해석 실패 - {}", e.getMessage());
            return null;
        }
    }

    /** 가운데를 가린다(한*준). 실명(본인인증)이 없으면 닉네임을 가린다 */
    static String maskReporter(Users reporter) {
        if (reporter == null) {
            return null;
        }
        String masked = MaskingUtils.maskNameMiddle(reporter.getName());
        return masked != null ? masked : NicknameMasker.mask(reporter.getNickname());
    }

    private static String nameOf(Map<Long, String> names, Long operatorId) {
        return operatorId == null ? null : names.get(operatorId);
    }

    // ------------------------------------------------------------------ 처리 이력

    /**
     * 처리 이력 (§54-3 · 54 설계 6-2) — 7개 원천을 최신순으로 합친다.
     *
     * <p>같은 시각이면 「결과」가 「원인」 위에 온다(최신순 화면에서 위가 나중). 예: 노출 중지 → 작성자 알림 발송은
     * 같은 시각이지만 중지가 위, 반려 → 영구 삭제 전환은 같은 시각이지만 삭제가 위다.
     *
     * <p>「같은 시각」은 <b>초 단위</b>로 본다. 조치 시각과 알림 적재 시각은 한 트랜잭션 안에서 각자 시계를 읽어
     * 마이크로초가 어긋난다 — 그대로 비교하면 알림이 조치보다 위에 올라간다.
     */
    private static final class TimelineBuilder {

        private static final Map<TimelineType, Integer> WEIGHT = new EnumMap<>(Map.ofEntries(
                Map.entry(TimelineType.PUBLISHED, 0),
                Map.entry(TimelineType.EDITED, 1),
                Map.entry(TimelineType.REPORTED, 2),
                Map.entry(TimelineType.APPEAL_SUBMITTED, 3),
                Map.entry(TimelineType.ORIGINAL_DOWNLOADED, 3),
                Map.entry(TimelineType.NOTIFIED, 4),
                Map.entry(TimelineType.SUSPENDED, 5),
                Map.entry(TimelineType.REPORTS_CLOSED, 5),
                Map.entry(TimelineType.SUSPENSION_CANCELED, 5),
                Map.entry(TimelineType.APPEAL_APPROVED, 6),
                Map.entry(TimelineType.APPEAL_REJECTED, 6),
                Map.entry(TimelineType.DELETED, 7)));

        private final Map<Long, String> operatorNames;
        private final String showroomName;
        private final List<AdminPostMonitoringDto.TimelineItem> items = new ArrayList<>();

        TimelineBuilder(Map<Long, String> operatorNames, String showroomName) {
            this.operatorNames = operatorNames;
            this.showroomName = showroomName;
        }

        List<AdminPostMonitoringDto.TimelineItem> build(Post post, List<PostReport> reports,
                                                        List<PostSuspension> suspensions, PostAppeal appeal,
                                                        List<PostNotificationLog> logs,
                                                        List<PostOriginalDownload> downloads) {
            add(TimelineType.PUBLISHED, post.getPublishedAt(), TimelineActor.CREATOR, showroomName,
                    "게시물 등록", null, "INFO", post.getId());
            add(TimelineType.EDITED, post.getEditedAt(), TimelineActor.CREATOR, showroomName,
                    "게시물 수정", null, "NEUTRAL", post.getId());
            addReports(reports);
            for (PostSuspension s : suspensions) {
                add(TimelineType.SUSPENDED, s.getSuspendedAt(), TimelineActor.ADMIN, nameOf(operatorNames, s.getSuspendedBy()),
                        "노출 중지 · " + s.getReasonCode().getLabel(), s.getPolicyRef(), "DANGER", s.getId());
                if (s.getResolution() == SuspensionResolution.CANCELED_BY_ADMIN) {
                    add(TimelineType.SUSPENSION_CANCELED, s.getResolvedAt(), TimelineActor.ADMIN,
                            nameOf(operatorNames, s.getResolvedBy()), "조치 취소 · 재게시", null, "SUCCESS", s.getId());
                }
            }
            if (appeal != null) {
                add(TimelineType.APPEAL_SUBMITTED, appeal.getSubmittedAt(), TimelineActor.CREATOR, showroomName,
                        "이의 신청 접수", null, "WARNING", appeal.getId());
                if (appeal.getStatus() == PostAppealStatus.APPROVED) {
                    add(TimelineType.APPEAL_APPROVED, appeal.getReviewedAt(), TimelineActor.ADMIN,
                            nameOf(operatorNames, appeal.getReviewedBy()), "이의 신청 승인 · 재게시", null, "SUCCESS",
                            appeal.getId());
                } else if (appeal.getStatus() == PostAppealStatus.REJECTED) {
                    add(TimelineType.APPEAL_REJECTED, appeal.getReviewedAt(), TimelineActor.ADMIN,
                            nameOf(operatorNames, appeal.getReviewedBy()), "이의 신청 반려", null, "DANGER",
                            appeal.getId());
                }
            }
            for (PostNotificationLog log : logs) {
                String detail = noticeLabel(log.getEventType());
                if (detail != null) {
                    add(TimelineType.NOTIFIED, log.getSentAt(), TimelineActor.SYSTEM, null,
                            "작성자 알림 발송", detail, "INFO", log.getId());
                }
            }
            for (PostOriginalDownload d : downloads) {
                add(TimelineType.ORIGINAL_DOWNLOADED, d.getDownloadedAt(), TimelineActor.CREATOR, showroomName,
                        "원본 내려받기 (" + d.getImageCount() + "장)", null, "NEUTRAL", d.getId());
            }
            addDeletion(post, appeal);

            items.sort(Comparator.comparing((AdminPostMonitoringDto.TimelineItem item) ->
                            item.getAt().truncatedTo(ChronoUnit.SECONDS))
                    .thenComparing(item -> WEIGHT.getOrDefault(item.getType(), 0))
                    .thenComparing(AdminPostMonitoringDto.TimelineItem::getAt)
                    .reversed());
            return items;
        }

        private void addReports(List<PostReport> reports) {
            int ordinal = 0;
            Map<String, List<PostReport>> closedGroups = new LinkedHashMap<>();
            for (PostReport report : reports) {
                ordinal++;
                add(TimelineType.REPORTED, report.getReportedAt(), TimelineActor.USER, maskReporter(report.getReporter()),
                        "신고 접수 (" + ordinal + "건째)", report.getReasonCode().getLabel(), "WARNING", report.getId());
                if (report.getStatus() == PostReportStatus.DISMISSED && report.getHandledAt() != null) {
                    closedGroups.computeIfAbsent(report.getHandledAt() + "|" + report.getHandledBy(),
                            key -> new ArrayList<>()).add(report);
                }
            }
            // 한 번의 신고 종결이 닫은 신고들은 한 줄로 — 같은 처리 시각 · 처리자로 묶는다
            for (List<PostReport> group : closedGroups.values()) {
                PostReport first = group.get(0);
                add(TimelineType.REPORTS_CLOSED, first.getHandledAt(), TimelineActor.ADMIN,
                        nameOf(operatorNames, first.getHandledBy()), "신고 종결 (" + group.size() + "건)", null, "NEUTRAL",
                        first.getId());
            }
        }

        private void addDeletion(Post post, PostAppeal appeal) {
            if (!post.isDeleted() || post.getDeletedAt() == null) {
                return;
            }
            PostDeleteReason reason = post.getDeleteReason();
            if (reason == PostDeleteReason.SELF) {
                add(TimelineType.DELETED, post.getDeletedAt(), TimelineActor.CREATOR, showroomName,
                        "작성자 삭제 · 비공개 보관 전환", null, "NEUTRAL", post.getId());
            } else if (reason == PostDeleteReason.APPEAL_REJECTED) {
                add(TimelineType.DELETED, post.getDeletedAt(), TimelineActor.ADMIN,
                        appeal == null ? null : nameOf(operatorNames, appeal.getReviewedBy()),
                        "영구 삭제 · 비공개 보관 전환", null, "NEUTRAL", post.getId());
            } else {
                add(TimelineType.DELETED, post.getDeletedAt(), TimelineActor.SYSTEM, null,
                        "기한 경과 · 영구 삭제 · 비공개 보관 전환", null, "NEUTRAL", post.getId());
            }
        }

        /** 작성자에게 가는 통지만 — 본인 삭제(되돌려 알리지 않는다)와 팔로워 알림은 이력 줄로 만들지 않는다 */
        private static String noticeLabel(PostNotificationEvent event) {
            return switch (event) {
                case SUSPENDED -> "노출 중지 안내";
                case APPEAL_RECEIVED -> "이의 신청 접수 안내";
                case APPEAL_APPROVED -> "재게시 안내";
                case APPEAL_REJECTED -> "반려 안내";
                case DELETED_BY_EXPIRE -> "기한 경과 삭제 안내";
                case SUSPENSION_CANCELED -> "조치 취소 안내";
                case DELETED_BY_SELF, PUBLISHED_TO_FOLLOWERS -> null;
            };
        }

        private void add(TimelineType type, LocalDateTime at, TimelineActor actor, String actorName, String label,
                         String detail, String tone, Long refId) {
            if (at == null) {
                return;
            }
            items.add(AdminPostMonitoringDto.TimelineItem.builder()
                    .type(type).at(at).actorType(actor).actorName(actorName)
                    .label(label).detail(detail).tone(tone).refId(refId)
                    .build());
        }
    }
}
