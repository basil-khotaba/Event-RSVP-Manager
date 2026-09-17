# Design File — Event RSVP Manager

**Status:** Complete first draft, weaknesses identified (Step 18). Not yet revised against them.
**Authorship:** The design decisions in this file are the author's. Where a statement is an inference rather than something an input states, it is marked as such.
**Scope:** Design only — high-level, architecture-focused, no code. Working notes stay out of this file.

---

## Summary

A host needs a trustworthy answer to one question: **who is actually coming?** That answer is unstable by nature — invitees revise it, a stated limit constrains it, and it is only useful once it stops moving. This design covers collecting those answers, holding them within capacity, reallocating released places automatically, and making the record final at the event's start.

**The shape of the system.** One application over one relational database. Allocation decisions for a single event are serialized and settled in one transaction. Finality is computed from the clock rather than stored. All counts are derived, never cached. There are no workers, no queue, and no cross-event coordination — because no confirmed fact asks for any of them.

**What the design gets right.** Contention over the last place is solvable locally (F5), events are independent and therefore cleanly partitionable (A5), and the highest-value correctness property — capacity never exceeded, even transiently — is stated as an invariant rather than left implied.

**What is honestly unresolved.** Fourteen points in the source brief are unclear, eight of which block later sections. Six of those eight reduce to two questions: **what occupies a place**, and **when the record stops moving**. Two stated rules in the brief actively contradict each other (B5). No business motivation, no scale figures, and no identity mechanism exist in any input.

**The defining risk is not fragility — it is silence.** Most ways this design can fail produce no error and no contradictory record: an undelivered invitation, a forwarded link, an over-admission, a standing change nobody authorised.

---

## Contents

| # | Section | What it establishes |
|---|---|---|
| 01 | Setup | The scaffold as it actually is — verified facts |
| 02 | Raw feature brief | The source text, restated, with its silences catalogued (B1–B14) |
| 03 | Problem statement | The problem, the absent business motivation, the success conditions (C1–C6) |
| 04 | Goals and non-goals | G1–G8, and eleven boundaries with reversal conditions (N1–N11) |
| 05 | Context and constraints | Technical, product, operational, organizational (T, P, O, ORG) |
| 06 | Facts, assumptions, open questions | The audit surface: F1–F18, A1–A9, and the open-question triage |
| 07 | Actors and workflows | Seven actors; user-facing, internal, background and failure flows |
| 08 | Invariants | 41 invariants across business, data, authorization, concurrency, isolation |
| 09 | First-pass architecture | Components, ownership, flow boundaries, design choices DC1–DC9 |
| 10 | Data ownership and state model | Sources of truth, and state machines for the event and for standing |
| 11 | Trust boundaries and security | Where trust enters, where authorization must hold, S1–S10 |
| 12 | Concurrency and correctness | Duplicates, contention, stale reads, ordering, retries, side effects |
| 13 | Scalability and multi-tenancy | Growth axes, first bottlenecks, what needs architectural change |
| 14 | Risks and failure notes | Ranked by impact and **detectability**, not by probability |
| 15 | Alternatives and tradeoffs | Three directions compared, with declared tiebreakers |
| 16 | Rollout and migration | Prerequisites, phasing, compatibility, rollback, sensitivities |

**Reading paths.** For the argument: 03 → 04 → 08 → 09. For what is undecided: 06 → and the *Blocked* subsection closing most later sections. For building it: 08 → 09 → 10 → 12 → 16.

---

## Decision register

What is settled, what is offered, and what is not decided at all. Anything in the middle column is a **judgement call awaiting ratification** — it reads as design, but no input requires it.

