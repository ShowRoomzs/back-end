package showroomz.api.creator.connection.type;

import showroomz.domain.connection.type.ConnectionStatus;

import java.util.List;

/** 요청함 목록 필터 — 미처리만 / 전체(요청·수락·거절). */
public enum ConnectionRequestStatusFilter {
    REQUESTED(List.of(ConnectionStatus.REQUESTED)),
    ALL(List.of(ConnectionStatus.REQUESTED, ConnectionStatus.CONNECTED, ConnectionStatus.REJECTED));

    private final List<ConnectionStatus> statuses;

    ConnectionRequestStatusFilter(List<ConnectionStatus> statuses) {
        this.statuses = statuses;
    }

    public List<ConnectionStatus> getStatuses() {
        return statuses;
    }
}
