package showroomz.domain.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;

import java.time.LocalDate;

/**
 * 영업일 달력의 공휴일(1009 기획 수정본 10절 Q1) — 발송 기한 · 취소 요청 응답 기한 · 검수 기한이 전부 이 달력을 쓴다.
 * 매년 운영자가 적재하고, 임시 공휴일 · 대체 공휴일은 어드민에서 더한다. 주말은 여기 넣지 않는다(달력이 따로 뺀다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "business_holiday")
public class BusinessHoliday extends BaseTimeEntity {

    @Id
    @Column(name = "holiday_date")
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "registered_by")
    private Long registeredBy;

    public BusinessHoliday(LocalDate date, String name, Long registeredBy) {
        this.date = date;
        this.name = name;
        this.registeredBy = registeredBy;
    }

    public void rename(String name) {
        this.name = name;
    }
}
