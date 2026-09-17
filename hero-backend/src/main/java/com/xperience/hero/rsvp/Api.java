package com.xperience.hero.rsvp;

import java.time.Instant;
import java.util.List;

/**
 * Request and response shapes. Separated from the entities so that the host view
 * and the invitee view can differ in what they carry, not merely in what they display
 * (design: F16, AI4 — the boundary is what is sent).
 */
public final class Api {

    private Api() {
    }

    public record CreateEventRequest(
            String title,
            String description,
            Instant startsAt,
            String location,
            Integer capacity) {
    }

    public record CreatedEvent(Long eventId, String hostToken) {
    }

    public record InviteRequest(List<String> emails) {
    }

    public record InvitedPerson(String email, String token, String status) {
    }

    /** Counts are derived on every read; nothing is stored (design: DI4, DC3). */
    public record Counts(
            long yes,
            long no,
            long maybe,
            long notAnswered,
            long holding,
            long waiting,
            Integer capacity,
            Integer placesLeft) {
    }

    public record AttendeeRow(
            String email,
            String response,
            String standing,
            Instant waitlistedAt,
            Instant respondedAt,
            String token) {
    }

    /** The aggregate. Only ever sent to a caller holding the host token (design: AI1). */
    public record HostView(
            Long eventId,
            String title,
            String description,
            Instant startsAt,
            String location,
            Integer capacity,
            String status,
            boolean locked,
            boolean acceptingAnswers,
            Counts counts,
            List<AttendeeRow> confirmed,
            List<AttendeeRow> waitlist,
            List<AttendeeRow> declined,
            List<AttendeeRow> maybe,
            List<AttendeeRow> notAnswered) {
    }

    /** What an invitee sees: their own answer and nothing about anyone else (design: F16). */
    public record InviteeView(
            String title,
            String description,
            Instant startsAt,
            String location,
            String eventStatus,
            boolean locked,
            boolean canRespond,
            String lockedReason,
            String yourEmail,
            String yourResponse,
            String yourStanding,
            Integer yourWaitlistPosition) {
    }

    public record RespondRequest(String response) {
    }
}
