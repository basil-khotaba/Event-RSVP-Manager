import { useState } from 'react'
import { createEvent } from '../services/rsvpApi'

export default function CreateEvent() {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [location, setLocation] = useState('')
  const [bounded, setBounded] = useState(false)
  const [capacity, setCapacity] = useState('10')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const created = await createEvent({
        title,
        description,
        // datetime-local gives a local wall-clock string; converting here is what
        // makes the stored value a single unambiguous instant (design: DI7, B9).
        startsAt: new Date(startsAt).toISOString(),
        location,
        capacity: bounded ? Number(capacity) : null,
      })
      window.location.search = `?host=${created.hostToken}`
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <h2>Create an event</h2>
      <form onSubmit={submit}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>

        <label>
          Description
          <textarea
            value={description}
            rows={3}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>

        <label>
          Starts at
          <input
            type="datetime-local"
            value={startsAt}
            onChange={(e) => setStartsAt(e.target.value)}
            required
          />
        </label>

        <label>
          Location
          <input value={location} onChange={(e) => setLocation(e.target.value)} />
        </label>

        <label className="inline">
          <input
            type="checkbox"
            checked={bounded}
            onChange={(e) => setBounded(e.target.checked)}
          />
          Limit the number of places
        </label>

        {bounded && (
          <label>
            Maximum capacity
            <input
              type="number"
              min={1}
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
            />
          </label>
        )}

        <p className="hint">
          An event without a limit never has a waitlist. Capacity is optional by design.
        </p>

        {error && <p className="error">{error}</p>}

        <button type="submit" disabled={busy}>
          {busy ? 'Creating…' : 'Create event'}
        </button>
      </form>
    </div>
  )
}
