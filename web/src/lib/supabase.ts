import { createClient } from '@supabase/supabase-js'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
const supabasePublishableKey = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY

if (!supabaseUrl) {
  throw new Error('VITE_SUPABASE_URL is not configured')
}

if (!supabasePublishableKey) {
  throw new Error('VITE_SUPABASE_PUBLISHABLE_KEY is not configured')
}

export const supabase = createClient(
  supabaseUrl,
  supabasePublishableKey,
  {
    db: {
      schema: 'jsimplelist',
    },
  },
)

const clientInstanceStorageKey =
  'jsimplelist_client_instance_id'

let clientInstanceId =
  localStorage.getItem(clientInstanceStorageKey)

if (!clientInstanceId) {
  clientInstanceId = crypto.randomUUID()
  localStorage.setItem(
    clientInstanceStorageKey,
    clientInstanceId,
  )
}

export const browserClientInstanceId = clientInstanceId