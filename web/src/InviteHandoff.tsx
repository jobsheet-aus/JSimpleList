import {
  useEffect,
  useState,
} from 'react'
import './App.css'
import { supabase } from './lib/supabase'

type HandoffState =
  | 'checking'
  | 'active'
  | 'code-sent'
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

  const [code, setCode] =
    useState('')

  const [busy, setBusy] =
    useState(false)

  const [message, setMessage] =
    useState('')

  async function invokeHandoff(
    action: 'inspect' | 'send_code' | 'verify_code',
    verificationCode?: string,
  ) {
    if (!handoff) {
      throw new Error('This invitation link is incomplete')
    }

    const { data, error } =
      await supabase.functions.invoke(
        'invitation-handoff',
        {
          body: {
            action,
            handoff,
            ...(verificationCode
              ? { code: verificationCode }
              : {}),
          },
        },
      )

    if (error) {
      const context =
        (error as {
          context?: Response
        }).context

      if (context) {
        try {
          const body =
            await context.clone().json()

          if (
            body &&
            typeof body.error === 'string'
          ) {
            throw new Error(body.error)
          }

          if (body?.state === 'invalid') {
            throw new Error(
              'This invitation has expired or is no longer valid',
            )
          }
        } catch (responseError) {
          if (responseError instanceof Error) {
            throw responseError
          }
        }
      }

      throw error
    }

    return data
  }

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

  async function sendCode() {
    setBusy(true)
    setMessage('')

    try {
      const data =
        await invokeHandoff('send_code')

      if (data?.state !== 'active') {
        setState('invalid')
        return
      }

      setContext(data as HandoffContext)
      setState('code-sent')
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'Could not send verification code',
      )
    } finally {
      setBusy(false)
    }
  }

  async function verifyCode() {
    const cleanCode =
      code.replace(/\D/g, '').slice(0, 6)

    if (cleanCode.length !== 6) {
      setMessage('Enter the six-digit verification code')
      return
    }

    setBusy(true)
    setMessage('')

    try {
      const data =
        await invokeHandoff(
          'verify_code',
          cleanCode,
        ) as AcceptedResponse

      if (
        data?.state !== 'accepted' ||
        !data.session?.accessToken ||
        !data.session?.refreshToken
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
            data.session.accessToken,

          refresh_token:
            data.session.refreshToken,
        })

      if (sessionError) {
        throw sessionError
      }

      setState('accepted')
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'Verification failed',
      )
    } finally {
      setBusy(false)
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

        {(state === 'active' || state === 'code-sent') && context && (
          <>
            <p className="secondary">
              {context.inviterDisplayName} invited you to join
            </p>

            <p>
              <strong>{context.listName}</strong>
            </p>

            {state === 'active' && (
              <>
                <p className="secondary">
                  To continue, verify the email address this invitation
                  was sent to: {context.maskedEmail}
                </p>

                <button
                  type="button"
                  onClick={sendCode}
                  disabled={busy}
                >
                  {busy
                    ? 'Sending code'
                    : 'Send verification code'}
                </button>
              </>
            )}

            {state === 'code-sent' && (
              <>
                <p className="secondary">
                  We sent a six-digit verification code to
                  {' '}
                  {context.maskedEmail}
                </p>

                <label htmlFor="invite-code">
                  Verification code
                </label>

                <input
                  id="invite-code"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  value={code}
                  maxLength={6}
                  onChange={(event) => {
                    setCode(
                      event.target.value
                        .replace(/\D/g, '')
                        .slice(0, 6),
                    )

                    setMessage('')
                  }}
                  disabled={busy}
                />

                <button
                  type="button"
                  onClick={verifyCode}
                  disabled={busy}
                >
                  {busy
                    ? 'Verifying'
                    : 'Verify and join list'}
                </button>

                <button
                  type="button"
                  className="secondary-button"
                  onClick={sendCode}
                  disabled={busy}
                >
                  Send another code
                </button>
              </>
            )}
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
