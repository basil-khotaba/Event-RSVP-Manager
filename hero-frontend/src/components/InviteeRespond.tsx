import { useCallback, useEffect, useState } from 'react'
import type { InviteeView, ResponseValue } from '../types'
import { getInvitation, respond } from '../services/rsvpApi'

const CHOICES: { value: ResponseValue; label: string }[] = [
  { value: 'YES', label: 'Yes' },
  { value: 'NO', label: 'No' },
  { value: 'MAYBE', label: 'Maybe' },
]

export default function InviteeRespond({ token }: { token: string }) {
  const [view, setView] = useState<InviteeView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setView(await getInvitation(token))
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [token])

  useEffect(() => {
    void load()
  }, [load])

  async function choose(value: ResponseValue) {
    setBusy(true)
    setError(null)
    try {
      setView(await respond(token, value))
    } catch (err) {
      // A refusal is a normal outcome: locked, closed or cancelled.
      setError(err instanceof Error ? err.message : String(err))
      await load()
    } finally {
      setBusy(false)
    }
  }

  if (error && !view) return <div className="card error">{error}</div>
  if (!view) return <div className="card">Loading…</div>

  return (
    <div className="stack">
      <div className="card">
        <h2>{view.title}</h2>
        {view.description && <p>{view.description}</p>}
        <p className="hint">
          {new Date(view.startsAt).toLocaleString()}
          {view.location ? ` · ${view.location}` : ''}
        </p>
        <p className="hint">Invitation for {view.yourEmail}</p>
      </div>

      <div className="card">
        <h3>Will you be there?</h3>

        {view.lockedReason ? (
          <p className="notice">{view.lockedReason}</p>
        ) : (
          <div className="row">
            {CHOICES.map((choice) => (
              <button
                key={choice.value}
                onClick={() => choose(choice.value)}
                disabled={busy}
                className={view.yourResponse === choice.value ? 'chosen' : ''}
              >
                {choice.label}
              </button>
            ))}
          </div>
        )}

        {error && <p className="error">{error}</p>}

        <div className="standing">
          {view.yourResponse === null && <p>You have not answered yet.</p>}

          {view.yourResponse === 'YES' && view.yourStanding === 'HOLDING' && (
            <p className="good">You have a place. You are confirmed.</p>
          )}

          {view.yourResponse === 'YES' && view.yourStanding === 'WAITING' && (
            <p className="warn">
              The event is full, so you are on the waitlist
              {view.yourWaitlistPosition ? ` at position ${view.yourWaitlistPosition}` : ''}.
              If a place frees up, the first person waiting gets it automatically.
            </p>
          )}

          {view.yourResponse === 'NO' && <p>You have declined.</p>}

          {view.yourResponse === 'MAYBE' && (
            <p>
              You answered maybe. A maybe does not hold a place — say yes to claim one.
            </p>
          )}
        </div>

        {!view.lockedReason && (
          <p className="hint">You can change your answer any time before the event starts.</p>
        )}
      </div>
    </div>
  )
}
