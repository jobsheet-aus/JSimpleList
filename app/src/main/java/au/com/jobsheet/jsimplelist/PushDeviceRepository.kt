package au.com.jobsheet.jsimplelist

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PushDeviceRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    fun requestFirebaseRegistration() {
        FirebaseMessaging
            .getInstance()
            .register()
            .addOnFailureListener { exception ->
                Log.w(
                    "JSimpleListPush",
                    "Firebase registration request failed",
                    exception
                )
            }
    }

    suspend fun registerStoredDevice(
        store: SimpleListStore,
        clientInstanceId: String
    ) {
        val installationId =
            store.loadFirebaseInstallationId()
                ?.trim()
                .orEmpty()

        if (installationId.isEmpty()) {
            return
        }

        register(
            clientInstanceId = clientInstanceId,
            firebaseInstallationId = installationId
        )
    }

    suspend fun register(
        clientInstanceId: String,
        firebaseInstallationId: String
    ) {
        client.postgrest.rpc(
            function = "register_push_device",
            parameters = buildJsonObject {
                put(
                    "target_client_instance_id",
                    clientInstanceId
                )
                put(
                    "target_firebase_installation_id",
                    firebaseInstallationId
                )
            }
        )
    }

    suspend fun unregister(
        clientInstanceId: String
    ) {
        client.postgrest.rpc(
            function = "unregister_push_device",
            parameters = buildJsonObject {
                put(
                    "target_client_instance_id",
                    clientInstanceId
                )
            }
        )
    }
}