| Status | Items |
|---|---|
| **Derived — follows from an input** | G1–G8; N1–N4, N11; most invariants (see Step 08's attribution); DC6; the component and ownership boundaries of Step 09; TI1–TI5 |
| **Proposed — awaiting ratification** | **Non-goals** N5–N10 · **Invariants** BI9, DI3, DI9, AI6, CI7 · **Architecture** DC1, DC2, DC3, DC8, DC9 · **State model** the separation of *answer* from *standing*, and absence-is-not-a-destination · **Security** S2's framing, S3 (a record of standing changes), S5, S10 · **Concurrency** inertness by comparison, and evaluating the finality instant once · **Step 15** tiebreakers TB-a–TB-e and the combined recommendation |
| **Open — no decision made** | B1–B14 (Step 02, triaged in Step 06); Q1, Q2, Q4; the business motivation in Step 03; DC4, DC5, DC7 |

> The middle row is the one a reviewer should read first. It is where this document is most likely to be wrong, because it is where it stopped describing the inputs and started making choices.

---

## How to read this file

Every statement is one of three kinds, and they are never blended:

| Tag | Meaning |
|-----|---------|
| **Fact** | Verifiable now, from the repository or the written brief. |
| **Assumption** | Adopted to let the design proceed. Not confirmed. May be wrong. |
| **Open question** | Unresolved. Needs an answer from a person, not a plausible guess. |

Gaps in the inputs were left as open questions. They were not filled in.

---

## Step 01 — Setup

All items below are **facts**, read directly from the repository.

**Repository**

- Root is `Xperience-Task-1-2026-04`; this is the git repository. The enclosing folder is not.
- No feature code exists. The backend contains only the Spring Boot entry-point class and its generated smoke test.
- Six frontend files exist but are **zero bytes**: four components, one service, one types file. Their names (`FileUpload`, `MessageComposer`, `RecipientTable`, `ResultsTable`, `bulkSendApi`) belong to a bulk-messaging application, not to this feature.

**Backend**

- Java 17, Spring Boot 4.0.5, Spring MVC (servlet stack, blocking I/O), Spring Data JPA, PostgreSQL driver, Lombok. Port 8280.
- PostgreSQL at `localhost:5432`, database `hero`, schema `hero`.
- Schema is generated by Hibernate via `ddl-auto: update`. **No migration tool** is present (no Flyway, no Liquibase).
- **No mail dependency.** The application cannot send email today.
- **No Spring Security dependency.** No authentication or authorization mechanism exists.
- `application.yml` configures a third-party WhatsApp-sending service (`wasender`) and carries a hardcoded API key as an environment-variable fallback. That key is committed to the repository.

**Frontend**

- Vite, TypeScript, Tailwind CSS v4. Port 5171.
- `package.json` pins **React 18.3.1**; the README states React 19. The two disagree.
- No router, no data-fetching library, no client state library.
- No dev-server proxy, so the browser would reach the backend cross-origin (5171 → 8280). No CORS configuration exists.

---

## Step 02 — Raw feature brief

**Source** — the feature text in the task README, captured verbatim:

> - A user can create an event with a title, description, date/time, location, and optional max-capacity.
> - The creator becomes the **host** of that event.
> - The host can invite people by email.
> - Each invitee receives a unique link and can respond: **Yes / No / Maybe**.
> - The host sees a live attendance dashboard with counts and a list of attendees.
> - If the event has a max-capacity and it is reached, new "Yes" RSVPs go to a **waitlist**.
> - A waitlisted attendee automatically moves to confirmed if a confirmed attendee changes their RSVP to No.
> - The host can cancel the event or close it to further responses at any time.
> - An invitee can change their RSVP at any point **before** the event starts.
> - After the event start time, all RSVPs are locked.

### What it is

A tool for collecting and tracking commitments to attend a specific event. A host describes an event, names who is invited, and each invited person records whether they intend to come. The tool keeps the resulting picture of attendance current, holds it within a stated limit when one is set, and stops accepting changes once the event begins.

Restated from the source text only. No capability appears here that the source does not state.

### Who uses it

| Actor | Stated or implied | Role |
|---|---|---|
| **Host** | Stated | Creates the event, names invitees, and is the only party given an aggregate view. Can end participation or call the event off. |
| **Invitee** | Stated | Records and revises a single answer about their own attendance. Has no stated visibility into anyone else's answer. |
| **Account holder / "a user"** | Implied | The first line says "a user can create an event," implying a party that exists before any event does. Whether this is distinct from the host, and how such a party comes to exist, is not stated — see **B1**. |

### Why it exists

The source text does not state a purpose. It states rules.

What it *implies*, by what it chooses to constrain: attendance figures matter enough to be worth maintaining accurately, stated limits are real enough to need enforcing rather than advising, and the answer must eventually stop moving. Beyond this, no purpose, beneficiary, or cost of failure appears in any input. See **Q4**.

### Touched system areas

Areas of the existing scaffold this feature reaches into. Named, not designed.

| Area | Current state | Nature of contact |
|---|---|---|
| Persistence (PostgreSQL / JPA) | Schema empty; Hibernate auto-generates | Events, invitees and their answers all need durable storage. |
| Backend web layer | Entry-point class only | Both actors need to reach the system; no request handling exists. |
| Identity / access control | **Absent** — no security dependency | The brief distinguishes a host from an invitee and gives them different powers. Nothing today can tell them apart. |
| Outbound messaging | **Absent** — no mail dependency; an unrelated WhatsApp integration is configured | The brief requires invitees to receive something. No delivery capability exists. |
| Frontend application shell | Single default view; no routing | Two distinct experiences are implied — host view, invitee view. |
| Frontend–backend boundary | Cross-origin, unconfigured | Any browser-based use crosses it. |
| Time | — | Event start time governs whether the system accepts input at all. |

### Unclear points

Silences and collisions in the source text. **B-numbered**; none are resolved here.

| # | Unclear point |
|---|---|
| **B1** | How a host comes to exist and is recognised. "A user can create an event" presumes an identified party; nothing states how identity is established or proven. |
| **B2** | Whether email delivery is genuinely in scope, or whether the system only produces links the host distributes by other means. |
| **B3** | Whether **Maybe** occupies a place against capacity. If it does, overflow forms behind non-committal answers; if it does not, a full event can be overrun when Maybes become Yeses. Not stated. |
| **B4** | Overflow promotion has exactly one stated trigger — a confirmed attendee switching to No. Places can also be freed by a confirmed attendee switching to **Maybe**, by the host raising capacity, or by an invitee being removed. Whether these promote anyone is not stated. |
| **B5** | Promotion is a system reaction, not a user action. Whether a promotion still occurs at or after the start-time lock is not stated; the lock and the promotion rule collide. |
| **B6** | Whether capacity can change after creation, and what happens if it is lowered below the number already confirmed. Not stated. |
| **B7** | **Cancel** and **close** appear in one sentence but differ: a closed event still occurs, a cancelled one does not. Whether either is reversible, and what existing answers mean afterwards, is not stated. |
| **B8** | "Live" is undefined. Whether the host's view updates on its own, and how stale it may be, is not stated. |
| **B9** | Which timezone governs "the event start time" for locking purposes. The lock rule cannot be evaluated without this. |
| **B10** | The ordering rule for overflow. Answers go to a waitlist, but what determines who is promoted first is never stated. |
| **B11** | Whether an invitee is identified by email address, and what happens if the same person is invited twice or an address is shared. |
| **B12** | What becomes of people still in overflow when the event starts, and what any answer means after the event has passed. |
| **B13** | How *not having answered yet* is represented and counted. The response vocabulary is closed at three values, but a silent invitee is in none of them — and the host's view must report something about them. Never addressed. |
| **B14** | Whether "the most recent answer" means the one most recently *given* or the one most recently *received*. The brief lets an invitee change their answer at any time and never addresses arrival order, so two revisions that cross in flight have no defined winner. |

---

## Step 03 — Problem statement

### The problem

Attendance is unknown, unstable, contested, perishable, and never final.

- **Unknown** — intent to attend exists only in each invitee's head until expressed, and a host cannot act on what has not been expressed.
- **Unstable** — an expressed intent is provisional. Each revision invalidates any figure previously relied upon.
- **Contested** — where places are limited, expressed intent can exceed them. Something must decide who holds a place; absent a fixed rule, that is a human judgement made after the fact.
- **Perishable** — a released place loses value as the event approaches, so delay between a withdrawal and its reallocation is itself a loss.
- **Never final** — nothing in the situation makes the answer stop moving, so the host cannot identify a moment when planning may safely begin.

> *Inference marker.* The source text states rules, not problems. The five characterisations above are a reading of what those rules exist to address. They are not stated by any input.

### Business motivation

**Not supplied by any input.** No brief, README section, or repository file states who benefits, what failure costs, or why this is worth building. Nothing has been invented to fill the gap.

To write this section honestly, the following must be answered (**Q4**):

- What does a wrong attendance figure cost, and who absorbs it?
- Is limited capacity the common case or the edge case? The weight of the contested-places problem depends on it.
- Is a promptly reallocated place worth more than an accurately counted one? They may trade off.
- Is the host an individual, or someone acting for an organisation with a budget at stake?

### Success condition

End states. No mechanism is implied by any of them.

| # | Condition |
|---|---|
| **C1** | The host can obtain the current expected attendance at any moment before the deadline, without reconciling anything by hand. |
| **C2** | Expected attendance never exceeds the stated limit — including when intents are expressed at the same instant. |
| **C3** | Intent expressed beyond the limit is retained rather than discarded, and its standing follows a rule fixed in advance rather than case-by-case judgement. |
| **C4** | A place released by a withdrawal is reallocated without the host acting. |
| **C5** | At a defined moment the answer becomes final and stops changing. |
| **C6** | The host can end participation before that moment, and no invitee is misled about whether the event stands. |

**Not success conditions.** Excluded deliberately as solution rather than problem: anything stated in terms of endpoints, tables, refresh intervals, notification channels, or credential handling.

**Conditions not yet testable.** **C1** cannot be judged until B8 is answered; **C2** and **C3** stay ambiguous until B3 is answered; **C5** is untestable until B9 is answered.

---

## Step 04 — Goals and non-goals

### Goals

Each goal traces to the problem statement or to a success condition. Nothing here introduces scope that Step 03 did not already imply.

| # | Goal | Derives from |
|---|---|---|
| **G1** | Hold one authoritative, current answer per invitee, with each revision superseding the last. | Unknown, Unstable |
| **G2** | Let the host read current expected attendance at any moment, without reconciling anything by hand. | C1 |
| **G3** | Treat a stated limit as a hard boundary that confirmed commitment never exceeds, including under simultaneous expression. | C2, Contested |
| **G4** | Retain commitment expressed beyond the limit, with standing determined by a rule fixed before it is needed. | C3, Contested |
| **G5** | Reallocate a released place without the host intervening. | C4, Perishable |
| **G6** | Make the record final at one defined moment, after which it does not move. | C5, Never final |
| **G7** | Let the host end participation, or withdraw the event, before that moment — and make that status visible to invitees. | C6 |
| **G8** | Let an invitee revise their own answer freely until finality. | Unstable |

*An invitee being able to respond without holding an account is a **constraint** the brief imposes, not a goal. It belongs to Step 05.*

### Non-goals

Each carries a reason and a reversal condition. A non-goal with neither is a preference, not a boundary.

| # | Non-goal | Why it is excluded | What would reopen it |
|---|---|---|---|
| **N1** | Finding or negotiating a time — availability polls, proposing options, recurring events. | The brief takes a single start time as given input, not as something to be decided. | A stated need for recurrence or time-finding. |
| **N2** | Payment, ticketing, or assignment of a specific place. | Nothing in the brief attaches money to a commitment, and capacity is a count, not a seat map. | Capacity ever needing to distinguish *which* place, not how many. |
| **N3** | General messaging — chat between parties, reminders, broadcast updates, campaigns. | The only outbound need stated is delivering an invitation once. | Motivation (Q4) showing that forgetting drives the failure, or B2 resolving toward system-owned delivery. |
| **N4** | Becoming an identity provider — profiles, social login, organisations, teams, role hierarchies. | The brief needs only to tell a host from an invitee, nothing richer. | B1. |
| **N5** | Public or self-serve attendance — discovery, open registration, anyone-with-a-link joining. | Participation is by invitation; every responder is someone the host named. | An explicit requirement for open events. |
| **N6** | Plus-ones, group commitments, or answering on behalf of another person. | The brief gives one answer per invitee, about themselves. It would also break capacity arithmetic, where one answer currently equals one place. | Capacity needing to count people rather than answers. |
| **N7** | Recording actual attendance — check-in, no-show tracking, post-event verification. | The record is locked at the start time and the brief says nothing about the door. The subject is intent, not presence. | Motivation (Q4) identifying no-show cost as the thing being solved. |
| **N8** | Cross-event reporting, analytics, or host history. | Every rule in the brief is scoped to a single event. | Hosts being stated to run many events. |
| **N9** | Co-hosts, delegation, or transfer of hosting. | "The creator becomes the host" is singular, and no delegation appears anywhere. | An explicit need. |
| **N10** | Host-curated overflow — manual reordering, priority tiers, choosing who is promoted. | C3 requires a rule fixed in advance. Manual curation *is* the case-by-case human judgement the problem statement identifies as the failure. | B10 resolving toward host discretion — which would contradict C3, and must be raised as a change to the problem statement rather than slipped in as a feature. |
| **N11** | Reviving the configured WhatsApp (`wasender`) integration. | A1 treats it as unrelated scaffolding. | Q2. |

> **Attribution.** G1–G8, N1–N4 and N11 follow directly from the brief or from Step 03. **N5, N6, N7, N8, N9 and N10 are proposed boundaries, not derived ones** — the brief is silent on each, and excluding them is a judgement call awaiting ratification.

### Deferred, not excluded

A non-goal is a decision. An open question is not. These remain **undecided and in scope** — they must not be read as excluded because they are unresolved:

- **B2** — whether the system delivers invitations, or only produces links for the host to distribute.
- **B6** — whether capacity may change after creation.
- **B7** — whether cancelling or closing is reversible.
- **B8** — whether the host's view updates on its own.
- **B12** — what happens to people still in overflow once the record is final.

### How these boundaries are meant to hold

Scope creep in this feature will most likely arrive as a reasonable-sounding request that quietly breaks an existing goal. Three to watch:

- *"Let the host bump someone up the waitlist"* breaks **G4** and contradicts **C3**.
- *"Let people bring a guest"* breaks **G3**, because one answer stops equalling one place.
- *"Send a reminder the day before"* is the entry point to **N3**, and pulls the system into owning delivery — which **B2** has not yet decided it does.

Any of these may be accepted, but only by amending the goal it breaks, in writing, first.

---

## Step 05 — Context and constraints

### Context

The feature is being designed into an empty but opinionated scaffold. The stack is chosen and running; the schema, the web layer and both user-facing experiences are not yet written. Nothing exists to be preserved, and nothing exists to be relied on either — including two capabilities the brief assumes (a way to tell a host from an invitee, and a way to reach an invitee).

The constraints below are the ones that materially change a design decision. Facts that are merely true are recorded at the end as immaterial.

### Technical constraints

| # | Constraint | Source | How it shapes the design |
|---|---|---|---|
| **T1** | No authentication or authorization mechanism exists. | Step 01 — no security dependency | The brief's host/invitee asymmetry has nothing to rest on. Whatever distinguishes them must be introduced by this design, and cannot be assumed from the platform. Bears on B1. |
| **T2** | No outbound delivery capability exists. | Step 01 — no mail dependency | "Invitees receive a unique link" has no mechanism behind it. Whether the system delivers or merely produces links is a design decision, not a given. Bears on B2. |
| **T3** | The web layer is a blocking, thread-per-request servlet stack, not a reactive one. | Step 01 — Spring MVC | Constrains how "live" the host's view can be. Held-open connections are expensive on this stack, so continuous streaming is not the cheap default it would be elsewhere. Bears on B8. |
| **T4** | A single relational database with real transactional guarantees is available. | Step 01 — PostgreSQL, single instance | This is an *enabling* constraint. The last-place contention problem in C2 is solvable within one transactional boundary, and no distributed coordination is needed. The design should not reach for machinery that assumes otherwise. |
| **T5** | Schema is generated implicitly by Hibernate; no migration tool exists. | Step 01 — `ddl-auto: update` | Schema changes are unversioned, unreviewed and applied at startup. There is no rollback and no record of what changed. Forces Step 16 to address this directly rather than assume a migration pipeline. |
| **T6** | The browser reaches the backend cross-origin; no CORS configuration and no dev proxy exist. | Step 01 — ports 5171 / 8280 | Any browser-based access crosses an origin boundary that is currently unconfigured. This makes the frontend–backend boundary an explicit design surface rather than an invisible one. |
| **T7** | The frontend has no routing. | Step 01 | Two distinct entry experiences are implied — a host arriving at a management view, and an invitee arriving via a per-invitee link. Nothing currently distinguishes one entry point from another. |

### Product constraints

These come from the brief. They constrain the solution, so they are constraints rather than goals.

| # | Constraint | How it shapes the design |
|---|---|---|
| **P1** | The response vocabulary is fixed and closed: **Yes / No / Maybe**. | The design cannot introduce a fourth response to resolve an ambiguity. Note that *not having answered* is distinct from all three and is not itself a response — a distinction the brief never addresses. |
| **P2** | An invitee acts without an account, through a link unique to them. | The link carries the invitee's entire authority. It is a credential in everything but name, and inherits the properties of one — it can be forwarded, retained, and used by whoever holds it. Central to Step 11. |
| **P3** | Capacity is **optional**. | Two materially different event shapes must both work: bounded events, where contention and overflow exist, and unbounded ones, where neither does. Overflow behaviour cannot be assumed universal. |
| **P4** | Overflow is prescribed as a *waitlist*, with promotion on a confirmed attendee declining. | The brief names a mechanism, not only an outcome. This narrows C3's solution space to an ordered holding state with automatic promotion, rather than any rule that merely allocates fairly. |
| **P5** | Finality is bound to the event's start time — a stored data value, not an operator action. | System behaviour changes with the passage of time, with no request to trigger it and no actor to attribute it to. This is the single most awkward constraint in the set, and drives B5 and B9. |
| **P6** | Visibility is asymmetric: the host sees the aggregate, the invitee sees only their own answer. | Establishes a trust boundary inside the feature rather than only at its edge. Bears on Step 11. |
| **P7** | One host per event, established by creation. | No delegation, no transfer, no second authority. Reinforces N9. |
| **P8** | Event attributes are an enumerated set: title, description, date/time, location, optional capacity. | The design is not open-ended about what an event is. Anything beyond this set is new scope. |

### Operational constraints

| # | Constraint | How it shapes the design |
|---|---|---|
| **O1** | No deployment environments, CI, monitoring, alerting or backups exist or are described. | Nothing can be assumed operationally. Step 16 cannot lean on a staging environment or an automated release path, because neither is known to exist. |
| **O2** | Schema mutates on application startup. | Deployment and schema change are the same event, with no separation and no rehearsal. A bad change is discovered in production by definition. |
| **O3** | A live-looking third-party API key is committed to the repository. | Any clone or fork carries the credential. It must be treated as compromised and rotated; removing it from the file does not remove it from history. Belongs to Step 11 regardless of A1. |
| **O4** | Finality is evaluated against a server clock with no stated time source or timezone discipline. | Correctness of the lock depends on a clock nobody has specified. Harmless on one machine, unsound the moment there is more than one. Bears on B9 and Step 13. |
| **O5** | Local-only startup orchestration, Windows-specific. | The running system today is a developer workstation. Any claim about production behaviour is currently untested. |

### Organizational constraints

| # | Constraint | How it shapes the design |
|---|---|---|
| **ORG1** | The design file is the graded deliverable, assessed against a published Definition of Success. | Completeness of *reasoning* outranks completeness of features. Visible open questions are worth more here than resolved-looking prose. |
| **ORG2** | Design must precede implementation, and the two are committed separately. | The design cannot be retrofitted to justify code, because no code exists yet. |
| **ORG3** | There is no product owner, no stakeholder, and no one to escalate to. *(Inference — drawn from the task's solo structure, not stated.)* | Open questions cannot be resolved by asking anyone. They are resolved by the author deciding, or they stay open. This makes the distinction between *decided* and *deferred* the responsibility of the design file itself. |
| **ORG4** | No business motivation has been supplied. | Any tradeoff requiring a value judgement — speed of reallocation versus accuracy of count, for instance — has no authority to appeal to. Step 15 will have to state its tiebreakers explicitly. |

### Recorded but immaterial

True, and deliberately excluded as having no bearing on design decisions:

- Lombok, Tailwind CSS, ESLint, and the specific Vite version — build and ergonomics only.
- Exact port numbers, the database name and schema name — configuration, not design.
- **The React 18-vs-19 contradiction (Q3)** — a real inconsistency, but it changes nothing about the design. It resolves at implementation. Kept in the ledger, not treated as a constraint here.
- Java and Spring Boot versions — they bound available APIs but do not shape any decision this design makes.

### Where constraints pull against goals

Three collisions are already visible and will need resolving in later steps:

- **T1 vs. P6 / G7** — asymmetric visibility and host-only powers are required, while nothing exists to establish who anyone is.
- **T3 vs. G2** — a continuously current host view is wanted, on a stack where holding connections open is costly. B8 decides how sharp this conflict actually is.
- **P5 / O4 vs. G6** — finality must be exact, and is defined against an unspecified clock in an unspecified timezone.

---

## Step 06 — Facts, assumptions, and open questions

This section is the audit surface for the whole design. It supersedes the running ledger used in earlier steps. Three lists, never blended; each entry is filtered to those that materially affect a design decision.

### List 1 — Confirmed facts

Verifiable now. Repository facts are confirmable by inspection; brief facts by reading the source text. None depends on judgement.

| # | Fact | Source | Why it matters |
|---|---|---|---|
| **F1** | No feature code exists. The scaffold is empty of domain logic. | Repository | Nothing must be preserved, and nothing may be relied upon. |
| **F2** | No authentication or authorization mechanism exists. | Repository | The host/invitee asymmetry has nothing to rest on. |
| **F3** | No outbound delivery capability exists. | Repository | "Invitees receive a link" has no mechanism behind it. |
| **F4** | The web layer is a blocking, thread-per-request servlet stack. | Repository | Bounds how continuously the host's view can be kept current. |
| **F5** | A single PostgreSQL instance with full transactional guarantees is available. | Repository | Contention over the last place is solvable in one transactional boundary; no distributed coordination is required. |
| **F6** | Schema is generated by Hibernate at startup; no migration tool exists. | Repository | Schema change is unversioned, unrehearsed, and simultaneous with deployment. |
| **F7** | Frontend and backend are cross-origin, with no CORS configuration and no proxy. | Repository | The client–server boundary is an explicit design surface. |
| **F8** | The frontend has no routing. | Repository | Two implied entry experiences are currently indistinguishable. |
| **F9** | A live-looking third-party API key is committed to the repository. | Repository | Present in history and in every clone. Must be treated as compromised. |
| **F10** | No environments, CI, monitoring, alerting or backups exist or are described. | Repository | Rollout cannot assume any of them. |
| **F11** | The response vocabulary is closed: Yes, No, Maybe. | Brief | No fourth response may be introduced to resolve an ambiguity. |
| **F12** | Capacity is optional. | Brief | Bounded and unbounded events are both first-class; overflow is not universal. |
| **F13** | One host per event, established by creation. | Brief | No delegation, transfer, or second authority. |
| **F14** | Finality is bound to the event's stored start time. | Brief | Behaviour changes with the clock, with no request and no actor. |
| **F15** | An invitee acts without an account, via a link unique to them. | Brief | The link carries the invitee's entire authority. |
| **F16** | Visibility is asymmetric — host sees the aggregate, invitee sees only their own answer. | Brief | A trust boundary exists inside the feature, not only at its edge. |
| **F17** | Overflow is prescribed as a waitlist, with promotion when a confirmed attendee changes to No. | Brief | A mechanism, not merely an outcome, is mandated. |
| **F18** | Event attributes are an enumerated set: title, description, date/time, location, optional capacity. | Brief | Anything beyond the set is new scope. |

### List 2 — Working assumptions

Adopted so the design can proceed. **Each is unconfirmed and may be wrong.** Each states what breaks if it is.

| # | Assumption | Why adopted | What breaks if it is wrong |
|---|---|---|---|
| **A1** | The `wasender` configuration and the empty bulk-messaging files are unrelated scaffolding, forming no part of this feature. | Their names and purpose match a different application. | Invitation delivery is WhatsApp, not email, and F3 and N11 are both wrong. |
| **A2** | The RSVP text in the task README is the feature description this design is built from. | No other description was supplied. | Step 02 onward describes the wrong feature and must be redone. |
| **A3** | One answer equals one place. An invitee commits only themselves. | The brief gives each invitee a single answer about their own attendance. | Capacity stops being a count of answers, G3's arithmetic fails, and B3 becomes substantially harder. |
| **A4** | A single server clock is the authority for finality. | One instance, one database, no distribution described. | Two instances disagree about whether the record is locked, and F14 becomes unsound. |
| **A5** | Events are independent. No shared capacity, no cross-event rules, no interaction between them. | Every rule in the brief is scoped to one event. | Invariants scoped to a single event are insufficient. |
| **A6** | The invitee set is finite and determined solely by the host. Nobody invites themselves. | Participation is by invitation throughout the brief. | N5 is wrong and open registration is in scope. |
| **A7** | Whoever holds an invitee's link is treated as that invitee. | F15 leaves no other way to recognise them. | Either identity proof is required — contradicting F15 — or responses are attributable to the wrong person. |
| **A8** | No specific regulatory or privacy regime is imposed on this system. | None is named in any input. | Invitee email addresses are personal data; a regime would impose retention, erasure and consent obligations the design does not currently carry. |
| **A9** | The deliverable is the design; later steps stay conceptual rather than committing to implementation specifics. | Q1 is undecided, and this is the reversible choice. | Later steps are under-specified for building. |

### List 3 — Open questions that materially affect the design

**Blocking** — a later step cannot be completed honestly until these are answered.

| # | Question | Blocks | Consequence of leaving it open |
|---|---|---|---|
| **B3** | Does *Maybe* occupy a place against capacity? | Steps 08, 10 | Capacity invariants cannot be stated. C2 and C3 stay ambiguous. |
| **B13** | How is *not having answered yet* represented and counted? | Steps 08, 10 | The state model is incomplete; the host's view cannot report on silent invitees. |
| **B4** | Which events free a place — only a confirmed No, or also a switch to Maybe, a raised capacity, an invitee removal? | Steps 08, 10, 12 | The promotion rule cannot be specified, so G5 is untestable. |
| **B5** | Does automatic promotion still occur at or after the start-time lock? | Steps 08, 12 | Two stated rules contradict each other and neither can be made invariant. |
| **B9** | Which timezone governs the start time for locking? | Steps 08, 12 | Finality cannot be evaluated. C5 is untestable. |
| **B10** | What determines promotion order out of overflow? | Steps 08, 10 | C3 requires a rule fixed in advance; without one, N10 cannot be enforced. |
| **B1** | How does a host come to exist and get recognised? | Steps 09, 11 | The trust boundary cannot be drawn, and F16 cannot be enforced. |
| **B7** | How do cancel and close differ, are they reversible, and what do existing answers mean afterwards? | Steps 08, 10 | The event state model cannot be closed. |

**Shaping** — answerable later, but they change the design when answered.

| # | Question | Affects |
|---|---|---|
| **B2** | Does the system deliver invitations, or only produce links for the host to distribute? | Architecture boundaries (09), failure modes (14) |
| **B6** | May capacity change after creation, and what happens if lowered below the confirmed count? | Invariants (08), concurrency (12) |
| **B8** | What does "live" mean, and how stale may the host's view be? | Architecture (09), scalability (13) |
| **B11** | Is an invitee identified by email address, and what happens on duplicates or shared addresses? | Data ownership (10), security (11) |
| **B12** | What happens to people still in overflow once the record is final? | State model (10) |
| **B14** | Does "most recent answer" mean most recently given, or most recently received? | Concurrency (12), state model (10) |
| **Q1** | Is the deliverable the design alone, or a design to be implemented? | Depth of steps 09–17. Currently undecided; A9 holds. |
| **Q2** | Is the WhatsApp integration intended, or dead scaffolding? | Confirms or breaks A1 and N11. |
| **Q4** | What is the business motivation? | Step 03's motivation section, and every tiebreaker in step 15. |

**Recorded, not material.** **Q3** — the README says React 19, `package.json` pins React 18.3.1. A genuine contradiction, but it changes no design decision and resolves at implementation. Kept visible; excluded from the material set.

### What this section says about readiness

Eight questions block later steps, and six of the eight concern the same two things: **what occupies a place**, and **when the record stops moving**. That concentration is the honest headline of this design — the architecture is not the hard part, and the parts that are hard are not yet decided.

---

## Step 07 — Actors and workflows

### Actors

| Actor | Kind | Authority | Notes |
|---|---|---|---|
| **Host** | Human | Full control of one event: creation, invitation, closing, cancelling. Sole holder of the aggregate view. | One per event (F13). How the host is recognised is unresolved — **B1**. |
| **Invitee** | Human | Records and revises exactly one answer, about themselves only. | No account (F15). Cannot see other answers (F16). |
| **Link holder** | Human | Whatever the invitee has. | **Not the same actor as the invitee.** A7 treats possession of the link as sufficient. They coincide only when the link has not been forwarded, and the system cannot tell the difference. |
| **The clock** | Non-human | Causes the record to become final, with no request and no user action (F14). | Named as an actor deliberately: it changes system state and is accountable to no one. The most easily overlooked participant in this feature. |
| **The system** | Non-human | Allocates and releases places, and promotes from overflow. | F17 makes promotion system-initiated. This is authority the brief grants to no human, including the host (N10). |
| **Delivery channel** | External | Conveys an invitation to an invitee. | Exists as an actor **only if B2 resolves toward system-owned delivery.** No capability exists today (F3). |
| **Account holder** | Human | Precedes any event; creates one. | Implied by the brief's "a user can create an event." May or may not be distinct from Host — **B1**. |

### User-facing flows

Initiated deliberately by a person.

| # | Flow | Actor | Outcome | Depends on |
|---|---|---|---|---|
| **UF1** | Create an event | Host | An event exists, with the creator as its host. | — |
| **UF2** | Invite people | Host | Named people become invitees, each with a way to reach their own response. | B2, B11 |
| **UF3** | Respond for the first time | Invitee | An answer is recorded; if Yes, a place is sought. | B3 |
| **UF4** | Revise an existing answer | Invitee | The previous answer is superseded; places may be taken or released. | B3, B4 |
| **UF5** | View attendance | Host | Current counts and attendee list. | B8, B13 |
| **UF6** | Close the event to further responses | Host | Answers stop being accepted; the event still occurs. | B7 |
| **UF7** | Cancel the event | Host | The event will not occur. | B7, B12 |
| **UF8** | View own invitation and answer | Link holder | Event details and the current answer for that invitee. | B7 — what a cancelled event shows |

*Not a flow, but a state that needs reporting: an invitee who never acts at all. Covered by **B13**, and by nothing else in this list.*

### Internal system flows

Consequences of a user-facing flow. Not requested by anyone, and not separately visible.

| # | Flow | Triggered by | Effect | Depends on |
|---|---|---|---|---|
| **IS1** | Allocate a place | A Yes arriving (UF3, UF4) | Either a confirmed place or an overflow position. | B3, and the capacity check in F12 |
| **IS2** | Release a place | A confirmed attendee's answer changing | A place becomes free. | **B4** — only a No, or also a switch to Maybe, a raised capacity, a removal? |
| **IS3** | Promote from overflow | A place being released (IS2) | A waiting invitee becomes confirmed. | B4, B5, B10 |
| **IS4** | Assign overflow position | A Yes arriving when full (IS1) | The new entry takes a defined position in the order. | **B10** — no ordering rule is stated. |
| **IS5** | Derive aggregate counts | A host viewing attendance (UF5) | Counts by response, plus overflow and non-responders. | B3, B13 |
| **IS6** | Issue a per-invitee link | Inviting someone (UF2) | An invitee gains a means of responding, unique to them. | B11 |

**IS3 is the flow to watch.** It is the only one that changes a person's standing without that person or the host acting, and it is the one the brief specifies least completely.

### Background flows

No human actor. Triggered by time or by an external system.

| # | Flow | Trigger | Effect | Depends on |
|---|---|---|---|---|
| **BG1** | The record becomes final | The event's start time passing | All answers are locked; no further change is accepted. | **B9** (which clock, which timezone), **B5** (does promotion still fire?), **B12** (what happens to those still waiting) |
| **BG2** | Deliver an invitation | Inviting someone (UF2), asynchronously | An invitee receives their link. | **Exists only if B2 resolves toward system-owned delivery.** |

> **Not decided here.** Whether BG1 is a scheduled transition that actively changes stored state, or a condition evaluated whenever the record is read, is an architecture and state-model decision belonging to Steps 09 and 10. It is listed as a workflow because it occurs either way — the choice affects how, not whether.

### Failure flows

What happens when the expected path does not hold. These are the flows most likely to be missing from an implementation.

| # | Failure | Arises from | Currently undefined |
|---|---|---|---|
| **FF1** | An answer arrives after finality | Clock crossing the start time mid-interaction | What the responder is told; whether a request in flight at the boundary is honoured. |
| **FF2** | Two Yes answers contend for one remaining place | Simultaneous UF3/UF4 | The outcome is *specified* — one confirmed, one overflowed — but nothing states how that is guaranteed. Step 12. |
| **FF3** | An answer arrives for a cancelled event | UF7 followed by UF3/UF4 | Whether the answer is refused, accepted and ignored, or accepted and meaningless. **B7** |
| **FF4** | An answer arrives for a closed event | UF6 followed by UF3/UF4 | As FF3, and possibly differently. **B7** |
| **FF5** | A link is unknown, malformed, or no longer valid | Any UF8/UF3 | Whether links can be invalidated at all is not stated anywhere. |
| **FF6** | A link is used by someone other than the intended invitee | Forwarding | **Not detectable.** A7 accepts this. Whether it is acceptable is a Step 11 decision. |
| **FF7** | An invitation is never received | Delivery failure, if B2 is system-owned | A person who never got a link is **indistinguishable from one who ignored it** — the same non-response state as B13. The host cannot tell "unreachable" from "unwilling". |
| **FF8** | Capacity is lowered below the confirmed count | UF-level edit, if B6 permits it | Whether anyone is displaced, and who. **B6** |
| **FF9** | A place is released with nobody waiting | IS2 with empty overflow | Benign, but the no-op must be explicit rather than assumed. |
| **FF10** | The same person is invited twice, or two invitees share an address | UF2 | Whether they are one invitee or two, and how their answers combine. **B11** |
| **FF11** | The host acts on an event after it has started | UF6/UF7 after finality | Whether cancelling a past event is meaningful. **B7, B12** |
| **FF12** | Finality is evaluated inconsistently | Clock skew or timezone mismatch | Two evaluations disagree on whether the record is locked. **B9**, and A4 if ever more than one instance. |

### What this step exposes

- **FF7 collapses two different conditions into one.** Undelivered and unanswered look identical in the record. If the motivation (Q4) turns out to hinge on chasing non-responders, this is a real defect rather than an edge case, and it is created by the design, not by the brief.
- **The clock and the system act without accountability.** Every flow in the background and internal sections changes someone's standing with no human behind it. Steps 11 and 12 need to say who is answerable for those changes and how they are evidenced.
- **Eleven of twelve failure flows are undefined by the brief.** Only FF2 has a stated outcome, and even that has no stated guarantee.

---

## Step 08 — Invariants

An invariant holds at every observable moment, not merely at rest. Where an open question forks one, both branches are stated rather than a choice being made.

### Business invariants

| # | Invariant | Basis | Fork |
|---|---|---|---|
| **BI1** | When capacity is set, the number of places occupied never exceeds it. | F12, C2 | **B3** — whether a Maybe occupies a place determines what "occupied" counts. |
| **BI2** | An event without capacity never has anyone in overflow. Overflow is empty at all times. | F12 | — |
| **BI3** | An invitee has at most one current answer for an event. | F11, A3 | — |
| **BI4** | An invitee's current answer is the most recent one they gave. Earlier answers never resurface. | Unstable (Step 03) | — |
| **BI5** | Overflow is non-empty only while the event is at capacity. Nobody waits while a place stands free. | F17, C4 | **B4** — which releases count; **B5** — whether this still holds after finality. |
| **BI6** | Overflow is totally ordered, and position is set by a rule fixed before it is needed — never by discretion. | C3, N10 | **B10** — the rule itself is undecided. The *existence* of a non-discretionary rule is not. |
| **BI7** | After finality, no answer changes. | F14, C5 | **B5** — whether a system promotion counts as a change. The two stated rules conflict here. |
| **BI8** | Before finality, an invitee may always revise their own answer. | F15, G8 | **B7** — closing or cancelling may suspend this. |
| **BI9** | A confirmed place is never taken from someone except by their own action. | Proposed | **B6** — lowering capacity below the confirmed count would break this. Stating it forces that decision to be deliberate. |
| **BI10** | An answer exists only for someone who was invited. | A6 | — |
| **BI11** | An event has exactly one host, from creation until the record is final. | F13 | — |
| **BI12** | Finality is terminal. Once the record is final it never becomes mutable again. | F14 | **B7** — whether a host action after the start time can reopen anything. |

### Data integrity invariants

| # | Invariant | Basis | Fork |
|---|---|---|---|
| **DI1** | Every answer belongs to exactly one invitee and exactly one event. | A5 | — |
| **DI2** | An invitation is scoped to one event. Being invited to one confers nothing on another. | A5 | — |
| **DI3** | Capacity, when present, is a whole number greater than zero. | Proposed | A capacity of zero has no stated meaning — no input addresses it. |
| **DI4** | Any count the host is shown is derivable from the underlying answers. No separately stored total may disagree with them. | G2 | — |
| **DI5** | Overflow positions within an event are unique. No two entries share a position. | BI6 | **B10** |
| **DI6** | Every invitee has exactly one means of responding, and no two invitees share one. | F15 | **B11** — duplicate or shared addresses. |
| **DI7** | The start time denotes exactly one instant, not a wall-clock reading open to interpretation. | F14 | **B9** — currently it denotes neither. |
| **DI8** | Every recorded answer holds one of the three permitted values. | F11 | **B13** — *not yet answered* is not one of them and needs its own representation. |
| **DI9** | A removed invitee occupies no place and holds no overflow position. | Proposed | **B4** — whether removal is possible at all is unstated. |

### Authorization invariants

| # | Invariant | Basis | Fork |
|---|---|---|---|
| **AI1** | Only the host of an event may see its aggregate. | F16 | **B1** — nothing currently establishes who the host is. |
| **AI2** | Only the host may close or cancel their event. | F13, F16 | **B1** |
| **AI3** | A link grants read and write access to exactly one answer — the one it designates — and to nothing else. | F15, A7 | — |
| **AI4** | No invitee can observe another invitee's answer, or any aggregate. | F16 | — |
| **AI5** | Possession of a link confers exactly that invitee's authority, and never more. | A7 | Accepts FF6 as a consequence. Whether that is tolerable is a Step 11 decision. |
| **AI6** | The host cannot set or alter any invitee's answer. | Proposed | Nothing in the brief grants this power, and N10 argues against it. Not stated either way. |
| **AI7** | No actor — host included — may reorder overflow or choose who is promoted. | N10, C3 | **B10** resolving toward host discretion would break this and contradict C3. |
| **AI8** | Authority is event-scoped. Hosting one event grants nothing over another. | A5 | — |

### Concurrency invariants

These are the ones an implementation is most likely to violate without noticing.

| # | Invariant | Basis | Fork |
|---|---|---|---|
| **CI1** | Two simultaneous acceptances for one remaining place produce exactly one confirmation and one overflow entry — never two of either. | C2, FF2 | **B3** |
| **CI2** | Capacity is never exceeded, including transiently. There is no instant, however brief, at which more places are occupied than exist. | BI1 | The distinction between "never exceeded" and "corrected quickly" is the whole of this invariant. |
| **CI3** | A released place is allocated to at most one waiting invitee. Two concurrent releases never promote the same person twice. | BI5 | **B4** |
| **CI4** | Concurrent revisions by one invitee settle on exactly one of the submitted answers. No interleaving yields a state neither request asked for. | BI3, BI4 | — |
| **CI5** | Promotion and finality never interleave to leave someone promoted after the record is final. | BI7, BI12 | **B5** — unresolvable until answered; the two rules currently contradict. |
| **CI6** | An aggregate read never shows a combination of counts that never simultaneously existed. | G2, DI4 | **B8** — how current the view must be is separate from whether it is coherent. |
| **CI7** | Submitting the same answer twice leaves the record as it was after the first. No duplicate overflow entry, no reordering. | Proposed | Not stated anywhere. Retries and double submissions are otherwise unbounded in effect. |

### Tenant isolation invariants

**There is no tenancy concept in this feature.** No organizations, no teams, no workspaces — N4 excludes them and no input implies them. Stating that plainly matters more than inventing a boundary, because the isolation that *does* exist is narrower than the word "tenant" suggests.

The real boundaries are the **event** and the **single invitee within it**:

| # | Invariant | Basis | Fork |
|---|---|---|---|
| **TI1** | Every operation is scoped to exactly one event. No action spans two. | A5 | — |
| **TI2** | One event's capacity, overflow order and answers never influence another's. | A5 | — |
| **TI3** | A link reaches exactly one answer, in exactly one event. | AI3 | — |
| **TI4** | A host sees only events they host. | F16 | **B1** — with no identity mechanism, this is currently unenforceable. |
| **TI5** | No mutable state is shared between events. | A5 | — |

> If hosting ever becomes organizational — shared events, delegated access, team ownership — every invariant above is insufficient, and N4, N9 and A5 all fall together. Step 13 should treat that as a single decision rather than three.

### Invariants that cannot yet be stated

Blocked outright by open questions. Their absence is the honest output of this step:

- **What occupies a place.** Until **B3**, BI1, CI1 and CI2 are shapes without content.
- **What frees a place.** Until **B4**, BI5 and CI3 cannot be made precise.
- **Whether the system acts after finality.** **B5** puts BI5 and BI7 in direct contradiction. This is not ambiguity — it is two stated rules that cannot both hold, and no invariant can be written until one gives way.
- **How a non-response is represented.** Until **B13**, DI8 is incomplete and no invariant covers the largest group of invitees at most points in an event's life.
- **Who anyone is.** Until **B1**, every authorization invariant is a statement of intent with no mechanism — AI1, AI2 and TI4 are currently unenforceable by construction.

### Attribution

Derived from the brief or from earlier steps: BI1–BI8, BI10–BI12, DI1, DI2, DI4–DI8, AI1–AI5, AI7, AI8, CI1–CI6, TI1–TI5.

**Proposed, not derived** — the brief is silent and these are judgement calls awaiting ratification: **BI9** (confirmed places are not taken away), **DI3** (capacity is positive), **DI9** (removal clears standing), **AI6** (the host cannot answer for an invitee), **CI7** (resubmission is inert).

---

## Step 09 — First-pass architecture

Derived from the confirmed facts (F1–F18) and working assumptions (A1–A9) only. No code, no class names, no endpoint shapes, no table names. Where an open question forces a structural choice, the choice is presented rather than made.

### What the facts already decide

Three structural questions are settled before any design judgement is applied:

- **F5** — one database with real transactional guarantees. Contention over the last place is solvable inside a single transaction. Nothing here needs queues, brokers, distributed locks, or eventual consistency.
- **A5** — events are independent. There is no cross-event coordination to design.
- **F1** — nothing exists yet. There is no legacy shape to accommodate and no migration to sequence.

Taken together, these argue for **one deployable application over one database**, with boundaries drawn inside it rather than between processes. Any distribution would be a choice made against the facts, not because of them.

### Components and responsibilities

Named by capability, not by implementation.

| Component | Owns | Responsibilities | Must not |
|---|---|---|---|
| **Host experience** | Nothing durable | Presents event creation, invitation, the aggregate view, and lifecycle actions. | Compute counts, decide eligibility, or hold authority of its own. |
| **Invitee experience** | Nothing durable | Presents one event and one answer, reached by link. | Reveal any other invitee's answer or any aggregate (F16, AI4). |
| **Request edge** | Origin and transport boundary | Accepts requests from both experiences; the only way in. | Contain domain rules. |
| **Access decision** | The mapping from a credential to an actor's authority | Establishes whether a caller acts as a host or as a particular invitee, before any domain action. | Be bypassed by any component. **Does not exist today (F2) and must be introduced.** |
| **Event lifecycle** | The event record: attributes, capacity value, host binding, closed/cancelled status | Creation, invitation, closing, cancelling. | Write allocation state. |
| **Invitation issuance** | The per-invitee means of responding | Mints one unguessable, unique means of response per invitee (F15, DI6). | Reuse, re-derive, or share one across invitees. |
| **Response and allocation** | **All allocation state** — every answer, every confirmed place, every overflow position | Records answers, allocates and releases places, promotes from overflow, enforces BI1–BI6 and CI1–CI7. | Share write authority with anything else. |
| **Finality rule** | The definition of when the record is closed | Determines whether a moment is before or after the event's start instant. | Be evaluated independently in more than one place (CI5, FF12). |
| **Aggregate view** | Nothing | Derives counts and lists from allocation state on demand (DI4). | Store a total, or hold a count that could disagree with the answers. |
| **Delivery adapter** | Nothing durable | Conveys an invitation outward. | Exist at all unless **B2** resolves toward system-owned delivery; and never run inside a transaction. |
| **Store** | Durable state | Persists everything above; provides the transactional boundary (F5). | — |

### Ownership boundaries

The single most important rule in this architecture:

> **Allocation state has exactly one owner.** Every confirmed place, every overflow position, and every recorded answer is written only by *Response and allocation*. Nothing else — not the host experience, not event lifecycle, not a background process — writes it.

The rest follow from it:

| Boundary | Rule |
|---|---|
| **Capacity** | *Owned* by event lifecycle, *read* by allocation. Allocation never changes it; lifecycle never acts on its consequences. This split is exactly where **B6** lands — a capacity change is a lifecycle write with allocation consequences, and nothing currently specifies who reconciles them. |
| **Counts** | **Owned by nobody.** Derived on demand. DI4 makes a stored total a defect rather than an optimisation. |
| **Event status** | Owned by event lifecycle. Read by allocation to decide whether answers are accepted (B7). |
| **Time** | Owned by no component. The clock is external (F14, A4). The *rule* that interprets it has one owner, so that two parts of the system can never disagree about finality. |
| **Actor authority** | Established once, at the access decision, before any domain component runs. No component re-derives who the caller is. |
| **Invitation secret** | Owned by invitation issuance. Held by the invitee thereafter. A7 means the system cannot distinguish holder from intended recipient. |

### Flow boundaries

| Boundary | Where | What crosses | Rule |
|---|---|---|---|
| **Trust — external** | Browser ↔ request edge | Everything from both actors | Both host and invitee are untrusted. F7 makes this boundary explicit and currently unconfigured. |
| **Trust — internal** | Host ↔ invitee | Aggregate data | F16 places a boundary *inside* the feature. The invitee experience must not be a restricted view of the host's data; it should not receive it at all. |
| **Authority** | Access decision ↔ everything downstream | An established actor | Nothing downstream re-interprets a credential. |
| **Transaction** | Around one event's allocation state | Answer, allocation, release, promotion | **Must be one atomic unit.** An answer that takes or frees a place, and any promotion that follows, settle together or not at all (CI1–CI3). Cross-event transactions never occur (TI1). |
| **Consistency** | Aggregate view ↔ allocation state | Counts and lists | A read sees one committed state, never a blend (CI6). Separate from *how current* it is, which is B8. |
| **External** | Delivery adapter ↔ anything outside | An invitation | Must sit **outside** the transaction and after commit. Delivery is allowed to fail without losing the invitation — this is the boundary that FF7 exposes. |
| **Schema** | Application ↔ store | Structure | F6 makes schema change implicit and simultaneous with deployment. Currently a boundary with no control on it. Step 16. |

### Key design choices

Each states the options honestly. Where marked **proposal**, it is a recommendation awaiting ratification, not a decision taken.

**DC1 — Allocation is decided in one place, inside one transaction per event.**
Facts: F5, A5. Alternatives — optimistic retry, or application-level locking — are viable but unnecessary given F5. *Proposal, well supported by the facts.* Mechanism is Step 12's business, not this step's.

**DC2 — Finality: computed condition, or stored transition?**

- *Computed* — every decision evaluates the start instant against the clock. No scheduler, no drift, no missed transition. Costs a comparison on every operation.
- *Stored* — something flips the record to final at the start time. Makes the state explicit and cheaply readable, but requires a scheduler that **F10** says does not exist, and introduces a window where the stored state is wrong.

*Proposal: computed.* F10 and F14 both point that way, and a computed rule cannot silently fail to fire. **Blocked by B5** — if promotion must still occur after finality, the two interact and neither option is clean.

**DC3 — The aggregate view is derived, never stored.**
DI4 requires it. Deriving is also correct by construction. *Proposal, with revisiting deferred to Step 13 under measured load — not before.*

**DC4 — How current the host's view is: undecided.**
Options span manual refresh, periodic polling, and server push. **F4** makes held-open connections expensive on this stack, so push is the costliest option here and the one the facts least support. **Blocked by B8**; no proposal, because "live" has no agreed meaning yet.

**DC5 — How a host is recognised: undecided.**
Options: a host link symmetric to the invitee's (consistent with F15, weakest); a lightweight account; an external identity provider (largest addition, contradicts nothing but adds the most). **F2** means any of them is new construction. **Blocked by B1**, and it is the decision with the widest structural blast radius in the document — AI1, AI2, TI4 and the entire internal trust boundary depend on it.

**DC6 — Invitee authority rests on possession of an unguessable link.**
Follows from F15 and A7 with no alternative available that does not contradict F15. The architectural consequence: the link's unguessability *is* the access control. Properties and lifetime belong to Step 11.

**DC7 — Delivery, if owned, is an adapter outside the transaction.**
**Blocked by B2.** Regardless of resolution, the boundary rule holds: recording an invitation and delivering it are separate, and the first must not depend on the second.

**DC8 — Where invariants are enforced: a genuine tension.**
**F5** offers real database constraints — the most reliable place to enforce uniqueness and non-negative capacity. **F6** generates schema implicitly, making such constraints awkward to express and impossible to version. *Proposal: enforce in application logic as the primary mechanism, with the tension recorded rather than resolved here*, because choosing database constraints first would make F6 a blocker at Step 16 instead of a noted risk.

**DC9 — One frontend application, two entry paths.**
**F8** means routing must be introduced either way. Two separate applications would duplicate the boundary for no stated benefit. *Proposal.*

### What this architecture deliberately omits

Named so their absence is a decision rather than an oversight:

- **No queue or message broker.** Nothing in the facts requires asynchronous work except delivery, which is unresolved (B2) and small.
- **No cache.** DI4 wants derivation; no load figure exists to justify anything else.
- **No separate services.** A5 gives no seam worth a process boundary.
- **No event sourcing or audit log.** *Worth noting as a gap rather than a virtue* — IS3 changes a person's standing with no human behind it, and nothing currently records that it happened. Step 11 and Step 14 should decide whether that is acceptable.

### Structural decisions blocked on open questions

| Decision | Blocked by | Consequence of proceeding without an answer |
|---|---|---|
| How a host is recognised (DC5) | **B1** | Every authorization invariant stays unenforceable. |
| How current the host's view must be (DC4) | **B8** | The read path cannot be sized or shaped. |
| Whether finality and promotion interact (DC2) | **B5** | BI5 and BI7 remain in contradiction; no finality design is correct. |
| Whether delivery is owned (DC7, B2) | **B2** | The delivery adapter and FF7 are either in scope or absent entirely. |
| Who reconciles a capacity change (B6) | **B6** | The lifecycle/allocation ownership split has an undefined case at its centre. |

---

## Step 10 — Data ownership and state model

### The central modelling decision

The brief speaks of an RSVP as one thing that can be "Yes", "waitlisted" or "confirmed". Those are not one thing. They are two, and conflating them is what makes B3, B4 and B6 feel unanswerable:

- **The answer** is what the invitee intends. It is theirs, it is one of three values (F11), and only they change it.
- **The standing** is whether that intent currently holds a place. It is the system's, it is never chosen by anyone, and it changes without the invitee acting (IS3).

*Proposal, not derived.* Separating them is a judgement call. The justification: an invitee who was promoted from overflow never changed their answer, and an invitee whose event lost capacity never changed theirs either. Modelling both as a single value forces the system to silently rewrite what someone said. Every entry below assumes the separation; if it is rejected, this step needs rewriting.

### Entities and stateful concepts

| Concept | Source of truth | Mutated by | How it is read | Derived state |
|---|---|---|---|---|
| **Event attributes** — title, description, start instant, location | Stored, one record per event | Event lifecycle, on creation. Whether later edits are permitted is unstated. | By the host in full; by a link holder in the subset needed to answer (F16) | None |
| **Capacity** | Stored on the event | Event lifecycle only | Read by allocation on every claim | "At capacity" is derived, never stored |
| **Event status** — accepting / closed / cancelled | Stored on the event | Event lifecycle, by host action only (AI2) | Read by allocation before accepting any answer | Combines with finality to give observable state — see below |
| **Invitation** | Stored, one per invitee per event | Event lifecycle, on inviting. Removal unstated (**B4**, DI9) | Read to resolve a link to an invitee | The set of invitations defines who *could* answer |
| **Means of responding** (the link secret) | Stored with the invitation | Invitation issuance, once. Never re-derived | Presented by the link holder; never displayed back | None. Its unguessability is the access control (DC6) |
| **Answer** | Stored, at most one per invitation (BI3) | **The invitee alone.** Nothing else writes it (AI6, proposed) | By that invitee; in aggregate by the host | None |
| **Standing** — holds a place / waiting / neither | Stored, at most one per invitation | **Response and allocation alone** (Step 09 ownership rule) | By that invitee for themselves; in aggregate by the host | None. But *which* standing is legal is a function of the answer and capacity |
| **Overflow order** | Stored, per event, as a total order over waiting invitations (BI6, DI5) | Response and allocation alone. No actor may set it (AI7) | Not exposed to invitees. Host visibility unstated | Position is stored; "who is next" is derived from it |
| **Finality** | **Not stored.** The event's start instant plus the clock | Nothing. It is not mutated, it occurs (F14) | Evaluated by the one owner of the finality rule (Step 09) | Entirely derived — under DC2's proposal |
| **Counts and attendee list** | **Not stored** (DI4) | Nothing | Derived on demand from answers and standings | Entirely derived |
| **Non-responders** | **Not stored** | Nothing | Derived: invitations having no answer | Entirely derived — **B13** decides whether this is even representable |
| **Host identity** | **Undefined** | — | — | **Blocked by B1.** Today there is no source of truth for who a host is; F13's "one host per event" has nothing to point at |

### Event state model

Observable event state is **not a single value**. It is the product of a stored status and a derived condition:

**Stored status:** `Accepting` → `Closed` (host closes; the event still happens) or `Cancelled` (the event will not happen).
**Derived condition:** before the start instant, or after it.

| Stored status | Before start | After start |
|---|---|---|
| **Accepting** | Answers accepted; allocation active | Record final. Answers refused (BI7) |
| **Closed** | Answers refused; the event still occurs | Record final |
| **Cancelled** | Answers refused; the event will not occur | Final and moot |

Transitions: `Accepting → Closed`, `Accepting → Cancelled`, and — per **B7** — possibly `Closed → Accepting`. Nothing states whether cancelling is reversible; **BI12** holds that crossing the start instant is not.

> Modelling finality as a stored fourth status would be a mistake: it would need something to write it (F10 says nothing exists to), and it could then be wrong. Keeping it derived is what makes BI12 free.

**Forks:** **B7** — reversibility, and what a cancelled event's answers mean. **B12** — what the record means once final.

### Answer state model

Values: `Yes`, `No`, `Maybe` (F11), plus **the absence of any answer** — which F11 does not provide for and **B13** must resolve.

- From no answer, any of the three may be given.
- Any value may be replaced by any other while the event is accepting and before finality (G8, BI8).
- After finality, none change (BI7).
- An answer is never withdrawn back to "no answer". Absence is a starting condition, not a destination. *Proposal — nothing states it.*

### Standing state model

Values: `None`, `Holding` (occupies a place), `Waiting` (in overflow, at a position).

| From | To | Cause |
|---|---|---|
| None | Holding | An answer that claims a place, with room available |
| None | Waiting | An answer that claims a place, at capacity |
| Waiting | Holding | Promotion, when a place is released (IS3) — **system-initiated, no actor** |
| Holding | None | The invitee's answer stops claiming a place |
| Waiting | None | As above, from overflow |
| Holding | Waiting | **Only if capacity is lowered below the held count.** Forbidden by BI9 as proposed; possible only if **B6** permits it |

**Forks:** **B3** decides which answers claim a place. **B4** decides which changes release one. **B10** decides the order Waiting resolves in. **B5** decides whether the `Waiting → Holding` transition can still fire at or after finality — and it is the only transition in this document that two stated rules disagree about.

### Legal combinations

The answer and the standing are separate, but not independent. Which pairs are legal is exactly what **B3** decides:

| Answer | Standing if Maybe claims a place | Standing if Maybe does not |
|---|---|---|
| No answer | None | None |
| `Yes` | Holding or Waiting | Holding or Waiting |
| `No` | None | None |
| `Maybe` | Holding or Waiting | **None** |

Any pair outside the chosen column is a corruption, not a state — worth saying plainly, because it is the check that catches most allocation bugs.

### Derived state, and the rule about it

Derived, never stored: **finality**, **at-capacity**, **all counts**, **the attendee list**, **the non-responder set**, **who is next in overflow**.

> **Nothing derived is stored.** Under DI4 a stored total that disagrees with the answers is a defect, and the only way it cannot disagree is not to exist. If measurement later forces a cached count (Step 13), it must be introduced as an explicit, justified exception to this rule — not as an optimisation nobody noticed.

### What is deliberately not modelled

- **History.** No record of what an answer was before, who was promoted, or when. Standing changes without the invitee acting and nothing evidences it. *A gap, not a virtue* — carried from Step 09 to Steps 11 and 14.
- **Delivery state.** Whether an invitation was sent, delivered, or failed. Exists only if **B2** resolves toward owned delivery — and its absence is exactly what makes FF7 undetectable.
- **Retention.** Invitations hold personal data (A8) and nothing states how long any of it lives, or what happens to it after the event.
- **Host-to-invitee association across events.** A6 and A5 together mean an invitee is scoped to one event; the same person invited twice is two invitations (**B11**).

### Blocked — state model

| What cannot be modelled | Blocked by |
|---|---|
| Whether "no answer yet" is a representable value or an absence | **B13** |
| Which answers claim a place — the legal-combinations table has no single column | **B3** |
| Which changes release a place | **B4** |
| Whether `Waiting → Holding` survives finality | **B5** |
| The ordering of overflow | **B10** |
| Whether `Holding → Waiting` can occur at all | **B6** |
| Any source of truth for host identity | **B1** |

---

## Step 11 — Trust boundaries and security notes

First pass. Properties and boundaries only — no mechanisms, no libraries, no configuration.

### Where trust enters

| # | Entry point | What arrives | Trusted? | Notes |
|---|---|---|---|---|
| **TB1** | Browser → request edge | Every action by both actors | **No** | Both experiences are untrusted. Neither owns durable state (Step 09), so neither can be relied on to have checked anything. |
| **TB2** | A link presented by its holder | The entire authority of one invitee | **Conditionally** | The secret *is* the credential (DC6). A7 accepts that the holder may not be the intended person. Trust rests wholly on unguessability. |
| **TB3** | A claim to be a host | The authority to see and act on everything about an event | **Nothing checks it** | **B1.** This is not a weak boundary; it is an absent one. |
| **TB4** | The clock | Whether the record is still mutable | **Implicitly** | F14 and A4 make an unspecified time source an authority over who may act. A wrong clock changes permissions. Rarely modelled as a trust input; here it is one. |
| **TB5** | The store | Durable state | **Yes** | Inside the boundary, same deployment (Step 09). |
| **TB6** | Delivery adapter → third party | Invitee personal data, leaving the system | **Outbound trust** | Exists only if **B2** resolves toward owned delivery. Data crossing outward is a boundary even when nothing comes back. |
| **TB7** | Repository and configuration | Credentials and settings | **Currently misplaced** | **F9** — a third-party key is committed. The supply chain is a trust entry point, and this one is already compromised. |

### Where authorization must be enforced

**The rule:** authority is established once, at the access decision, before any domain component runs (Step 09). Nothing downstream re-derives it, and nothing upstream — neither client experience — is relied on to have applied it.

| Operation | Required authority | Invariant | Enforceable today? |
|---|---|---|---|
| Create an event | An identified party | F13 | **No — B1** |
| Invite people | Host of that event | AI2 | **No — B1** |
| View the aggregate | Host of that event | AI1 | **No — B1** |
| Close or cancel | Host of that event | AI2 | **No — B1** |
| Change capacity (if permitted) | Host of that event | AI2, B6 | **No — B1** |
| Read one answer and its event details | Holder of that invitation's link | AI3 | Yes |
| Write one answer | Holder of that invitation's link | AI3, AI5 | Yes |
| Set another invitee's answer | **Nobody** | AI6 (proposed) | Enforced by the operation not existing |
| Reorder overflow, choose who is promoted | **Nobody** | AI7, N10 | Enforced by the operation not existing |

> Two rows are enforced by absence. **An operation that does not exist cannot be misused**, and it is the strongest control available — stronger than any check. Both AI6 and AI7 depend on that absence holding, which is why N10 was written to require a stated amendment rather than a quiet addition.

**A consequence of F16 worth stating plainly:** the invitee experience must not receive aggregate data at all. Filtering it in the client is not enforcement — the boundary is what is sent, not what is displayed.

### Where tenant scope matters

There is no tenancy (Step 08). The scope unit is the **event**, and below it the **single invitation**.

**The rule that matters most here:**

> Scope is derived from the credential, never from an identifier the caller supplies.

A link determines *which* invitation and therefore which event. A host credential — once B1 is answered — determines which events. A caller naming an event, an invitation, or another invitee must never thereby gain access to it. Every one of TI1–TI5 fails the moment scope is taken from a request parameter rather than from the credential, and this is the single likeliest way this design gets implemented insecurely.

### Sensitive data

| Data | Why sensitive | Exposure path | Current state |
|---|---|---|---|
| **Invitee email addresses** | Personal data (A8) | Held by the host; visible in the aggregate; leaves the system if B2 is owned delivery | No retention rule, no erasure path, no stated obligations |
| **Link secrets** | Credential material (DC6) | Appears wherever a URL is recorded — browser history, referrers, logs, shared devices, forwarded messages | No revocation, no expiry, no rotation stated (**FF5**) |
| **Who declined, who is waiting** | Socially sensitive; F16 exists because of it | The aggregate; any leak of the host view | Protected only by AI1/AI4, which are unenforceable today |
| **Event details — time, location** | Moderately sensitive | Carried by any forwarded link (**FF6**) | Accepted consequence of A7 |
| **The committed third-party key** | A live credential | Every clone, and the whole of git history | **F9 — must be treated as compromised** |

### Privileged operations

| Operation | Why privileged | Who holds it |
|---|---|---|
| Close or cancel | Affects every invitee at once; reversibility unstated (**B7**) | Host |
| Invite | Causes personal data to be stored, and to be sent outward if B2 | Host |
| Change capacity | May displace confirmed people (**B6**, BI9) | Host |
| View the aggregate | The only view of everyone's answers | Host |
| **Promotion** | Changes a person's standing, with consequences for them | **Nobody** |

> **The most privileged operation in this system is performed by no one.** Promotion (IS3) changes who holds a place, is triggered by another person's action, and has no actor, no authorization check, and — per Step 10 — no record that it occurred. That is not a flaw in the brief; it is what "automatic" means. It does mean accountability for it has to be designed deliberately rather than inherited.

### Security notes

Numbered for later reference. Those marked **proposal** are judgement calls, not derived.

| # | Note |
|---|---|
| **S1** | **The link is a bearer credential.** Everything true of a password is true of it, except that it travels in a URL — so every place a URL is recorded is a place the credential is recorded. Its unguessability is the entire control. |
| **S2** | **No revocation exists.** Nothing in any input allows a link to be invalidated. Once issued it is valid until the record is final, and after a forward it cannot be taken back (**FF5**, **FF6**). *Whether revocation is needed is a decision, not an oversight to fix silently.* |
| **S3** | **Nothing is attributable.** With no history (Step 10), a dispute over who took the last place has no answer, and a standing change cannot be distinguished from a defect. *Proposal: an append-only record of standing changes is the minimum that makes IS3 accountable.* |
| **S4** | **F9 is a live incident, not a design item.** The committed key must be rotated at its provider. Removing it from the file does not remove it from history, and A1 (that the integration is unrelated) does not make the credential less exposed. |
| **S5** | **Enumeration collapses the whole model.** If invitation identifiers are sequential or short, TB2 and every authorization invariant fall at once. *Proposal: unguessability is a stated requirement of invitation issuance, not an implementation detail.* |
| **S6** | **The origin boundary is unconfigured (F7).** It has to be configured to something; a permissive setting would expose the host view to any origin that can reach it. |
| **S7** | **Personal data has no lifecycle.** A8 assumes no regime applies. If one does, retention, erasure and consent obligations all attach to data this design currently keeps indefinitely. |
| **S8** | **B1 is the largest hole in the document.** Today, six of nine privileged operations have no gate. This is not a hardening item to schedule later — until it is answered, the host boundary does not exist. |
| **S9** | **Finality depends on an untrusted clock.** **B9** leaves the instant undefined and A4 assumes one authoritative clock. A wrong or skewed clock silently changes who may still act (**FF12**). |
| **S10** | **No rate limiting is stated anywhere.** Link guessing and response flooding are both unbounded. *Proposal: name it as a requirement now; it is far harder to retrofit around a bearer-credential model.* |

### Blocked or deferred

| Item | Status |
|---|---|
| Host authentication and session handling | **Blocked by B1.** Everything in "where authorization must be enforced" waits on it. |
| Whether links expire or can be revoked | Undecided (S2). Not stated in any input. |
| Whether an audit record exists | Undecided (S3). Proposed, not adopted. |
| Transport security | Not addressed. Nothing in the inputs describes deployment; **O5** says the system runs on a workstation. Belongs to Step 16. |
| Obligations attaching to personal data | Rests on **A8**. If A8 is wrong, this section is materially incomplete. |

---

## Step 12 — Concurrency and correctness notes

First pass. Properties and hazards, not mechanisms.

### The shape of the problem

Concurrency here is unusually concentrated. Almost all of it is contention over **one event's allocation state**, and A5 means there is no cross-event coordination at all. Reads are wide and frequent; writes are narrow and rare. **F5** puts the hard part inside reach of a single transaction.

The risk is therefore not that this is difficult. It is that it looks easy enough to be written without thinking, and every hazard below is invisible in single-user testing.

### Duplicate requests

| Hazard | Effect | Required property |
|---|---|---|
| The same answer submitted twice — double click, impatient retry, resent request | A second overflow entry, or a second place claimed | Applying an answer identical to the current one changes nothing (**CI7**) |
| A resubmitted `Yes` processed as *release then claim* | **The invitee loses their place and returns at the back of overflow** — punished for retrying | A change is evaluated against current state; an unchanged answer never releases anything |
| Two submissions of *different* answers, close together | Either may win; both may partially apply | Each settles atomically; the outcome is one of the two submitted (**CI4**) |

> **No request-identity mechanism is stated anywhere.** *Proposal: derive inertness from comparing the submitted answer to the stored one, rather than from tracking request identifiers.* It needs nothing new, and it is correct even for a retry the system never saw the first time.

### Concurrent updates

**The named scenario — two acceptances, one remaining place.** Both requests read "one place free". Both conclude they may claim it. Both write. Two people hold one place, and **BI1**, **CI1** and **CI2** all fail at once. Nothing recovers this afterwards, because neither request did anything wrong in isolation.

> **The requirement, stated once:** the capacity check and the resulting write are not separable. They are one indivisible decision, per event.

*Proposal on how to get it:* serialize allocation decisions per event. A5 bounds contention to a single event, capacities are small, and F5 makes this cheap. The alternative — allow the conflict and let a constraint reject the loser — is also sound but pushes correctness into retry handling. Either is defensible; **what is not defensible is checking and writing as separate steps.**

Other collisions on the same state:

| # | Collision | Invariant at risk |
|---|---|---|
| **X1** | A release and a new claim arrive together — the freed place is given to a promoted invitee *and* to the newcomer | BI1, CI3 |
| **X2** | Two releases promote the same waiting invitee twice | CI3, DI5 |
| **X3** | The host closes the event while an answer is in flight | BI8, B7 |
| **X4** | Capacity is lowered while claims are being made | BI9, **B6** |
| **X5** | One invitee answers from two devices at once | CI4, **B14** |
| **X6** | Two different invitees revise simultaneously — independent answers, but both touch the same capacity accounting | BI1 |

X6 is the one most often missed: the answers are unrelated, but the *places* are not.

### Stale reads

- **The aggregate must be read as one coherent state.** Counting confirmed people, then counting those waiting, as separate reads can yield a total that never existed — a promotion landing between them appears in both or neither (**CI6**).
- **Coherence and freshness are different properties.** B8 governs how current the host's view is. CI6 governs whether it is *self-consistent*. A view may be a minute stale and still correct; it may be instantaneous and still incoherent.
- **A read never authorizes a write.** An invitee shown "places available" may submit into a full event; the decision is made again at write time and may differ. The read is advisory, always.
- **A stale read can mislead without violating anything.** A host sees nine of ten places taken and closes the event; the tenth answer was already in flight. No invariant breaks, and the host is still surprised. Worth stating because it is a real failure that no correctness property catches.

### Out-of-order events

| # | Hazard | Notes |
|---|---|---|
| **O-1** | A request begins before the start instant and commits after it | **FF1.** Which moment governs? *Proposal: the instant is evaluated once, at the decision point, and that outcome holds for the whole operation* — so an operation never half-applies across the boundary. |
| **O-2** | Promotion firing at or after finality | **B5.** Not a race — a contradiction. See below. |
| **O-3** | Two revisions from one invitee crossing in flight | **B14**, newly raised. BI4 says the most recent answer wins, and the brief never says whether that means most recently sent or most recently received. |
| **O-4** | An invitation delivered after the event is cancelled | Outbound side effects reflect a state that has since changed. Only exists if **B2** resolves toward owned delivery. |
| **O-5** | Two evaluations of finality disagreeing | **FF12.** Impossible under A4; guaranteed the moment A4 is false. |

> **O-2 deserves its own line.** B5 is not a concurrency problem and no amount of serialization fixes it. BI5 says nobody waits while a place is free; BI7 says nothing changes after finality. When a place is released at the boundary, **both cannot hold**. This is a specification contradiction wearing a race condition's clothing, and it is resolved by deciding, not by locking.

### Worker retries

**There are no workers in the proposed design**, and that is a deliberate consequence of DC2: finality is computed, so nothing runs on a schedule to make it happen. Retries therefore matter in exactly two places.

- **Delivery (only if B2 is owned).** A retry must not create a second invitation or mint a second link secret — two credentials for one invitee, or an earlier one silently invalidated. The retried thing is the *sending*, never the issuing.
- **Promotion in bulk.** If capacity is raised by several places (**B6**), several people are promoted. That is one indivisible step, not a loop that may stop halfway.

**The cost of the alternative, recorded honestly:** if DC2 were reversed and finality became a scheduled transition, a missed or failed run would leave the record mutable after its start time — a correctness failure caused entirely by infrastructure. This is the strongest argument for the computed approach, and it belongs here rather than in Step 09.

### Side effects that could violate invariants

| # | Side effect | Hazard |
|---|---|---|
| **SE1** | Delivery performed inside the transaction | A rollback leaves an invitation sent for a record that does not exist. Step 09's boundary rule exists for this. |
| **SE2** | Delivery performed after commit, then the process dies | The invitation exists and was never sent — **FF7**, and with no delivery state (Step 10) it is undetectable. At-most-once and at-least-once are both available; **neither is currently observable**. |
| **SE3** | Caching a derived count | Forbidden by DI4. Any cache is a second source of truth and drifts under exactly the concurrency above. |
| **SE4** | Recording a URL containing a link secret in a log | A security consequence of an operational side effect (**S1**). |
| **SE5** | A future notification on promotion | Out of scope today (N3), but it would be a side effect bound to a state change made by nobody — inheriting every hazard in this section. Noted so it is not added casually. |

### Scenarios that should be exercised deliberately

Named so they are not left to be discovered:

1. Two acceptances for one remaining place.
2. The same answer submitted twice in rapid succession.
3. A release and a claim arriving together.
4. An answer in flight when the host closes the event.
5. Capacity lowered while claims are being made (if B6 permits).
6. A request that crosses the start instant mid-operation.
7. Two revisions from one invitee, out of order.
8. A delivery failure after a successful commit.

### Blocked — concurrency

| What cannot be settled | Blocked by |
|---|---|
| Whether promotion survives finality — a contradiction, not a race | **B5** |
| What contends for a place, and what releases one | **B3**, **B4** |
| The instant against which the boundary is judged | **B9** |
| Whether displacement races exist at all | **B6** |
| Which of two crossing revisions wins | **B14** |

---

## Step 13 — Scalability and multi-tenancy notes

### The honest starting point

**No input contains a single number.** Not how many events, not how many invitees, not how often a host looks, not how many people answer at once. Nothing in the brief, the README, or the repository establishes scale.

This section therefore reasons about the **shape** of growth, not its magnitude, and names what would have to be measured before any structural change could be justified. No thresholds are given, because any threshold stated here would be invented.

### Growth axes

| # | Axis | What it drives |
|---|---|---|
| **GA1** | Number of events over time | Total stored data; nothing else, since events are independent (A5) |
| **GA2** | Invitees per event | The cost of every aggregate view — the derived count is proportional to it (DC3) |
| **GA3** | Concurrent responses to **one** event | Contention on that event's allocation state (Step 12) |
| **GA4** | How often a host views the aggregate | Read volume, multiplied by whatever **B8** turns out to mean |
| **GA5** | Events active at the same time | Shared resource pressure across otherwise independent events |
| **GA6** | Capacity size, and therefore overflow length | Ordering work, and the size of lists presented |
| **GA7** | Elapsed time with no retention rule | Unbounded accumulation of personal data (**S7**) |
| **GA8** | Number of hosts | Only meaningful once **B1** is answered |

### The axis that matters differently from the rest

Every axis above is a performance concern except one.

> **GA3 is the only axis that threatens correctness**, and it does not respond to scale-out. Allocation for one event is serialized (Step 12), so contention is a property of *that event*, not of the system. A thousand quiet events cost nothing; one popular event with a tight capacity is the hot spot. **Adding capacity to the system does not make the last place less contended.**

This is worth internalising because the instinctive remedy — more instances — makes things worse rather than better here, for reasons in the next section.

### Likely first bottlenecks

In the order they would plausibly arrive:

| # | Bottleneck | Driven by | Why it comes first |
|---|---|---|---|
| **BN1** | Recomputing the aggregate on every host view | GA2 × GA4 | Counts are derived by design (DI4, DC3). Cost scales with invitees *and* view frequency. If **B8** resolves toward polling, GA4 multiplies without anyone choosing it. |
| **BN2** | Threads blocked behind one event's serialized allocation | GA3 + **F4** | The stack is thread-per-request. Requests queued on a hot event hold threads, and a **single popular event can exhaust the pool and degrade every other event** — despite A5 making them logically independent. |
| **BN3** | Connections held open for a live view | **B8** + **F4** | Push on a blocking stack consumes a thread per viewer. This is why DC4 noted push as the option the facts least support. |
| **BN4** | Overflow reordering on removal | GA6 + **B10** | Only if positions are kept contiguous. DI5 requires uniqueness, not contiguity — a distinction that decides whether this bottleneck exists at all. |
| **BN5** | Unbounded data accumulation | GA7 | Slow, undramatic, and unbounded. Coupled to S7 rather than to performance. |

### What is sufficient now

Given no numbers, the following are adequate and should **not** be changed preemptively:

- **One application instance, one database.** F5 supports it and A4 depends on it.
- **Derived counts, no cache.** DI4 makes a cache a correctness liability, and no measurement exists to justify accepting that liability.
- **Per-event serialization of allocation.** Contention is bounded to one event; the mechanism is cheap.
- **No queue, no broker, no separate services.** Nothing in the facts asks for them (Step 09).

> Each of these is *sufficient because nothing is known*, not because it is proven adequate. That is an honest position, and it is different from claiming the design scales.

### What would require architectural change

These are not tuning exercises. Each invalidates something the design currently rests on.

| Change | What breaks | Why it is not incremental |
|---|---|---|
| **More than one application instance** | **A4** — the single-clock assumption | Finality is computed against a clock (DC2). Two instances mean two clocks, so **FF12 and O-5 stop being theoretical**. A time authority must be defined before a second instance exists. Horizontal scaling here is an assumption change, not a configuration change. |
| **Caching or storing counts** | **DI4** | A second source of truth appears, and it drifts under exactly the concurrency of Step 12. Admissible only as an explicit, justified exception with a stated invalidation rule — never as an unnoticed optimisation. |
| **Push-based live updates** | The concurrency model, via **F4** | On a thread-per-request stack, held connections are threads. This likely means changing how the application handles concurrency, not adding a feature. |
| **Read replicas** | **CI6** | Coherent aggregate reads are currently free because there is one state. Replicas make staleness systemic rather than incidental, and B8's answer would need to become a guarantee rather than a preference. |
| **Sharding by event** | Nothing | **The one change the design is already shaped for.** A5 means no invariant spans two events, so the event axis is a clean partition. Worth recording as a property the design has, not a plan to use it. |

### Multi-tenancy

**There is no tenancy.** No organizations, teams or workspaces exist in any input, and N4 excludes them. The isolation unit is the **event**, and below it the single invitation (Step 08).

Two things follow that are easy to miss:

**Isolation today is logical, not resource-level.** TI1–TI5 guarantee that one event's *data* never touches another's. They guarantee nothing about shared threads, connections, or database capacity — and BN2 shows one busy event degrading all the others. **A noisy-neighbour problem exists without there being any neighbours in the tenancy sense.**

**If organizational tenancy ever arrives, it is one decision, not several.** N4, N9, A5 and every TI invariant fall together. In scale terms specifically: org-level dashboards mean cross-event queries, shared capacity means cross-event invariants, and both destroy the clean event partition that makes sharding free today. The cost of adding tenancy is not the tenancy — it is losing A5.

### What would have to be measured

To convert any of this from speculation into a decision:

- The distribution of **invitees per event** — not the average, the tail. BN1 is driven by the largest events, not the typical one.
- **Peak concurrent responses to a single event** — the only figure that bears on GA3 and BN2.
- **How often hosts actually view the aggregate**, which is also the practical answer to B8.
- **Time to derive one aggregate**, which is the trigger condition for revisiting DC3.

Until these exist, changing the architecture for scale would be guessing with extra steps.

### Blocked — scalability

| What cannot be settled | Blocked by |
|---|---|
| The read-path shape and its cost | **B8** — "live" still has no meaning, and it multiplies GA4 |
| Whether overflow reordering is a bottleneck | **B10** — and whether positions are contiguous |
| Whether host count is a growth axis at all | **B1** |
| Retention, and therefore whether GA7 is bounded | **A8**, **S7** |

---

## Step 14 — Risks and failure notes

Ranked by what actually hurts, not by category order. **Detectability** is carried as its own column, because in this design it is the axis that separates an inconvenience from a liability.

### Correctness risks

| # | Risk | Impact | Detectable? |
|---|---|---|---|
| **CR1** | Capacity exceeded because the check and the write were separable (Step 12) | Two people hold one place. Discovered **at the door**, not in the system | **No.** Nothing in the design notices; the record looks consistent |
| **CR2** | The **B5** contradiction is shipped unresolved | An implementer silently picks one of two conflicting rules. Behaviour matches neither stated intention | Only by someone reading both rules together. No test catches a contradiction |
| **CR3** | The finality instant is misjudged — wrong timezone, a daylight-saving shift (**B9**) | The record locks hours early or late. Invitees are refused while the host still expects answers | Late, and usually by complaint |
| **CR4** | A retry is processed as release-then-claim | The invitee **loses their place for double-clicking** and reappears at the back of overflow | Rarely. Blamed on the user |
| **CR5** | Answer and standing are conflated (if Step 10's proposal is rejected) | The system rewrites what a person said. "I never declined" becomes unanswerable | **No** — and with no history (S3), unfalsifiable in both directions |
| **CR6** | A non-response is treated as a `No` (**B13**) | The host under-counts, or counts silence as refusal, and acts on it | No. It looks like an answer |
| **CR7** | Two crossing revisions resolve by arrival rather than intent (**B14**) | An invitee's older answer wins | No |
| **CR8** | A torn aggregate read (**CI6**) | The host acts on a total that never existed | No |
| **CR9** | One released place promotes two people, or none (**CI3**) | Over-admission, or someone waits beside an empty place | Partially — the count drifts from capacity |

### Dependency risks

| # | Risk | Impact | Notes |
|---|---|---|---|
| **DR1** | Delivery is adopted (**B2**) and invitations do not arrive | Silent non-participation. **FF7** makes undelivered indistinguishable from ignored | Deliverability, bounces and spam filtering sit outside the system's control entirely |
| **DR2** | **Automated fetchers visit invitation links** — mail scanners, chat link previews, security gateways | If responding is reachable by simply visiting a URL, **a preview bot can answer on an invitee's behalf**. A7 means the system cannot tell | Concrete, common, and a direct consequence of the link-as-credential model (DC6) |
| **DR3** | A1 is wrong and the WhatsApp integration is real (**Q2**) | An external API, its availability, rate limits and cost all enter the design — with an already-exposed key (F9) | Would also contradict the brief's "by email" |
| **DR4** | Hibernate infers the production schema (**F6**) | A library, not a person, decides what the database looks like | An implicit dependency that is never reviewed |
| **DR5** | Single database instance, no backups (F10, O1) | Total, unrecoverable loss of every event and answer | The most severe single-point failure in the system |
| **DR6** | The clock is wrong or drifts (**TB4**) | Finality is evaluated incorrectly, changing who may act | No alarm exists for it |
| **DR7** | An identity provider is adopted to resolve **B1** | A new external dependency on the critical path of every host action | Not a risk today; a risk created by the fix |

### Operational risks

| # | Risk | Impact | Notes |
|---|---|---|---|
| **OR1** | **An outage in the hours before an event starts** | Invitees cannot answer, the start time passes, and the record locks. **The lost window is unrecoverable** — there is no way to respond afterwards | Design-specific: for most systems downtime defers work; here it destroys it |
| **OR2** | Schema change and deployment are one event (**O2**) | A bad inference is discovered in production, with no rollback | Compounded by F10 — no rehearsal environment exists |
| **OR3** | No monitoring, alerting or logging described (F10) | Failures are noticed by users or not at all | Compounds every "No" in the detectability columns above |
| **OR4** | The committed key (**F9**) | Already exposed in history and in every clone | **A live incident, not a future risk** (S4) |
| **OR5** | No rate limiting (**S10**) | Link guessing and response flooding are unbounded | Hard to retrofit around a bearer-credential model |
| **OR6** | No backups (**O1**) | See DR5 | Listed twice deliberately: it is both a dependency and an operational failure |
| **OR7** | Personal data accumulates with no retention rule (**S7**) | A growing liability that nothing reduces | Invisible until someone asks for erasure |
| **OR8** | Production behaviour is untested — the system has only run on a workstation (**O5**) | Every operational claim in this document is unverified | Honest limitation, not a defect |

### Assumption failures

Step 06 records what each assumption is and what breaks if it is wrong. What follows is the part that matters in production: **how you would find out**, and how expensive the discovery is.

| Assumption | Early warning sign | Cost when it fails |
|---|---|---|
| **A4** — one authoritative clock | Any discussion of a second instance, a load balancer, or a container replica | **High.** Finality becomes inconsistent (FF12). Scaling out silently breaks correctness rather than failing loudly |
| **A7** — the link holder is the invitee | A host reports that someone answered who was never invited | **High**, and unfixable after the fact with no audit trail (S3) |
| **A3** — one answer equals one place | A request to allow plus-ones or guests | **High.** Capacity stops being a count of answers; G3's arithmetic collapses |
| **A8** — no privacy regime applies | The first erasure or export request | **High.** There is no deletion path, and data was never bounded (S7) |
| **A2** — the README text is the right brief | Anyone describing a feature this document does not contain | **Total**, but discovered early and cheaply |
| **A1** — the WhatsApp config is unrelated | Any requirement that invitations go by WhatsApp | Moderate. Invalidates N11, DR3 becomes live |
| **A5** — events are independent | Shared capacity, org dashboards, or delegated hosting | **High.** Every TI invariant, the free sharding axis, and N4/N9 fall together |
| **A6** — nobody invites themselves | A request for open or public events | Moderate. N5 falls; the invitation-based trust model needs rework |
| **A9** — conceptual depth suffices | An implementer asking for decisions this file defers | Low. Resolved by answering the open questions |

### The failures this design cannot see

Grouped deliberately, because they share one property:

- **An invitation that was never delivered** (FF7) — indistinguishable from an invitee who ignored it.
- **A standing change** (IS3) — no record that it happened, who it affected, or why.
- **Over-admission** (CR1) — the resulting state looks internally consistent.
- **A forwarded link being used** (FF6, DR2) — accepted by design under A7.
- **Clock drift** (DR6) — no alarm, and the symptom is a permissions change.

> **The defining weakness of this design is not fragility — it is silence.** Most of the ways it can be wrong produce no error, no exception, and no contradictory record. That is what makes S3's proposed history and OR3's absent monitoring worth more here than they would be in a system that fails loudly.

### Highest-priority risks

If only a few things are addressed before building:

1. **OR4 / F9** — rotate the committed credential. Already exposed; not a design matter.
2. **B5 (CR2)** — resolve the contradiction. No implementation can be correct while two stated rules conflict.
3. **CR1** — treat the capacity check and write as one indivisible decision. The single most consequential implementation property in the document.
4. **B9 (CR3)** — define the finality instant. Cheap to decide now, expensive to discover wrong.
5. **DR5 / OR6** — no backups. The only failure here that is unrecoverable rather than merely bad.

### Blocked — risks

| What cannot be assessed | Blocked by |
|---|---|
| Whether delivery risks exist at all | **B2** |
| The true severity of CR6 and CR7 | **B13**, **B14** |
| Whether displacement failures are possible | **B6** |
| Any likelihood estimate whatsoever | **Q4** and the absence of scale figures (Step 13) — this section ranks by impact and detectability only, never by probability |

---

## Step 15 — Alternatives and tradeoffs

### Tiebreakers

**ORG4** means there is no business motivation to appeal to when two options trade against each other. Rather than let an unstated preference decide, the criteria used here are declared first. *These are proposals; changing them changes the conclusions.*

| # | Tiebreaker | Why |
|---|---|---|
| **TB-a** | Prefer a design where a broken invariant is **impossible** over one where it is merely unlikely | Step 14 shows the worst failures are undetectable; prevention beats correction when correction never gets triggered |
| **TB-b** | Prefer designs that **fail loudly** | Silence is this design's defining weakness (Step 14) |
| **TB-c** | Prefer **reversible** choices | With Q4 unanswered, nothing justifies committing to an irreversible structure |
| **TB-d** | Prefer **fewer moving parts** | F10 — no monitoring, CI, or operational capability exists to look after them |
| **TB-e** | When two options are close, prefer the one that **preserves A5** | Event independence is what makes the design changeable and shardable (Step 13) |

---

### The proposed design, stated for comparison

**P — Transactional core, derived everything.** One application over one database. Allocation decisions serialized per event. Finality computed from the clock. Counts derived on every read. No workers, no queue, no history.

---

### Alternative 1 — Explicit places

Instead of comparing a count against a capacity, model capacity as **N discrete places**. Claiming is acquiring a specific place; releasing returns it. Overflow is the queue of claims no place could satisfy.

**What it changes.** Capacity stops being an arithmetic rule and becomes a structural fact. A place can be held by at most one invitee **because it is one thing**, not because a check said so.

| Aspect | Effect |
|---|---|
| **Correctness** | **Strongest of the four.** CR1 — the two-people-one-place failure — becomes structurally impossible rather than prevented by discipline. Satisfies TB-a directly |
| **Complexity** | Slightly higher in the model, **lower in the logic**. No capacity arithmetic to get wrong |
| **Operational burden** | Unchanged. Same deployment, same dependencies |
| **Scalability** | Contention becomes per-place rather than per-event, which is finer-grained and *better* under GA3 |
| **Changeability** | **Mixed.** Raising capacity means adding places — clean. Lowering it (**B6**) means removing places that may be held, which forces the displacement question into the open rather than leaving it undefined |
| **Weakness** | **F12 makes capacity optional**, and an unbounded event has no places to model. It needs a second path, which is exactly the kind of special case that hides bugs |

---

### Alternative 2 — Append-only record of facts

Record every answer and every standing change as an immutable entry. Current state is derived by folding the entries. Nothing is overwritten.

| Aspect | Effect |
|---|---|
| **Correctness** | Strong on *attributability*, neutral on *contention*. It does not prevent CR1 — a serialization point is still required. It does resolve **CR5** (disputes become answerable), **S3** (IS3 becomes accountable), and gives **B14** a natural answer, since order is recorded rather than inferred |
| **Complexity** | **Highest of the four.** Introduces projections, replay, and the question of what happens when a fold is expensive |
| **Operational burden** | Higher. More data, and derived views that can themselves be stale or wrong. Works against TB-d |
| **Scalability** | Worse for reads without caching — and caching collides with **DI4** |
| **Changeability** | **Best.** Facts retained under one interpretation can be reinterpreted later. Given how many questions are open, this has real option value |
| **Weakness** | Solves the problems this design has *least* evidence of having, at the cost of the most machinery. Nothing in any input says disputes occur |

---

### Alternative 3 — Scheduled lifecycle with workers

The counterpoint to DC2. Finality becomes a **stored** state flipped by a scheduled job. Promotion is queued work rather than an in-transaction consequence.

| Aspect | Effect |
|---|---|
| **Correctness** | **Weakest.** A missed or failed run leaves the record mutable after its start time — a correctness failure caused purely by infrastructure (Step 12). Violates TB-a and TB-b together, since a job that did not run announces nothing |
| **Complexity** | Higher: a scheduler, job state, retry semantics, and the idempotence problems that come with them |
| **Operational burden** | **Highest.** Requires exactly the monitoring and alerting **F10** says does not exist |
| **Scalability** | Better at decoupling bursts — promotion work moves off the request path, easing **BN2** |
| **Changeability** | Neutral |
| **Where it would be right** | If promotion ever acquires side effects — notifications, external calls — doing that work inline becomes untenable and this becomes the natural shape |

---

### Comparison

Relative, not absolute. **P** is the proposed design.

| Axis | P — Transactional core | A1 — Explicit places | A2 — Append-only facts | A3 — Scheduled workers |
|---|---|---|---|---|
| **Correctness — contention** | Good, by discipline | **Best, by construction** | Good, by discipline | Poor |
| **Correctness — attributability** | **Absent** | Absent | **Best** | Partial |
| **Complexity** | **Lowest** | Low | Highest | High |
| **Operational burden** | **Lowest** | Lowest | Higher | Highest |
| **Scalability** | Adequate | Slightly better | Worse on reads | Better on bursts |
| **Changeability** | Adequate | Mixed — B6 gets harder | **Best** | Neutral |
| **Fails loudly? (TB-b)** | No | Partly | Yes | **No** |

### Observations

**These are not mutually exclusive.** The two strongest — explicit places and an append-only record — address different weaknesses and compose. A design could make over-admission structurally impossible *and* make standing changes attributable, at the cost of A2's complexity but not its read-path problems, since current state would still be stored directly.

**A1 is the only alternative that makes a top-five risk impossible rather than unlikely.** Under TB-a that is the strongest single argument in this section. Its cost is the unbounded-event special case, which is real.

**A2 buys option value, not correctness.** With fourteen unresolved points in the brief, being able to reinterpret recorded facts later is worth something. But it works against TB-d, and no input evidences the problem it solves.

**A3 is dominated today** and named anyway, because it is what this design *becomes* if promotion ever gains a side effect. Recording why it was not chosen now is more useful than recording that it was rejected.

### A recommendation, marked as such

*Proposal, not a decision.* Keep **P** as the base, adopt **A1's explicit places** if capacity handling is built at all, and adopt **A2 narrowly** — an append-only record of standing changes only, not of everything — to close S3 without paying A2's full cost.

That combination satisfies TB-a on the highest-ranked correctness risk, TB-b on the least visible one, and TB-d by adding one concept rather than an architecture.

### What no alternative fixes

> **B3, B4, B5, B9, B13 and B14 are unaffected by every option above.** They are specification gaps, not structural ones. No architecture decides whether a *Maybe* holds a place, or whether promotion survives finality. Choosing among these alternatives without answering them would produce a well-built system that does the wrong thing — which is the failure mode this document exists to prevent.

---

## Step 16 — Rollout and migration notes

### Starting position

**Nothing exists to migrate from (F1), and nothing exists to release with (F10).** There is no environment, no pipeline, no monitoring, no backup, and no schema versioning. The system has run only on a workstation (**O5**).

So this section has two halves: what must exist before *any* release, and how the feature itself should be sequenced once it can be.

### Prerequisites

Not feature work. Each blocks a safe release rather than a correct design.

| # | Prerequisite | Why it comes first |
|---|---|---|
| **PR1** | **Rotate the committed third-party key** at its provider (**F9**, **S4**) | Already exposed. Independent of everything else here. *If A1 is wrong and the integration is live, rotation breaks it — which is how A1 gets tested.* |
| **PR2** | **Replace implicit schema generation with explicit, versioned migrations** (**F6**, **O2**) | Hibernate's automatic update is **additive only**: it will not drop a column, will not rename one, and will not reliably alter a type. Every later phase changes structure, and the first change it cannot infer fails silently or at startup |
| **PR3** | **A backup, and one proven restore** (**O1**, **DR5**) | Without it, no rollback of data exists at any point in this plan |
| **PR4** | **Decide the finality instant** (**B9**) | Changing it later retroactively changes which events are locked. Cheap now, corrupting afterwards |
| **PR5** | **Resolve host identity** (**B1**) | Phase 1 below is shippable to *real* users only once this exists. Until then it is a local build, because six of nine privileged operations have no gate (**S8**) |

### Release sequencing

The sequencing exploits one fact: **F12 makes capacity optional.** An event without capacity never exercises allocation, contention, or overflow. That means the riskiest machinery can ship *after* a genuinely working product exists — not as a stub, but as a complete subset of the feature.

| Phase | Contents | Delivers | Risk introduced |
|---|---|---|---|
| **0** | PR1–PR5 | Nothing user-visible | — |
| **1** | Events, invitations, per-invitee links, recording and revising answers. **No capacity, no lock** | G1, G8, part of G2 | Low. No allocation exists to get wrong |
| **2** | Finality — answers stop at the start instant | G6 | Moderate. **CR3** becomes live |
| **3** | Capacity, allocation, overflow, promotion | G3, G4, G5 | **Highest.** CR1, CR4, CR9 and every concurrency hazard arrive together |
| **4** | Close and cancel | G7 | Moderate. Depends on **B7** |
| **5** | Delivery | — | Exists only if **B2** resolves toward owned delivery |

Each phase is independently useful, and phase 3 — where the real danger is — lands on a system whose basics have already been exercised.

### Backward compatibility

Greenfield, so there are no existing consumers. There are two compatibility surfaces anyway, and the first is easy to miss:

> **Invitation links are long-lived external references held by people.** Once a link is in someone's inbox it is outside the system's control, and it must keep working across every subsequent release. This is the real backward-compatibility surface — not an interface, but the links already distributed.

The second is **events in flight across a deployment**:

- An event created in phase 1 has no capacity. When phase 3 adds capacity, that event must remain unbounded — **new semantics must not apply retroactively to events created without them.**
- An answer recorded under one set of rules must remain interpretable under the next. *Rule: never change the meaning of a stored value; add a new value instead.* This bears directly on **B3** and **B13**, where a later change to how *Maybe* or *no answer* is represented would silently reinterpret existing records.
- Requests in flight during a restart are protected by the transaction boundary (**F5**) and need nothing further.

### Migration needs

- **No data migration exists today.** Every migration in this plan is structural, introduced by a phase.
- **Phase 3 is the only one with a backfill question.** Introducing capacity to events that never had it requires a defined meaning for its absence — unbounded, not zero (**DI3**).
- **Standing should ship with capacity, not after it.** If answers exist before standing is modelled, standing must be derived for every historical answer — a backfill that depends on **B3**, which is exactly the question that is open. Sequencing them together avoids inventing history.
- **Phase 2 changes no structure but changes behaviour.** Events already past their start instant become locked the moment it ships. That is a data-visible change with no migration, and it should be a deliberate choice rather than a surprise.

### Flags

**No feature-flag infrastructure exists (F10), and none should be invented.** Two things serve instead:

- **The data model is the flag.** Because capacity is optional, an event without one does not exercise phase 3's machinery at all. Allocation can be exercised on a small number of events before it is used widely, with no toggle anywhere.
- **Configuration is sufficient for delivery**, which is the only externally-visible dependency (phase 5).

> **One rule, regardless of mechanism:** a flag or configuration change must never alter the rules of an event already in flight. An event's rules are fixed when it is created and hold until its record is final. Changing capacity semantics, finality interpretation, or promotion behaviour underneath a live event breaks the only promise the system makes to its invitees.

### Rollback

**Rollback here is not the inverse of deployment**, and the plan should not pretend otherwise.

| Concern | Detail |
|---|---|
| **Schema does not roll back with code** | **O2** — a deployment applies structure. Reverting the code leaves the structure. *Therefore: additive changes only. Deprecate, never remove.* Under that rule, code rollback stays safe |
| **State changed in the interval** | Rolling back phase 3 after people were placed in overflow leaves that data uninterpretable, and **silently removes places people were told they held** |
| **Some changes are irreversible for users** | A promotion that happened, and a record that became final, cannot be undone by a deployment. The user-visible effect outlives the release that caused it |
| **No data rollback exists** | Without PR3 there is nothing to restore from. Until then, **forward fix is the only option**, and the plan should be written on that basis |

### Operationally sensitive

| # | Sensitivity | Detail |
|---|---|---|
| **RS1** | **Never deploy close to an event's start time** | **OR1** — an outage in the final hours destroys a response window that cannot be reopened. Deployment windows must consider events approaching finality, which is a scheduling constraint most systems do not have |
| **RS2** | **Clock and timezone changes are behaviour changes** | An NTP step, a host timezone change, or a daylight-saving transition alters who may still act (**DR6**, **B9**). Treat them with the caution given to a release |
| **RS3** | **Changing the finality rule reinterprets history** | Altering the timezone interpretation after launch silently locks or reopens existing events. There is no migration for it because no data changes — only its meaning |
| **RS4** | **Do not add a second instance as a scaling measure** | It breaks **A4** (Step 13). Horizontal scaling is an assumption change and must be treated as a design decision, not an operational one |
| **RS5** | **Phase 3 needs deliberate exercise before real use** | With no monitoring (**F10**), the eight scenarios in Step 12 are the substitute, and they have to be run on purpose |

### Readiness per phase

Because F10 leaves nothing observing the system, each phase needs a stated gate:

- **Phase 1** — a link issued before a deploy still works after it.
- **Phase 2** — the boundary behaves identically either side of a restart, and PR4's chosen instant is the one actually applied.
- **Phase 3** — Step 12's scenarios 1–5 exercised deliberately, especially two acceptances for one remaining place.
- **Phase 4** — an answer in flight when the host closes the event resolves the way **B7** says it should.
- **Phase 5** — a delivery failure after a successful commit leaves the invitation intact (**SE2**).

### Blocked — rollout

| What cannot be planned | Blocked by |
|---|---|
| Whether phase 5 exists at all | **B2** |
| Whether phase 1 is shippable beyond a local build | **B1** / PR5 |
| Phase 4's contents | **B7** |
| Whether phase 3 carries a displacement path | **B6** |
| Any environment, pipeline or monitoring plan | **F10** — none of it exists to plan around, and inventing one here would be fiction |

---

## State of this draft

### What the draft covers

Every section is present and internally cross-referenced. Facts trace to the repository or the brief; assumptions are numbered, and each states what breaks if it is wrong; open questions are triaged by whether they block later work; risks are ranked by impact and detectability; alternatives are compared against declared tiebreakers.

### What the draft does not contain

Stated plainly, because absence is easier to mistake for oversight than a written limitation is:

- **No business motivation.** Step 03's section is deliberately empty, carrying the four questions that would fill it (**Q4**). Nothing was invented to occupy the space.
- **No scale figures of any kind.** Step 13 reasons about the shape of growth and names what would have to be measured. No threshold in this document is a number.
- **No identity mechanism.** **B1** is unanswered, so every authorization invariant is stated as intent with the gap named, not written as though it works.
- **No probability estimates.** Step 14 ranks by impact and detectability only.
- **No implementation detail.** No endpoints, schemas, class names, libraries, or configuration. Where a decision would force one, the decision is presented and left open.

### Where this draft is most likely to be wrong

1. **The separation of *answer* from *standing*** (Step 10). It is the single structural idea in the document that no input requires. Most of Steps 10 and 12 rest on it.
2. **The six proposed non-goals** (N5–N10). Each excludes something the brief never mentions; each could be a real requirement nobody wrote down.
3. **A7 — that whoever holds a link is the invitee.** Unavoidable given F15, and it silently accepts that answers may come from the wrong person.
4. **The Step 15 tiebreakers.** They decide the recommendation, and they exist only because **ORG4** left no authority to appeal to.

### The decisions that would move this design furthest

In order of how much they unblock:

1. **B5** — the contradiction. No implementation can be correct while two stated rules conflict.
2. **B3** — what occupies a place. Six invariants and the legal-combinations table wait on it.
3. **B1** — who a host is. The entire authorization boundary, and whether anything is shippable beyond a local build.
4. **B9** — the finality instant. Cheap now, corrupting later (**RS3**).
5. **Q4** — the motivation. It is the tiebreaker every tradeoff in Step 15 currently lacks.

Answering those five would convert most of this document from conditional to decided.

---

## Step 18 — Pre-review weakness check

Identification only. Nothing here is fixed, and no weakness is argued to a conclusion — the point is to know where the draft is soft **before** a reviewer finds it.

### The weakness that conditions all the others

> **W0 — The document may be using "open question" as a way to avoid deciding.**
>
> There are fourteen open points in the brief and six *Blocked* subsections. **ORG3 states that there is nobody to escalate to** — no product owner, no stakeholder. If no one else can answer these, then listing them is not a neutral act of rigour; it is deferral wearing the costume of discipline. A reviewer is entitled to ask why a document that identified B3 at Step 02 still had not decided it by Step 17. Every weakness below is easier to excuse while W0 stands.

### Vague

| # | Where | Why it is soft |
|---|---|---|
| **W1** | **C1–C6** (Step 03) | The success conditions use terms the document has not defined. "Current expected attendance" cannot be evaluated while **B3** and **B13** are open, so three of six conditions are untestable by their own admission — which makes them aspirations rather than criteria |
| **W2** | **"Perishable"** (Step 03) | Asserted — a released place loses value as the event approaches — and then never used. No invariant, goal, or risk depends on it. Either it matters and should drive something, or it is decoration |
| **W3** | **Request edge** (Step 09) | Listed as a component but given no responsibility beyond "the only way in." It is a label, not a boundary with content |
| **W4** | **"Sufficient now"** (Step 13) | Sufficiency is claimed with no criteria for what would make it insufficient, other than "measure something." The trigger conditions are named; the thresholds cannot be, which leaves the section unable to be acted on |
| **W5** | **Impact ratings** (Step 14) | "High", "Moderate", "Total" are used in the assumption-failure table without any scale defining them |

### Assumption-heavy

| # | Where | Why it is soft |
|---|---|---|
| **W6** | **Step 10 entire** | Rests on one proposed idea — the separation of *answer* from *standing* — that no input requires. Step 12 inherits it. **Two sections have a single point of failure**, and it is a judgement call |
| **W7** | **Step 15's recommendation** | A proposal resting on proposals: the recommendation follows from tiebreakers TB-a–TB-e, which are themselves unratified, which exist only because **ORG4** left no authority. The stack has no grounded base |
| **W8** | **A9** | "Conceptual depth suffices" is the assumption that excuses the absence of depth in Steps 09–16. **It is circular** — the assumption licenses the gap it creates, and it was adopted because Q1 was undecided rather than because anyone judged it right |
| **W9** | **A8** | One unverified line — no privacy regime applies — carries S7, OR7, the retention gap, and part of the risk ranking. A single assumption is load-bearing for the document's entire treatment of personal data |
| **W10** | **A1** | The WhatsApp integration is dismissed as unrelated **on the basis of file naming**. N11, DR3 and part of Step 16's PR1 rest on it. The evidence is weak relative to the weight placed on it |

### Structurally incomplete

| # | Where | What is missing |
|---|---|---|
| **W11** | **Step 07** | **There is no workflow for removing an invitee**, yet **B4** and **DI9** both depend on removal existing. A flow is referenced by two later sections and never defined in the section whose job that was |
| **W12** | **Step 11** | **No threat model.** Boundaries and notes are listed, but the document never states who the adversary is — a curious invitee, a forwarded link, an opportunistic stranger, an automated scanner. Without one, the section is a checklist rather than an argument |
| **W13** | **Steps 08 → 09** | 41 invariants are stated and DC8 gestures at "application logic primarily", but **no mapping says which invariant is enforced where**. The most important link between the two sections is absent |
| **W14** | **Whole document** | **Nothing proposes what to observe.** Step 14 concludes the defining weakness is silence; Step 16 records that no monitoring exists; no section treats it. The document diagnoses the problem and never prescribes |
| **W15** | **Steps 04 and 03** | **No priority among goals.** If G5 (immediate reallocation) conflicts with G6 (finality) — which **B5** guarantees it will — nothing says which yields. The empty business motivation removes the only basis for ranking them |
| **W16** | **Whole document** | **No diagram.** Both state machines are tables; the architecture is prose. For a design meant to be reviewed, the absence of any visual makes the structure harder to check than it needs to be |

### Under-argued

| # | Where | Why the argument is thin |
|---|---|---|
| **W17** | **DC2 — computed finality** | Argued largely from **F10**, but F10 records that no monitoring, CI or environments are *described* — not that scheduling is unavailable. The framework almost certainly provides it. **The argument leans on an absence that is not really a constraint**, and the genuinely strong reason (a missed run fails silently) is left to Step 12 instead of being made here |
| **W18** | **Step 15 vs. S3** | An internal tension. **S3 proposes a record of standing changes as the minimum that makes IS3 accountable**; Step 15 then dismisses the append-only alternative because "no input evidences the problem it solves." Both cannot be right, and the document does not notice |
| **W19** | **CI2 — no transient over-admission** | Asserted as required, never argued. For a physical event with a soft door, briefly exceeding capacity before self-correcting may be acceptable. The strictest invariant in the document has the least justification behind it |
| **W20** | **N6 / A3 — no plus-ones** | Excluded chiefly because it would break capacity arithmetic. That is **reasoning from implementation convenience to product scope** — the weakest form of justification for a boundary, and it is one of the most likely boundaries to be challenged |
| **W21** | **BN2** (Step 13) | The claim that one hot event can exhaust the thread pool is plausible and entirely unquantified. With no figures anywhere, it is intuition presented in the register of analysis |

### Presentation weaknesses

| # | Issue |
|---|---|
| **W22** | **Repetition.** The committed-credential fact appears in fifteen places across six sections. A reader cannot easily tell which mentions add something and which restate. The same is true, less severely, of B1, B3 and B5 |
| **W23** | **Length versus decision density.** Roughly 1,500 lines, and the number of ratified decisions is small. The proportion of the document that describes, catalogues and defers is high relative to the proportion that decides |
| **W24** | **The Summary claims merit.** "What the design gets right" is self-assessment in a draft that has not been reviewed. A reviewer may reasonably discount the section that grades itself |

### What is *not* weak

Recorded so a review does not spend effort re-checking it:

- **Step 01's facts** are verifiable by inspection and were verified.
- **The fact/assumption/open-question separation** holds throughout; no assumption is presented as fact.
- **Attribution of proposals** is consistent — every judgement call is marked, in-section and in the decision register.
- **Step 12's hazards** are concrete and derived from stated invariants rather than from a generic concurrency checklist.

### Severity, for sequencing a review

**Address before review:** W0, W11, W13, W18 — a missing workflow, an absent mapping, an internal contradiction, and the deferral question.
**Expect a reviewer to challenge:** W6, W7, W17, W19, W20 — the places where judgement calls are doing structural work.
**Acknowledge as known limits:** W4, W5, W15, W21 — all downstream of **Q4** and the total absence of figures. They do not resolve without an input the author does not have.

---

*End of draft. Eighteen sections complete; weaknesses identified, not yet addressed.*
