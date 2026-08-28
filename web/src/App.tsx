import {
  useCallback,
  useEffect,
  useState,
} from 'react'
import type { Session } from '@supabase/supabase-js'
import {
  browserClientInstanceId,
  supabase,
} from './lib/supabase'
import './App.css'

type AuthStep = 'email' | 'code'

type OnlineList = {
  id: string
  name: string
  kind: string
  role: string
  position: number
}

type OnlineItemSnapshot = {
  id: string
  list_id: string
  description: string
  quantity: number | null
  completed: boolean
  position: number
  created_at: string
  updated_at: string
  deleted_at: string | null
}

type OnlineListSnapshot = {
  id: string
  owner_id: string
  name: string
  kind: string
  created_at: string
  updated_at: string
  deleted_at: string | null
}

type ListSyncSnapshot = {
  state: string
  list_id?: string
  list?: OnlineListSnapshot
  items?: OnlineItemSnapshot[]
  deleted_at?: string
  removed_at?: string
}

function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [authReady, setAuthReady] = useState(false)
  const [authStep, setAuthStep] = useState<AuthStep>('email')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [onlineLists, setOnlineLists] = useState<OnlineList[]>([])
  const [listsLoading, setListsLoading] = useState(false)
  const [selectedSnapshot, setSelectedSnapshot] =
    useState<ListSyncSnapshot | null>(null)
  const [snapshotLoading, setSnapshotLoading] = useState(false)
  const [newItemDescription, setNewItemDescription] = useState('')
  const [newItemQuantity, setNewItemQuantity] = useState('1')
  const [editingItemId, setEditingItemId] = useState<string | null>(null)
  const [editingFocus, setEditingFocus] =
    useState<'description' | 'quantity'>('description')
  const [editDescription, setEditDescription] = useState('')
  const [editQuantity, setEditQuantity] = useState('1')

  useEffect(() => {
    void supabase.auth.getSession().then(({ data }) => {
      setSession(data.session)
      setAuthReady(true)
    })

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)

      if (!nextSession) {
        setOnlineLists([])
        setSelectedSnapshot(null)
      }

      setAuthReady(true)
    })

    return () => {
      subscription.unsubscribe()
    }
  }, [])

  useEffect(() => {
    if (session) {
      void loadOnlineLists(session.user.id)
    }
  }, [session])



  async function loadOnlineLists(userId: string) {
    setListsLoading(true)
    setMessage('')

    const {
      data: memberships,
      error: membershipsError,
    } = await supabase
      .from('list_members')
      .select('list_id, role, position')
      .eq('user_id', userId)
      .is('removed_at', null)
      .order('position')

    if (membershipsError) {
      setListsLoading(false)
      setMessage(membershipsError.message)
      return
    }

    if (!memberships || memberships.length === 0) {
      setOnlineLists([])
      setListsLoading(false)
      return
    }

    const listIds =
      memberships.map((membership) => membership.list_id)

    const {
      data: lists,
      error: listsError,
    } = await supabase
      .from('lists')
      .select('id, name, kind')
      .in('id', listIds)
      .is('deleted_at', null)

    if (listsError) {
      setListsLoading(false)
      setMessage(listsError.message)
      return
    }

    const listsById =
      new Map(
        (lists ?? []).map((list) => [
          list.id,
          list,
        ]),
      )

    const loadedLists =
      memberships.flatMap((membership) => {
        const list = listsById.get(membership.list_id)

        if (!list) {
          return []
        }

        return [{
          id: list.id,
          name: list.name,
          kind: list.kind,
          role: membership.role,
          position: membership.position,
        }]
      })

    setOnlineLists(loadedLists)
    setListsLoading(false)
  }

  async function updateOnlineItem(
    item: OnlineItemSnapshot,
    completed: boolean,
  ) {
    setMessage('')

    const updatedItem = {
      id: item.id,
      list_id: item.list_id,
      description: item.description,
      quantity: item.quantity,
      completed,
      position: item.position,
      created_at: item.created_at,
      updated_at: new Date().toISOString(),
      deleted_at: item.deleted_at,
      origin_client_id: browserClientInstanceId,
    }

    const { error } = await supabase
      .from('items')
      .upsert(updatedItem)

    if (error) {
      setMessage(error.message)
      return
    }

    await openOnlineList(item.list_id)
  }

  async function uncheckAllItems() {
    const list = selectedSnapshot?.list
    const completedItems =
      (selectedSnapshot?.items ?? [])
        .filter(
          (item) =>
            item.deleted_at === null &&
            item.completed,
        )

    if (!list || completedItems.length === 0) {
      return
    }

    const updatedAt = new Date().toISOString()

    const updatedItems =
      completedItems.map((item) => ({
        id: item.id,
        list_id: item.list_id,
        description: item.description,
        quantity: item.quantity,
        completed: false,
        position: item.position,
        created_at: item.created_at,
        updated_at: updatedAt,
        deleted_at: item.deleted_at,
        origin_client_id: browserClientInstanceId,
      }))

    setMessage('')

    const { error } = await supabase
      .from('items')
      .upsert(updatedItems)

    if (error) {
      setMessage(error.message)
      return
    }

    await openOnlineList(list.id)
  }

  function startEditingItem(
    item: OnlineItemSnapshot,
    focus: 'description' | 'quantity',
  ) {
    setEditingItemId(item.id)
    setEditingFocus(focus)
    setEditDescription(item.description)
    setEditQuantity(
      item.quantity === null
        ? '1'
        : String(item.quantity),
    )
    setMessage('')
  }

  function cancelEditingItem() {
    setEditingItemId(null)
    setEditDescription('')
    setEditQuantity('1')
  }

  async function saveEditedItem(item: OnlineItemSnapshot) {
    const trimmedDescription = editDescription.trim()

    if (!trimmedDescription) {
      return
    }

    let quantity = item.quantity

    if (selectedSnapshot?.list?.kind === 'SHOPPING') {
      const trimmedQuantity = editQuantity.trim()
      const integerText = /^[+-]?\d+$/.test(trimmedQuantity)
      const parsedQuantity =
        integerText ? Number(trimmedQuantity) : Number.NaN

      quantity =
        Number.isInteger(parsedQuantity) &&
        parsedQuantity >= -2147483648 &&
        parsedQuantity <= 2147483647
          ? Math.max(parsedQuantity, 1)
          : 1
    }

    const updatedItem = {
      id: item.id,
      list_id: item.list_id,
      description: trimmedDescription,
      quantity,
      completed: item.completed,
      position: item.position,
      created_at: item.created_at,
      updated_at: new Date().toISOString(),
      deleted_at: item.deleted_at,
      origin_client_id: browserClientInstanceId,
    }

    setMessage('')

    const { error } = await supabase
      .from('items')
      .upsert(updatedItem)

    if (error) {
      setMessage(error.message)
      return
    }

    cancelEditingItem()
    await openOnlineList(item.list_id)
  }

  async function deleteOnlineItem(item: OnlineItemSnapshot) {
    setMessage('')

    const { error } = await supabase.rpc(
      'delete_online_item',
      {
        target_item_id: item.id,
        target_origin_client_id: browserClientInstanceId,
      },
    )

    if (error) {
      setMessage(error.message)
      return
    }

    if (editingItemId === item.id) {
      cancelEditingItem()
    }

    await openOnlineList(item.list_id)
  }

  async function deleteAllOnlineItems() {
    const list = selectedSnapshot?.list
    const activeItems =
      (selectedSnapshot?.items ?? [])
        .filter((item) => item.deleted_at === null)

    if (!list || activeItems.length === 0) {
      return
    }

    const confirmed = window.confirm(
      `Delete all ${activeItems.length} ${
        activeItems.length === 1 ? 'item' : 'items'
      } from "${list.name}"?`,
    )

    if (!confirmed) {
      return
    }

    setMessage('')

    const { error } = await supabase.rpc(
      'delete_all_online_items',
      {
        target_list_id: list.id,
        target_origin_client_id: browserClientInstanceId,
      },
    )

    if (error) {
      setMessage(error.message)
      return
    }

    cancelEditingItem()
    await openOnlineList(list.id)
  }

  async function addOnlineItem() {
    const list = selectedSnapshot?.list
    const trimmedDescription = newItemDescription.trim()

    if (!list || !trimmedDescription) {
      return
    }

    let quantity: number | null = null

    if (list.kind === 'SHOPPING') {
      const trimmedQuantity = newItemQuantity.trim()
      const integerText = /^[+-]?\d+$/.test(trimmedQuantity)
      const parsedQuantity =
        integerText ? Number(trimmedQuantity) : Number.NaN

      quantity =
        Number.isInteger(parsedQuantity) &&
        parsedQuantity >= -2147483648 &&
        parsedQuantity <= 2147483647
          ? Math.max(parsedQuantity, 1)
          : 1
    }

    const activeItems =
      (selectedSnapshot.items ?? [])
        .filter((item) => item.deleted_at === null)

    const minimumPosition =
      activeItems.length === 0
        ? 10
        : Math.min(...activeItems.map((item) => item.position))

    const now = new Date().toISOString()

    const newItem = {
      id: crypto.randomUUID(),
      list_id: list.id,
      description: trimmedDescription,
      quantity,
      completed: false,
      position: minimumPosition - 10,
      created_at: now,
      updated_at: now,
      deleted_at: null,
      origin_client_id: browserClientInstanceId,
    }

    setMessage('')

    const { error } = await supabase
      .from('items')
      .insert(newItem)

    if (error) {
      setMessage(error.message)
      return
    }

    setNewItemDescription('')

    if (list.kind === 'SHOPPING') {
      setNewItemQuantity('1')
    }

    await openOnlineList(list.id)
  }

  const openOnlineList = useCallback(
    async (listId: string) => {
      setSnapshotLoading(true)
      setMessage('')

      const {
        data,
        error,
      } = await supabase.rpc(
        'get_list_sync_snapshot',
        {
          target_list_id: listId,
        },
      )

      setSnapshotLoading(false)

      if (error) {
        setMessage(error.message)
        return
      }

      const snapshot = data as ListSyncSnapshot

      if (snapshot.state !== 'active' || !snapshot.list) {
        setMessage('List is no longer available')
        return
      }

      setSelectedSnapshot(snapshot)
    },
    [],
  )

  useEffect(() => {
    const listId = selectedSnapshot?.list?.id

    if (!session || !listId) {
      return
    }

    let cancelled = false

    const channel = supabase
      .channel(
        `jsimplelist:list:${listId}`,
        {
          config: {
            private: true,
          },
        },
      )
      .on(
        'broadcast',
        {
          event: 'list_changed',
        },
        ({ payload }) => {
          if (
            payload?.origin_client_id ===
            browserClientInstanceId
          ) {
            return
          }

          void openOnlineList(listId)
        },
      )

    void supabase.realtime
      .setAuth(session.access_token)
      .then(() => {
        if (!cancelled) {
          channel.subscribe()
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setMessage(
            error instanceof Error
              ? error.message
              : 'Could not start realtime sync',
          )
        }
      })

    return () => {
      cancelled = true
      void supabase.removeChannel(channel)
    }
  }, [
    session,
    selectedSnapshot?.list?.id,
    openOnlineList,
  ])

  async function requestOtp() {
    const trimmedEmail = email.trim()

    if (!trimmedEmail) {
      return
    }

    setBusy(true)
    setMessage('')

    const { error } = await supabase.auth.signInWithOtp({
      email: trimmedEmail,
    })

    setBusy(false)

    if (error) {
      setMessage(error.message)
      return
    }

    setEmail(trimmedEmail)
    setCode('')
    setAuthStep('code')
  }

  async function verifyOtp() {
    const trimmedCode = code.trim()

    if (!trimmedCode) {
      return
    }

    setBusy(true)
    setMessage('')

    const { error } = await supabase.auth.verifyOtp({
      email,
      token: trimmedCode,
      type: 'email',
    })

    setBusy(false)

    if (error) {
      setMessage(error.message)
      return
    }

    setCode('')
  }

  async function signOut() {
    setBusy(true)
    setMessage('')

    const { error } = await supabase.auth.signOut()

    setBusy(false)

    if (error) {
      setMessage(error.message)
      return
    }

    setAuthStep('email')
    setCode('')
  }

  if (!authReady) {
    return (
      <main className="app-shell">
        <section className="auth-panel">
          <h1>JSimpleList</h1>
          <p>Loading</p>
        </section>
      </main>
    )
  }

  if (session && selectedSnapshot?.list) {
    const activeItems =
      (selectedSnapshot.items ?? [])
        .filter((item) => item.deleted_at === null)
        .sort((left, right) => {
          if (left.completed !== right.completed) {
            return left.completed ? 1 : -1
          }

          if (left.position !== right.position) {
            return left.position - right.position
          }

          return left.created_at.localeCompare(right.created_at)
        })

    const hasCompletedItems =
      activeItems.some((item) => item.completed)

    const itemCount = activeItems.length

    return (
      <main className="app-shell">
        <section className="auth-panel list-panel">
          <div className="list-header">
            <div>
              <p className="app-brand">JSimpleList</p>
              <h1>{selectedSnapshot.list.name}</h1>
              <p className="secondary">
                {selectedSnapshot.list.kind === 'TODO'
                  ? 'To-do'
                  : selectedSnapshot.list.kind === 'SHOPPING'
                    ? 'Shopping'
                    : 'Discussion points'}
                {' · '}
                {itemCount} {itemCount === 1 ? 'item' : 'items'}
              </p>
            </div>

            <button
              type="button"
              className="secondary-button compact-button"
              onClick={() => setSelectedSnapshot(null)}
            >
              Lists
            </button>
          </div>

          <form
            className="item-entry-form"
            onSubmit={(event) => {
              event.preventDefault()
              void addOnlineItem()
            }}
          >
            {selectedSnapshot.list.kind === 'SHOPPING' && (
              <input
                type="text"
                inputMode="numeric"
                className="item-entry-quantity"
                aria-label="Quantity"
                value={newItemQuantity}
                onFocus={(event) => {
                  event.currentTarget.select()
                }}
                onChange={(event) => {
                  setNewItemQuantity(event.target.value)
                }}
              />
            )}

            <input
              type="text"
              className="item-entry-description"
              aria-label="Item"
              placeholder="Item"
              autoComplete="off"
              value={newItemDescription}
              onChange={(event) => {
                setNewItemDescription(event.target.value)
              }}
            />

            <button
              type="submit"
              className="item-entry-add"
              disabled={
                snapshotLoading ||
                newItemDescription.trim().length === 0
              }
            >
              Add
            </button>
          </form>

          {activeItems.length > 0 && (
            <div className="item-heading-row">
              <button
                type="button"
                className={`item-check item-check-button ${
                  hasCompletedItems ? 'checked' : ''
                }`}
                aria-label="Uncheck all completed items"
                disabled={!hasCompletedItems || snapshotLoading}
                onClick={() => {
                  void uncheckAllItems()
                }}
              >
                {hasCompletedItems ? '✓' : ''}
              </button>

              {selectedSnapshot.list.kind === 'SHOPPING' && (
                <span className="item-heading-quantity">
                  Qty
                </span>
              )}

              <span className="item-heading-description">
                Item
              </span>

              <button
                type="button"
                className="secondary-button compact-button item-delete-all-button"
                disabled={snapshotLoading}
                onClick={() => {
                  void deleteAllOnlineItems()
                }}
              >
                Delete all
              </button>
            </div>
          )}

          {activeItems.length === 0 ? (
            <p className="secondary">
              No items
            </p>
          ) : (
            <div className="item-rows">
              {activeItems.map((item) => (
                <div
                  key={item.id}
                  className={`item-row ${
                    item.completed ? 'completed' : ''
                  }`}
                  onBlur={(event) => {
                    if (
                      editingItemId === item.id &&
                      !event.currentTarget.contains(event.relatedTarget)
                    ) {
                      cancelEditingItem()
                    }
                  }}
                >
                  <button
                    type="button"
                    className={`item-check item-check-button ${
                      item.completed ? 'checked' : ''
                    }`}
                    aria-label={
                      item.completed
                        ? `Uncheck ${item.description}`
                        : `Check ${item.description}`
                    }
                    onClick={() => {
                      void updateOnlineItem(
                        item,
                        !item.completed,
                      )
                    }}
                  >
                    {item.completed ? '✓' : ''}
                  </button>

                  {editingItemId === item.id ? (
                    <>
                      {selectedSnapshot.list?.kind === 'SHOPPING' && (
                        <input
                          type="text"
                          inputMode="numeric"
                          className="item-edit-quantity"
                          aria-label="Quantity"
                          value={editQuantity}
                          autoFocus={editingFocus === 'quantity'}
                          onFocus={(event) => {
                            event.currentTarget.select()
                          }}
                          onChange={(event) => {
                            setEditQuantity(event.target.value)
                          }}
                          onKeyDown={(event) => {
                            if (event.key === 'Escape') {
                              cancelEditingItem()
                            }
                          }}
                        />
                      )}

                      <input
                        type="text"
                        className="item-edit-description"
                        aria-label="Item"
                        autoComplete="off"
                        value={editDescription}
                        autoFocus={editingFocus === 'description'}
                        onFocus={(event) => {
                          const length = event.currentTarget.value.length
                          event.currentTarget.setSelectionRange(
                            length,
                            length,
                          )
                        }}
                        onChange={(event) => {
                          setEditDescription(event.target.value)
                        }}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault()
                            void saveEditedItem(item)
                          } else if (event.key === 'Escape') {
                            cancelEditingItem()
                          }
                        }}
                      />

                      <button
                        type="button"
                        className="secondary-button compact-button item-edit-action"
                        disabled={editDescription.trim().length === 0}
                        onClick={() => {
                          void saveEditedItem(item)
                        }}
                      >
                        Save
                      </button>

                      <button
                        type="button"
                        className="secondary-button compact-button item-edit-action"
                        onClick={cancelEditingItem}
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <>
                      {selectedSnapshot.list?.kind === 'SHOPPING' && (
                        <button
                          type="button"
                          className="item-quantity item-quantity-button"
                          onClick={() => {
                            startEditingItem(item, 'quantity')
                          }}
                        >
                          {item.quantity ?? ''}
                        </button>
                      )}

                      <button
                        type="button"
                        className="item-description item-description-button"
                        onClick={() => {
                          startEditingItem(item, 'description')
                        }}
                      >
                        {item.description}
                      </button>

                      <button
                        type="button"
                        className="secondary-button compact-button item-delete-button"
                        onClick={() => {
                          void deleteOnlineItem(item)
                        }}
                      >
                        Delete
                      </button>
                    </>
                  )}
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            className="secondary-button compact-button"
            disabled={snapshotLoading}
            onClick={() => {
              void openOnlineList(selectedSnapshot.list!.id)
            }}
          >
            {snapshotLoading ? 'Refreshing' : 'Refresh'}
          </button>

          {message && (
            <p className="message">{message}</p>
          )}
        </section>
      </main>
    )
  }

  if (session) {
    return (
      <main className="app-shell">
        <section className="auth-panel list-panel">
          <h1>JSimpleList</h1>
          <p className="secondary signed-in">
            Signed in as {session.user.email}
          </p>

          <div className="online-lists">
            <div className="online-lists-heading">
              <h2>Lists</h2>

              <button
                type="button"
                className="secondary-button compact-button"
                disabled={listsLoading}
                onClick={() => {
                  void loadOnlineLists(session.user.id)
                }}
              >
                {listsLoading ? 'Refreshing' : 'Refresh'}
              </button>
            </div>

            {listsLoading && onlineLists.length === 0 ? (
              <p className="secondary">
                Loading lists
              </p>
            ) : onlineLists.length === 0 ? (
              <p className="secondary">
                No lists available
              </p>
            ) : (
              <div className="online-list-rows">
                {onlineLists.map((list) => (
                  <button
                    key={list.id}
                    type="button"
                    className="online-list-row"
                    disabled={snapshotLoading}
                    onClick={() => void openOnlineList(list.id)}
                  >
                    <div>
                      <strong>{list.name}</strong>
                      <span>
                        {list.kind === 'TODO'
                          ? 'To-do'
                          : list.kind === 'SHOPPING'
                            ? 'Shopping'
                            : 'Discussion points'}
                      </span>
                    </div>

                    <span className="list-role">
                      {list.role === 'owner'
                        ? 'Owner'
                        : 'Shared'}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            type="button"
            className="secondary-button compact-button"
            disabled={busy}
            onClick={() => void signOut()}
          >
            Sign out
          </button>

          {message && (
            <p className="message">{message}</p>
          )}
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <section className="auth-panel">
        <h1>JSimpleList</h1>

        {authStep === 'email' ? (
          <>
            <p className="secondary">
              Sign in to access your online lists
            </p>

            <label htmlFor="email">
              Email address
            </label>

            <input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              disabled={busy}
              onChange={(event) => setEmail(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  void requestOtp()
                }
              }}
            />

            <button
              type="button"
              disabled={busy || !email.trim()}
              onClick={() => void requestOtp()}
            >
              {busy ? 'Sending' : 'Send code'}
            </button>
          </>
        ) : (
          <>
            <p className="secondary">
              Enter the six-digit code sent to {email}
            </p>

            <label htmlFor="code">
              Verification code
            </label>

            <input
              id="code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              disabled={busy}
              onChange={(event) => {
                setCode(
                  event.target.value
                    .replace(/\D/g, '')
                    .slice(0, 6),
                )
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && code.length === 6) {
                  void verifyOtp()
                }
              }}
            />

            <button
              type="button"
              disabled={busy || code.length !== 6}
              onClick={() => void verifyOtp()}
            >
              {busy ? 'Signing in' : 'Sign in'}
            </button>

            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => {
                setAuthStep('email')
                setCode('')
                setMessage('')
              }}
            >
              Use another email address
            </button>
          </>
        )}

        {message && (
          <p className="message">{message}</p>
        )}
      </section>
    </main>
  )
}

export default App