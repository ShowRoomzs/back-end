package showroomz.api.admin.changerequest.type;

import showroomz.domain.changerequest.type.ChangeRequestStatus;

import java.util.List;

/** §16-1 어드민 목록 탭. CANCELED는 ALL에서만 노출된다(확정 사항 2). */
public enum AdminChangeRequestStatusFilter {
    PENDING(List.of(ChangeRequestStatus.PENDING)),
    APPROVED(List.of(ChangeRequestStatus.APPROVED)),
    REJECTED(List.of(ChangeRequestStatus.REJECTED)),
    ALL(List.of(ChangeRequestStatus.PENDING, ChangeRequestStatus.APPROVED,
            ChangeRequestStatus.REJECTED, ChangeRequestStatus.CANCELED));

    private final List<ChangeRequestStatus> statuses;

    AdminChangeRequestStatusFilter(List<ChangeRequestStatus> statuses) {
        this.statuses = statuses;
    }

    public List<ChangeRequestStatus> getStatuses() {
        return statuses;
    }

    public List<String> getStatusNames() {
        return statuses.stream().map(Enum::name).toList();
    }
}
