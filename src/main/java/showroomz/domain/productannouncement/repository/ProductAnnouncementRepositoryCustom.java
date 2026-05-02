package showroomz.domain.productannouncement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.productannouncement.entity.ProductAnnouncement;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.time.LocalDateTime;

public interface ProductAnnouncementRepositoryCustom {

    Page<ProductAnnouncement> search(
            Long marketId,
            Pageable pageable,
            String keyword,
            String category,
            ProductAnnouncementDisplayStatus displayStatus,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    );
}
