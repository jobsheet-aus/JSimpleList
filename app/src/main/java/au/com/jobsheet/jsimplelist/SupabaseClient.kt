package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions

object JSimpleListSupabase {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Auth) {
            scheme = "https"
            host = "jslist.jobsheet.com.au"
            defaultRedirectUrl =
                "https://jslist.jobsheet.com.au/auth/invite"
        }
        install(Postgrest) {
            defaultSchema = "jsimplelist"
        }
        install(Realtime)
        install(Functions)
    }
}
