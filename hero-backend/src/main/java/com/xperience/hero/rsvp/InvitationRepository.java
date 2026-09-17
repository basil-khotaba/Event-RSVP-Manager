package com.xperience.hero.rsvp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    List<Invitation> findByEventIdOrderByCreatedAtAsc(Long eventId);

    boolean existsByEventIdAndEmailIgnoreCase(Long eventId, String email);

    /** Counts places currently occupied. The basis of every capacity check (design: BI1). */
    long countByEventIdAndStanding(Long eventId, Standing standing);

    /** Next in line out of overflow: earliest to join it (design: B10, decided as FIFO). */
    Optional<Invitation> findFirstByEventIdAndStandingOrderByWaitlistedAtAsc(Long eventId, Standing standing);
}
