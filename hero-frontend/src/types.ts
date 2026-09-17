// Shapes returned by the backend. Kept deliberately separate for the two surfaces:
// the invitee view never carries aggregate data (design: F16, AI4).

export type ResponseValue = 'YES' | 'NO' | 'MAYBE'
export type Standing = 'NONE' | 'HOLDING' | 'WAITING'
export type EventStatus = 'ACCEPTING' | 'CLOSED' | 'CANCELLED'

export interface CreatedEvent {
  eventId: number
  hostToken: string
}

export interface InvitedPerson {
  email: string
  token: string | null
  status: string
}

export interface Counts {
  yes: number
  no: number
  maybe: number
  notAnswered: number
  holding: number
  waiting: number
  capacity: number | null
  placesLeft: number | null
}

export interface AttendeeRow {
  email: string
  response: ResponseValue | null
  standing: Standing
  waitlistedAt: string | null
  respondedAt: string | null
  token: string
}

export interface HostView {
  eventId: number
  title: string
  description: string | null
  startsAt: string
  location: string | null
  capacity: number | null
  status: EventStatus
  locked: boolean
  acceptingAnswers: boolean
  counts: Counts
  confirmed: AttendeeRow[]
  waitlist: AttendeeRow[]
  declined: AttendeeRow[]
  maybe: AttendeeRow[]
  notAnswered: AttendeeRow[]
}

export interface InviteeView {
  title: string
  description: string | null
  startsAt: string
  location: string | null
  eventStatus: EventStatus
  locked: boolean
  canRespond: boolean
  lockedReason: string | null
  yourEmail: string
  yourResponse: ResponseValue | null
  yourStanding: Standing
  yourWaitlistPosition: number | null
}
