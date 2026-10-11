package showroomz.domain.calendar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.calendar.entity.BusinessHoliday;

import java.time.LocalDate;
import java.util.List;

public interface BusinessHolidayRepository extends JpaRepository<BusinessHoliday, LocalDate> {

    @Query("SELECT h.date FROM BusinessHoliday h")
    List<LocalDate> findAllDates();

    @Query("SELECT h FROM BusinessHoliday h WHERE h.date BETWEEN :from AND :to ORDER BY h.date ASC")
    List<BusinessHoliday> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
