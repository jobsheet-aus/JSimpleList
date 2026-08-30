import './App.css'

function InviteHandoff() {
  const tokenHash =
    new URLSearchParams(window.location.search).get('token_hash')

  return (
    <main className="app-shell">
      <section className="auth-panel">
        <h1>JSimpleList</h1>

        {tokenHash ? (
          <>
            <p className="secondary">
              You've been invited to share a JSimpleList list
            </p>

            <button
              type="button"
              disabled
            >
              Open JSimpleList
            </button>

            <p className="secondary">
              You can also use the six-digit sign-in code from your email
            </p>
          </>
        ) : (
          <>
            <p className="secondary">
              This invitation link is incomplete
            </p>

            <p className="message">
              Open the invitation from your email and try again
            </p>
          </>
        )}
      </section>
    </main>
  )
}

export default InviteHandoff
