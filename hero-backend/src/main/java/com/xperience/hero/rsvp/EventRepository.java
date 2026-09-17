package com.xperience.hero.rsvp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByHostToken(String hostToken);

    /**
     * Serializes every allocation decision for one event (design: DC1, CI1, CI2).
     *
     * <p>Two invitees accepting the last place both pass through this lock, so the
     * capacity check and the resulting write cannot interleave. Contention is bounded
     * to a single event because no invariant spans two (design: A5, TI1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
