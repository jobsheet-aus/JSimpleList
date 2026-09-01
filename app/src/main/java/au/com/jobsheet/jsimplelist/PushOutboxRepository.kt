package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions

class PushOutboxRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    suspend fun processPending() {
        client.functions.invoke(
            function = "send-push-outbox"
        )
    }
}
