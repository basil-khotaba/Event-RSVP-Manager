package com.xperience.hero.rsvp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An event. Owns its attributes, its capacity and its stored status
 * (design: Step 10 ownership boundaries). It does NOT own allocation state.
 */
@Entity
@Table(name = "rsvp_event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Stored as an instant, never as a local wall-clock reading (design: DI7, B9 decided as UTC instant). */
    @Column(nullable = false)
    private Instant startsAt;

    private String location;

    /** null means unbounded. Never zero (design: DI3). */
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.ACCEPTING;

    /**
     * The host's means of access. Symmetric to the invitee link (design: DC5,
     * decided as a host link because B1 was never answered and no identity
     * mechanism exists in the scaffold).
     */
    @Column(nullable = false, unique = true, length = 64)
    private String hostToken;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Finality is derived, never stored (design: DC2, BI12). */
    public boolean isFinal() {
        return !Instant.now().isBefore(startsAt);
    }

    /** Whether a new answer may be recorded at all. */
    public boolean isAcceptingAnswers() {
        return status == EventStatus.ACCEPTING && !isFinal();
    }

    public boolean isUnbounded() {
        return capacity == null;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public String getHostToken() {
        return hostToken;
    }

    public void setHostToken(String hostToken) {
        this.hostToken = hostToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
