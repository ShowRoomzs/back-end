package showroomz.api.admin.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.user.dto.AdminUserDto.HistoryItem;
import showroomz.api.admin.user.dto.AdminUserDto.HistorySummary;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.member.user.entity.UserAdminHistory;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserAdminHistoryRepository;
import showroomz.domain.member.user.service.UserAdminHistoryRecorder;
import showroomz.domain.member.user.type.UserAdminActorType;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

/**
 * 소비자 처리 이력 병합(51-6 설계서 7-2) — 저장 이력 + 파생 4종(가입 · 탈퇴 · 재가입 · 소명 접수). 정지 · 해제 전이는 {@code user_status_history}
 * 에도 남지만 <b>읽지 않는다</b> — 처리자 · 사유를 가진 저장 이력과 두 줄이 된다.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserHistoryReaderTest {

    private static final long USER_ID = 42L;
    private static final LocalDateTime JOINED = LocalDateTime.of(2026, 1, 5, 10, 0);

    @Mock
    private UserAdminHistoryRepository histories;
    @Mock
    private UserStatusHistoryRepository statusHistories;
    @Mock
    private OneToOneInquiryRepository inquiries;

    @InjectMocks
    private AdminUserHistoryReader reader;

    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setCreatedAt(JOINED);
    }

    private UserAdminHistory stored(long id, UserAdminEventType type, String actorName, String detail, LocalDateTime at) {
        UserAdminHistory history = UserAdminHistory.of(USER_ID, type, UserAdminActorType.ADMIN, actorName == null ? null : 7L,
                actorName, detail, UserAdminHistoryRecorder.REF_SANCTION, 1L, at);
        ReflectionTestUtils.setField(history, "id", id);
        return history;
    }

    private UserStatusHistory transition(UserStatus from, UserStatus to, LocalDateTime at) {
        UserStatusHistory history = UserStatusHistory.builder().user(user).previousStatus(from).newStatus(to).build();
        ReflectionTestUtils.setField(history, "createdAt", at);
        return history;
    }

    private OneToOneInquiry appeal(long id, LocalDateTime at) {
        OneToOneInquiry inquiry = OneToOneInquiry.builder().user(user).type(CsCategory.ACCOUNT).content("정지 사유 확인")
                .sanctionId(3L).build();
        ReflectionTestUtils.setField(inquiry, "id", id);
        ReflectionTestUtils.setField(inquiry, "createdAt", at);
        return inquiry;
    }

    @Test
    @DisplayName("저장 이력과 파생 사건을 한 목록 최신순으로 합치고, 정지 · 해제 전이는 전이 로그에서 다시 읽지 않는다")
    void mergesNewestFirstWithoutDuplicatingSanctions() {
        LocalDateTime suspended = JOINED.plusMonths(2);
        given(histories.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).willReturn(List.of(
                stored(2, UserAdminEventType.SUSPENSION_RELEASED, "박운영", "소명 확인 완료", suspended.plusDays(3)),
                stored(1, UserAdminEventType.SUSPENDED, "김운영", "욕설·비방", suspended)));
        given(statusHistories.findByUser_IdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of(
                transition(UserStatus.SUSPENDED, UserStatus.NORMAL, suspended.plusDays(3)),
                transition(UserStatus.NORMAL, UserStatus.SUSPENDED, suspended)));
        given(inquiries.findByUser_IdAndSanctionIdIsNotNull(USER_ID)).willReturn(List.of(appeal(77, suspended.plusDays(1))));

        List<HistoryItem> items = reader.read(user);

        assertThat(items).extracting(HistoryItem::eventType).containsExactly(
                UserAdminEventType.SUSPENSION_RELEASED,
                UserAdminEventType.APPEAL_RECEIVED,
                UserAdminEventType.SUSPENDED,
                UserAdminEventType.SIGNED_UP);
        assertThat(items.get(1)).extracting(HistoryItem::historyId, HistoryItem::refType, HistoryItem::refId,
                        HistoryItem::actorType, HistoryItem::actorName)
                .containsExactly(null, UserAdminHistoryRecorder.REF_INQUIRY, 77L, UserAdminActorType.CONSUMER, "소비자 본인");
        assertThat(items.get(0).actorName()).isEqualTo("박운영");
        assertThat(items.get(3).occurredAt()).isEqualTo(JOINED);
    }

    @Test
    @DisplayName("탈퇴 · 재가입은 전이 로그에서 파생하고, 정지로 복귀한 재가입은 그 사실을 상세에 적는다")
    void withdrawalAndRejoinAreDerived() {
        LocalDateTime withdrawn = JOINED.plusMonths(3);
        given(histories.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).willReturn(List.of());
        given(statusHistories.findByUser_IdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of(
                transition(UserStatus.WITHDRAWN, UserStatus.SUSPENDED, withdrawn.plusDays(30)),
                transition(UserStatus.SUSPENDED, UserStatus.WITHDRAWN, withdrawn),
                transition(UserStatus.WITHDRAWN, UserStatus.NORMAL, withdrawn.minusDays(30)),
                transition(UserStatus.NORMAL, UserStatus.WITHDRAWN, withdrawn.minusDays(60))));
        given(inquiries.findByUser_IdAndSanctionIdIsNotNull(USER_ID)).willReturn(List.of());

        List<HistoryItem> items = reader.read(user);

        assertThat(items).extracting(HistoryItem::eventType, HistoryItem::detail).containsExactly(
                tuple(UserAdminEventType.REJOINED, "정지 상태로 복귀"),
                tuple(UserAdminEventType.WITHDRAWN, null),
                tuple(UserAdminEventType.REJOINED, null),
                tuple(UserAdminEventType.WITHDRAWN, null),
                tuple(UserAdminEventType.SIGNED_UP, null));
    }

    @Test
    @DisplayName("처리자가 기록되지 않은 이관 행은 「운영자」로 보인다 · 같은 시각이면 저장 이력이 파생 사건보다 위다")
    void migratedRowsAndTies() {
        given(histories.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).willReturn(List.of(
                stored(5, UserAdminEventType.SUSPENDED, null, "이전 방식 처리 — 사유 · 처리자 미기록", JOINED)));
        given(statusHistories.findByUser_IdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of());
        given(inquiries.findByUser_IdAndSanctionIdIsNotNull(USER_ID)).willReturn(List.of());

        List<HistoryItem> items = reader.read(user);

        assertThat(items).extracting(HistoryItem::eventType)
                .containsExactly(UserAdminEventType.SUSPENDED, UserAdminEventType.SIGNED_UP);
        assertThat(items.get(0).actorName()).isEqualTo("운영자");
        assertThat(items.get(0).tone()).isEqualTo("DANGER");
    }

    @Test
    @DisplayName("요약은 전체 건수와 최신 N건이다")
    void summaryKeepsTotal() {
        given(histories.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).willReturn(List.of(
                stored(3, UserAdminEventType.MEMO_CREATED, "김운영", null, JOINED.plusDays(3)),
                stored(2, UserAdminEventType.MEMO_UPDATED, "김운영", null, JOINED.plusDays(2)),
                stored(1, UserAdminEventType.MEMO_DELETED, "김운영", null, JOINED.plusDays(1))));
        given(statusHistories.findByUser_IdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of());
        given(inquiries.findByUser_IdAndSanctionIdIsNotNull(USER_ID)).willReturn(List.of());

        HistorySummary summary = reader.summary(user, 2);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.content()).extracting(HistoryItem::eventType)
                .containsExactly(UserAdminEventType.MEMO_CREATED, UserAdminEventType.MEMO_UPDATED);
    }
}
