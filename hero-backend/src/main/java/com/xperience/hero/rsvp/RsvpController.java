package com.xperience.hero.rsvp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Two surfaces, deliberately separate.
 *
 * <p>Scope is always taken from the token in the path, never from an identifier the
 * caller supplies alongside it (design: Step 11 — "scope is derived from the
 * credential"). There is no endpoint that accepts an event id and a separate claim
 * about who is calling.
 */
@RestController
@RequestMapping("/api")
class RsvpController {

    private final RsvpService service;

    RsvpController(RsvpService service) {
        this.service = service;
    }

    // ------------------------------------------------------------ host surface

    @PostMapping("/events")
    Api.CreatedEvent create(@RequestBody Api.CreateEventRequest request) {
        return service.createEvent(request);
    }

    @GetMapping("/host/{hostToken}")
    Api.HostView host(@PathVariable String hostToken) {
        return service.hostView(hostToken);
    }

    @PostMapping("/host/{hostToken}/invitations")
    List<Api.InvitedPerson> invite(@PathVariable String hostToken,
                                   @RequestBody Api.InviteRequest request) {
        return service.invite(hostToken, request);
    }

    @PostMapping("/host/{hostToken}/close")
    Api.HostView close(@PathVariable String hostToken) {
        return service.close(hostToken);
    }

    @PostMapping("/host/{hostToken}/cancel")
    Api.HostView cancel(@PathVariable String hostToken) {
        return service.cancel(hostToken);
    }

    // --------------------------------------------------------- invitee surface

    @GetMapping("/invitations/{token}")
    Api.InviteeView invitation(@PathVariable String token) {
        return service.inviteeView(token);
    }

    @PostMapping("/invitations/{token}/response")
    Api.InviteeView respond(@PathVariable String token,
                            @RequestBody Api.RespondRequest request) {
        return service.respond(token, request.response());
    }
}
