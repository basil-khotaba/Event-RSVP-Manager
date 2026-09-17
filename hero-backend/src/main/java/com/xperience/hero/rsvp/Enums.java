package com.xperience.hero.rsvp;

/**
 * The closed response vocabulary (design: F11).
 * The absence of a value means "not answered yet" (design: B13, decided as absence).
 */
enum ResponseValue {
    YES, NO, MAYBE
}

/**
 * Whether an invitee's intent currently holds a place (design: Step 10).
 * Deliberately separate from ResponseValue: a promotion changes standing
 * without the invitee ever changing their answer.
 */
enum Standing {
    NONE, HOLDING, WAITING
}

/**
 * Stored event status. Finality is NOT a status here — it is derived from
 * the start instant and the clock (design: DC2).
 */
enum EventStatus {
    ACCEPTING, CLOSED, CANCELLED
}
