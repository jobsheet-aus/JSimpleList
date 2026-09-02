import {
  useEffect,
  useState,
} from 'react'
import './App.css'
import { supabase } from './lib/supabase'

type HandoffState =
  | 'checking'
  | 'active'
  | 'joining'
  | 'accepted'
  | 'invalid'
  | 'error'

type HandoffContext = {
  state: 'active'
  inviterDisplayName: string
  listName: string
  listKind: string
  maskedEmail: string
  expiresAt: string
}

type AcceptedResponse = {
  state: 'accepted'
  listId: string
  session: {
    accessToken: string
    refreshToken: string
    expiresAt: number | null
  }
}

function InviteHandoff() {
  const handoff =
    new URLSearchParams(window.location.search).get('h')?.trim() ?? ''

  const [state, setState] =
    useState<HandoffState>(
      handoff
        ? 'checking'
        : 'invalid',
    )

  const [context, setContext] =
    useState<HandoffContext | null>(null)

  const [message, setMessage] =
    useState('')

  useEffect(() => {
    let cancelled = false

    if (!handoff) {
      return
    }

    void supabase.functions.invoke(
      'invitation-handoff',
      {
        body: {
          action: 'inspect',
          handoff,
        },
      },
    )
      .then(({ data, error }) => {
        if (cancelled) {
          return
        }

        if (error || data?.state !== 'active') {
          setState('invalid')
          return
        }

        setContext(data as HandoffContext)
        setState('active')
      })
      .catch((error) => {
        if (cancelled) {
          return
        }

        setMessage(
          error instanceof Error
            ? error.message
            : 'Could not open this invitation',
        )

        setState('error')
      })

    return () => {
      cancelled = true
    }
  }, [handoff])

  async function joinList() {
    if (!handoff) {
      return
    }

    setState('joining')
    setMessage('')

    try {
      const { data, error } =
        await supabase.functions.invoke(
          'invitation-handoff',
          {
            body: {
              action: 'join',
              handoff,
            },
          },
        )

      if (error) {
        throw error
      }

      const accepted =
        data as AcceptedResponse

      if (
        accepted?.state !== 'accepted' ||
        !accepted.session?.accessToken ||
        !accepted.session?.refreshToken
      ) {
        throw new Error(
          'The invitation could not be accepted',
        )
      }

      const {
        error: sessionError,
      } =
        await supabase.auth.setSession({
          access_token:
            accepted.session.accessToken,

          refresh_token:
            accepted.session.refreshToken,
        })

      if (sessionError) {
        throw sessionError
      }

      setState('accepted')
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'Could not join this list',
      )

      setState('active')
    }
  }

  function openLists() {
    window.location.assign('/')
  }

  return (
    <main className="app-shell">
      <section className="auth-panel">
        <h1>JSimpleList</h1>

        {state === 'checking' && (
          <p className="secondary">
            Checking your invitation
          </p>
        )}

        {(state === 'active' || state === 'joining') && context && (
          <>
            <p className="secondary">
              {context.inviterDisplayName} invited you to join
            </p>

            <p>
              <strong>{context.listName}</strong>
            </p>

            <p className="secondary">
              This invitation was sent to {context.maskedEmail}
            </p>

            <button
              type="button"
              onClick={joinList}
              disabled={state === 'joining'}
            >
              {state === 'joining'
                ? 'Joining list'
                : 'Join list'}
            </button>
          </>
        )}

        {state === 'accepted' && (
          <>
            <p className="secondary">
              Invitation accepted
            </p>

            <button
              type="button"
              onClick={openLists}
            >
              Open JSimpleList
            </button>
          </>
        )}

        {state === 'invalid' && (
          <>
            <p className="secondary">
              This invitation has expired or is no longer valid
            </p>

            <p className="message">
              Ask the list owner to send you a new invitation
            </p>
          </>
        )}

        {state === 'error' && (
          <p className="message">
            {message || 'Could not open this invitation'}
          </p>
        )}

        {message &&
          state !== 'error' &&
          state !== 'invalid' && (
            <p className="message">
              {message}
            </p>
          )}
      </section>
    </main>
  )
}

export default InviteHandoff
