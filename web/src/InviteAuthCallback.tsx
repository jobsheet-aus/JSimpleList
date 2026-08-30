import {
  useEffect,
  useState,
} from 'react'
import type { Session } from '@supabase/supabase-js'
import './App.css'
import { supabase } from './lib/supabase'

type CallbackState =
  | 'checking'
  | 'signed-in'
  | 'error'

function InviteAuthCallback() {
  const [state, setState] =
    useState<CallbackState>('checking')
  const [session, setSession] =
    useState<Session | null>(null)

  useEffect(() => {
    const hashParams =
      new URLSearchParams(window.location.hash.slice(1))

    if (hashParams.get('error')) {
      setState('error')
      return
    }

    let cancelled = false

    void supabase.auth.getSession().then(({ data, error }) => {
      if (cancelled) {
        return
      }

      if (error) {
        setState('error')
        return
      }

      if (data.session) {
        setSession(data.session)
        setState('signed-in')
      }
    })

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      if (cancelled || !nextSession) {
        return
      }

      setSession(nextSession)
      setState('signed-in')
    })

    return () => {
      cancelled = true
      subscription.unsubscribe()
    }
  }, [])

  return (
    <main className="app-shell">
      <section className="auth-panel">
        <h1>JSimpleList</h1>

        {state === 'checking' && (
          <p className="secondary">
            Signing you in
          </p>
        )}

        {state === 'signed-in' && (
          <>
            <p className="secondary">
              Signed in as {session?.user.email}
            </p>

            <p className="secondary">
              Checking your list invitation
            </p>
          </>
        )}

        {state === 'error' && (
          <>
            <p className="secondary">
              This sign-in link has expired or is no longer valid
            </p>

            <p className="secondary">
              Use the six-digit sign-in code from your email instead
            </p>
          </>
        )}
      </section>
    </main>
  )
}

export default InviteAuthCallback
