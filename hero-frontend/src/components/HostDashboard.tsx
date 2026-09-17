import { useCallback, useEffect, useState } from 'react'
import type { AttendeeRow, HostView } from '../types'
import { cancelEvent, closeEvent, getHostView, invite } from '../services/rsvpApi'

function inviteLink(token: string) {
  return `${window.location.origin}/?invite=${token}`
}

function People({ rows, empty }: { rows: AttendeeRow[]; empty: string }) {
  if (rows.length === 0) return <p className="hint">{empty}</p>
  return (
    <ul className="people">
      {rows.map((r) => (
        <li key={r.token}>
          <span>{r.email}</span>
          <button className="link" onClick={() => navigator.clipboard?.writeText(inviteLink(r.token))}>
            copy link
          </button>
        </li>
      ))}
    </ul>
  )
}

export default function HostDashboard({ hostToken }: { hostToken: string }) {
  const [view, setView] = useState<HostView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [emails, setEmails] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setView(await getHostView(hostToken))
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [hostToken])

  useEffect(() => {
    void load()
    // "Live" was never defined in the brief (design: B8). Polling is the modest
    // reading of it, and the one this stack supports cheaply — holding connections
    // open costs a thread per viewer here.
    const timer = setInterval(() => void load(), 4000)
    return () => clearInterval(timer)
  }, [load])

  async function sendInvites(e: React.FormEvent) {
    e.preventDefault()
    const list = emails
      .split(/[\s,;]+/)
      .map((s) => s.trim())
      .filter(Boolean)
    if (list.length === 0) return
    setBusy(true)
    try {
      await invite(hostToken, list)
      setEmails('')
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  if (error && !view) return <div className="card error">{error}</div>
  if (!view) return <div className="card">Loading…</div>

  const c = view.counts

  return (
    <div className="stack">
      <div className="card">
        <div className="row">
          <h2>{view.title}</h2>
          <span className={`badge ${view.status.toLowerCase()}`}>
            {view.locked ? 'LOCKED' : view.status}
          </span>
        </div>
        {view.description && <p>{view.description}</p>}
        <p className="hint">
          {new Date(view.startsAt).toLocaleString()}
          {view.location ? ` · ${view.location}` : ''}
          {' · '}
          {view.capacity === null ? 'no capacity limit' : `capacity ${view.capacity}`}
        </p>
        {view.locked && (
          <p className="notice">The event has started. All answers are locked.</p>
        )}
      </div>

      <div className="card">
        <h3>Attendance</h3>
        <div className="counts">
          <div className="count"><strong>{c.holding}</strong><span>confirmed</span></div>
          <div className="count"><strong>{c.waiting}</strong><span>waitlisted</span></div>
          <div className="count"><strong>{c.maybe}</strong><span>maybe</span></div>
          <div className="count"><strong>{c.no}</strong><span>declined</span></div>
          <div className="count"><strong>{c.notAnswered}</strong><span>no answer</span></div>
          <div className="count">
            <strong>{c.placesLeft === null ? '∞' : c.placesLeft}</strong>
            <span>places left</span>
          </div>
        </div>
        <p className="hint">
          Every number here is derived from the answers themselves — nothing is stored
          or cached, so no count can drift from reality.
        </p>
      </div>

      <div className="card">
        <h3>Invite people</h3>
        <form onSubmit={sendInvites}>
          <textarea
            rows={2}
            value={emails}
            placeholder="email addresses, separated by commas or spaces"
            onChange={(e) => setEmails(e.target.value)}
          />
          <button type="submit" disabled={busy}>
            {busy ? 'Inviting…' : 'Invite'}
          </button>
        </form>
        <p className="hint">
          The system issues a unique link per invitee but does not send anything — the
          scaffold has no mail capability. Copy each link and distribute it yourself.
        </p>
      </div>

      <div className="grid">
        <div className="card">
          <h3>Confirmed ({view.confirmed.length})</h3>
          <People rows={view.confirmed} empty="Nobody is confirmed yet." />
        </div>
        <div className="card">
          <h3>Waitlist ({view.waitlist.length})</h3>
          <People rows={view.waitlist} empty="Nobody is waiting." />
          {view.waitlist.length > 0 && (
            <p className="hint">In order. The next freed place goes to the top of this list.</p>
          )}
        </div>
        <div className="card">
          <h3>Maybe ({view.maybe.length})</h3>
          <People rows={view.maybe} empty="No maybes." />
        </div>
        <div className="card">
          <h3>Declined ({view.declined.length})</h3>
          <People rows={view.declined} empty="Nobody has declined." />
        </div>
        <div className="card">
          <h3>No answer ({view.notAnswered.length})</h3>
          <People rows={view.notAnswered} empty="Everyone has answered." />
          {view.notAnswered.length > 0 && (
            <p className="hint">
              Not an answer — these people have not responded at all.
            </p>
          )}
        </div>
      </div>

      <div className="card">
        <h3>Event controls</h3>
        <div className="row">
          <button
            onClick={async () => {
              await closeEvent(hostToken)
              await load()
            }}
            disabled={view.status !== 'ACCEPTING'}
          >
            Close to further responses
          </button>
          <button
            className="danger"
            onClick={async () => {
              if (confirm('Cancel this event? This cannot be undone.')) {
                await cancelEvent(hostToken)
                await load()
              }
            }}
            disabled={view.status === 'CANCELLED'}
          >
            Cancel event
          </button>
        </div>
        <p className="hint">
          Closing stops new answers; the event still happens. Cancelling means it does
          not. Neither is reversible.
        </p>
      </div>

      <div className="card">
        <h3>Your host link</h3>
        <code className="token">{window.location.href}</code>
        <p className="hint">
          This link is how you get back to this dashboard. Anyone holding it has your
          view — keep it to yourself.
        </p>
      </div>
    </div>
  )
}
