import './App.css'
import CreateEvent from './components/CreateEvent'
import HostDashboard from './components/HostDashboard'
import InviteeRespond from './components/InviteeRespond'

/**
 * Two entry experiences, distinguished by the token in the URL.
 *
 * No router is installed in the scaffold, and the two surfaces need nothing more
 * than this, so none was added. The token in the query string is what decides which
 * surface you get — and it is also the only thing that grants access to it.
 */
export default function App() {
  const params = new URLSearchParams(window.location.search)
  const hostToken = params.get('host')
  const inviteToken = params.get('invite')

  return (
    <div className="app">
      <header>
        <a href="/" className="brand">Event RSVP Manager</a>
        <span className="sub">
          {hostToken ? 'Host view' : inviteToken ? 'Invitation' : 'New event'}
        </span>
      </header>

      <main>
        {hostToken ? (
          <HostDashboard hostToken={hostToken} />
        ) : inviteToken ? (
          <InviteeRespond token={inviteToken} />
        ) : (
          <CreateEvent />
        )}
      </main>
    </div>
  )
}
