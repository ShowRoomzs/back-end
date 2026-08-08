package showroomz.domain.message.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.entity.ThreadParticipant;
import showroomz.domain.message.type.ParticipantType;

import java.util.Optional;

@Repository
public interface ThreadParticipantRepository extends JpaRepository<ThreadParticipant, Long> {

    Optional<ThreadParticipant> findByThreadAndParticipantTypeAndParticipantId(
            MessageThread thread, ParticipantType participantType, Long participantId);
}
