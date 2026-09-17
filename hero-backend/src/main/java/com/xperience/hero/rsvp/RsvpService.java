package com.xperience.hero.rsvp;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * The single owner of allocation state (design: Step 09 ownership rule).
 * Nothing else writes a standing or a waitlist position.
 */
@Service
public class RsvpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKENS = Base64.getUrlEncoder().withoutPadding();

    private final EventRepository events;
    private final InvitationRepository invitations;

    RsvpService(EventRepository events, InvitationRepository invitations) {
        this.events = events;
        this.invitations = invitations;
    }

    /** 32 random bytes. Unguessability is the entire access control (design: S5, DC6). */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKENS.encodeToString(bytes);
    }

    // ---------------------------------------------------------------- events

    @Transactional
    public Api.CreatedEvent createEvent(Api.CreateEventRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw badRequest("title is required");
        }
        if (request.startsAt() == null) {
            throw badRequest("startsAt is required");
        }
        if (request.capacity() != null && request.capacity() < 1) {
            throw badRequest("capacity must be at least 1, or omitted for an unbounded event");
        }

        Event event = new Event();
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setStartsAt(request.startsAt());
        event.setLocation(request.location());
        event.setCapacity(request.capacity());
        event.setHostToken(newToken());
        events.save(event);

        return new Api.CreatedEvent(event.getId(), event.getHostToken());
    }

    @Transactional
    public List<Api.InvitedPerson> invite(String hostToken, Api.InviteRequest request) {
        Event event = requireHost(hostToken);
        if (request.emails() == null || request.emails().isEmpty()) {
            throw badRequest("at least one email is required");
        }

        List<Api.InvitedPerson> result = new ArrayList<>();
        for (String raw : request.emails()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String email = raw.trim();

            // One invitation per address per event (design: B11, decided as unique-by-email).
            if (invitations.existsByEventIdAndEmailIgnoreCase(event.getId(), email)) {
                result.add(new Api.InvitedPerson(email, null, "already invited"));
                continue;
            }

            Invitation invitation = new Invitation();
            invitation.setEvent(event);
            invitation.setEmail(email);
            invitation.setToken(newToken());
            invitations.save(invitation);

            // No delivery happens here (design: B2, decided as links-only; the scaffold
            // has no mail capability). The host distributes these by their own means.
            result.add(new Api.InvitedPerson(email, invitation.getToken(), "invited"));
        }
        return result;
    }

    @Transactional
    public Api.HostView close(String hostToken) {
        Event event = requireHost(hostToken);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw conflict("a cancelled event cannot be closed");
        }
        event.setStatus(EventStatus.CLOSED);
        return hostView(hostToken);
    }

    @Transactional
    public Api.HostView cancel(String hostToken) {
        Event event = requireHost(hostToken);
        event.setStatus(EventStatus.CANCELLED);
        return hostView(hostToken);
    }

    // ------------------------------------------------------------ responding

    /**
     * Records an answer and settles its consequences in one transaction.
     *
     * <p>The event row is locked first, so the capacity check and the resulting write
     * cannot be separated (design: CI1, CI2). Two acceptances for one remaining place
     * therefore produce exactly one confirmation and one waitlist entry.
     */
    @Transactional
    public Api.InviteeView respond(String token, String rawResponse) {
        Invitation invitation = invitations.findByToken(token)
                .orElseThrow(() -> notFound("no such invitation"));

        // Lock the event before reading any count (design: DC1).
        Event event = events.findByIdForUpdate(invitation.getEvent().getId())
                .orElseThrow(() -> notFound("no such event"));

        if (event.isFinal()) {
            throw conflict("the event has started; answers are locked");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw conflict("the event was cancelled");
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            throw conflict("the event is closed to further responses");
        }

        ResponseValue newResponse = parseResponse(rawResponse);

        // Resubmitting an unchanged answer does nothing at all (design: CI7).
        // Without this, a retry would release and re-claim a place, sending the
        // invitee to the back of the waitlist for double-clicking.
        if (newResponse == invitation.getResponse()) {
            return inviteeView(invitation, event);
        }

        Standing previous = invitation.getStanding();
        invitation.setResponse(newResponse);
        invitation.setRespondedAt(Instant.now());

        if (newResponse == ResponseValue.YES) {
            if (previous == Standing.NONE) {
                if (hasRoom(event)) {
                    invitation.setStanding(Standing.HOLDING);
                    invitation.setWaitlistedAt(null);
                } else {
                    invitation.setStanding(Standing.WAITING);
                    invitation.setWaitlistedAt(Instant.now());
                }
            }
            // Already HOLDING or WAITING: the claim stands, and the queue position is kept.
        } else {
            // NO and MAYBE both release a place (design: B3, decided as Maybe-does-not-hold;
            // B4 follows from it — any move away from YES frees the place).
            invitation.setStanding(Standing.NONE);
            invitation.setWaitlistedAt(null);
        }

        invitations.save(invitation);

        // Nobody waits while a place stands free (design: BI5).
        if (previous == Standing.HOLDING && invitation.getStanding() != Standing.HOLDING) {
            promoteNext(event);
        }

        return inviteeView(invitation, event);
    }

    /**
     * Fills one freed place from the front of the queue.
     *
     * <p>Promotion does not fire once the record is final (design: B5, decided in
     * favour of BI7 over BI5 — finality wins). This is the contradiction the design
     * flagged; it is resolved here by decision, not by mechanism.
     */
    private void promoteNext(Event event) {
        if (event.isFinal() || event.isUnbounded() || !hasRoom(event)) {
            return;
        }
        invitations.findFirstByEventIdAndStandingOrderByWaitlistedAtAsc(event.getId(), Standing.WAITING)
                .ifPresent(next -> {
                    next.setStanding(Standing.HOLDING);
                    next.setWaitlistedAt(null);
                    invitations.save(next);
                });
    }

    private boolean hasRoom(Event event) {
        if (event.isUnbounded()) {
            return true;
        }
        long held = invitations.countByEventIdAndStanding(event.getId(), Standing.HOLDING);
        return held < event.getCapacity();
    }

    // ----------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public Api.HostView hostView(String hostToken) {
        Event event = requireHost(hostToken);
        List<Invitation> all = invitations.findByEventIdOrderByCreatedAtAsc(event.getId());

        List<Api.AttendeeRow> confirmed = new ArrayList<>();
        List<Api.AttendeeRow> waitlist = new ArrayList<>();
        List<Api.AttendeeRow> declined = new ArrayList<>();
        List<Api.AttendeeRow> maybe = new ArrayList<>();
        List<Api.AttendeeRow> notAnswered = new ArrayList<>();

        long yes = 0;
        long no = 0;
        long maybeCount = 0;
        long none = 0;

        for (Invitation invitation : all) {
            Api.AttendeeRow row = row(invitation);
            if (invitation.getResponse() == null) {
                none++;
                notAnswered.add(row);
            } else {
                switch (invitation.getResponse()) {
                    case YES -> {
                        yes++;
                        if (invitation.getStanding() == Standing.HOLDING) {
                            confirmed.add(row);
                        } else {
                            waitlist.add(row);
                        }
                    }
                    case NO -> {
                        no++;
                        declined.add(row);
                    }
                    case MAYBE -> {
                        maybeCount++;
                        maybe.add(row);
                    }
                }
            }
        }

        waitlist.sort(Comparator.comparing(Api.AttendeeRow::waitlistedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));

        long holding = confirmed.size();
        long waiting = waitlist.size();
        Integer placesLeft = event.isUnbounded()
                ? null
                : Math.max(0, event.getCapacity() - (int) holding);

        Api.Counts counts = new Api.Counts(
                yes, no, maybeCount, none, holding, waiting, event.getCapacity(), placesLeft);

        return new Api.HostView(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartsAt(),
                event.getLocation(),
                event.getCapacity(),
                event.getStatus().name(),
                event.isFinal(),
                event.isAcceptingAnswers(),
                counts,
                confirmed,
                waitlist,
                declined,
                maybe,
                notAnswered);
    }

    @Transactional(readOnly = true)
    public Api.InviteeView inviteeView(String token) {
        Invitation invitation = invitations.findByToken(token)
                .orElseThrow(() -> notFound("no such invitation"));
        return inviteeView(invitation, invitation.getEvent());
    }

    private Api.InviteeView inviteeView(Invitation invitation, Event event) {
        Integer position = null;
        if (invitation.getStanding() == Standing.WAITING && invitation.getWaitlistedAt() != null) {
            long ahead = invitations.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
                    .filter(other -> other.getStanding() == Standing.WAITING)
                    .filter(other -> other.getWaitlistedAt() != null)
                    .filter(other -> other.getWaitlistedAt().isBefore(invitation.getWaitlistedAt()))
                    .count();
            position = (int) ahead + 1;
        }

        String lockedReason = null;
        if (event.isFinal()) {
            lockedReason = "The event has started. Answers are locked.";
        } else if (event.getStatus() == EventStatus.CANCELLED) {
            lockedReason = "This event was cancelled.";
        } else if (event.getStatus() == EventStatus.CLOSED) {
            lockedReason = "This event is closed to further responses.";
        }

        return new Api.InviteeView(
                event.getTitle(),
                event.getDescription(),
                event.getStartsAt(),
                event.getLocation(),
                event.getStatus().name(),
                event.isFinal(),
                event.isAcceptingAnswers(),
                lockedReason,
                invitation.getEmail(),
                invitation.getResponse() == null ? null : invitation.getResponse().name(),
                invitation.getStanding().name(),
                position);
    }

    private Api.AttendeeRow row(Invitation invitation) {
        return new Api.AttendeeRow(
                invitation.getEmail(),
                invitation.getResponse() == null ? null : invitation.getResponse().name(),
                invitation.getStanding().name(),
                invitation.getWaitlistedAt(),
                invitation.getRespondedAt(),
                invitation.getToken());
    }

    // ------------------------------------------------------------- plumbing

    private Event requireHost(String hostToken) {
        return events.findByHostToken(hostToken)
                .orElseThrow(() -> notFound("no such event"));
    }

    private static ResponseValue parseResponse(String raw) {
        if (raw == null) {
            throw badRequest("response is required");
        }
        try {
            return ResponseValue.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("response must be one of YES, NO, MAYBE");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
