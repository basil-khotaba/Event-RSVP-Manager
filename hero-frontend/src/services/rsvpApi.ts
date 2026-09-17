import type {
  CreatedEvent,
  HostView,
  InviteeView,
  InvitedPerson,
  ResponseValue,
} from '../types'

const BASE = 'http://localhost:8280/api'

async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    // The backend answers refusals with a reason (locked, closed, cancelled).
    // Surfacing it matters: a refused answer is a normal outcome here, not a bug.
    let detail = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.detail) detail = body.detail
      else if (body?.message) detail = body.message
    } catch {
      /* no JSON body — keep the status line */
    }
    throw new Error(detail)
  }
  return res.json() as Promise<T>
}

export function createEvent(input: {
  title: string
  description: string
  startsAt: string
  location: string
  capacity: number | null
}): Promise<CreatedEvent> {
  return call<CreatedEvent>('/events', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function getHostView(hostToken: string): Promise<HostView> {
  return call<HostView>(`/host/${hostToken}`)
}

export function invite(hostToken: string, emails: string[]): Promise<InvitedPerson[]> {
  return call<InvitedPerson[]>(`/host/${hostToken}/invitations`, {
    method: 'POST',
    body: JSON.stringify({ emails }),
  })
}

export function closeEvent(hostToken: string): Promise<HostView> {
  return call<HostView>(`/host/${hostToken}/close`, { method: 'POST' })
}

export function cancelEvent(hostToken: string): Promise<HostView> {
  return call<HostView>(`/host/${hostToken}/cancel`, { method: 'POST' })
}

export function getInvitation(token: string): Promise<InviteeView> {
  return call<InviteeView>(`/invitations/${token}`)
}

export function respond(token: string, response: ResponseValue): Promise<InviteeView> {
  return call<InviteeView>(`/invitations/${token}/response`, {
    method: 'POST',
    body: JSON.stringify({ response }),
  })
}
