package com.xperience.hero.rsvp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One invitee's invitation to one event, carrying both their answer and their
 * standing. Scoped to a single event (design: DI2, TI3).
 *
 * <p>Answer and standing are separate fields on purpose (design: Step 10).
 * The invitee owns {@code response}; only the allocation logic writes
 * {@code standing}.
 */
@Entity
@Table(
        name = "rsvp_invitation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "email"})
)
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String email;

    /** The invitee's entire authority. Unguessable by construction (design: S5, DC6). */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** null means "not answered yet" (design: B13, decided as absence). */
    @Enumerated(EnumType.STRING)
    private ResponseValue response;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Standing standing = Standing.NONE;

    /** Fixes overflow order: first in, first promoted (design: B10, decided as FIFO). */
    private Instant waitlistedAt;

    private Instant respondedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public ResponseValue getResponse() {
        return response;
    }

    public void setResponse(ResponseValue response) {
        this.response = response;
    }

    public Standing getStanding() {
        return standing;
    }

    public void setStanding(Standing standing) {
        this.standing = standing;
    }

    public Instant getWaitlistedAt() {
        return waitlistedAt;
    }

    public void setWaitlistedAt(Instant waitlistedAt) {
        this.waitlistedAt = waitlistedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
