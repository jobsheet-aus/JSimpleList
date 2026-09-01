import { createClient } from "npm:@supabase/supabase-js@2";

type FirebaseServiceAccount = {
    project_id: string;
    client_email: string;
    private_key: string;
    token_uri?: string;
};

type PushOutboxRow = {
    id: string;
    notification_id: string;
    recipient_user_id: string;
    event_type: string;
    list_id: string | null;
    actor_user_id: string | null;
    list_name: string;
    actor_display_name: string;
    attempt_count: number;
};

type PushDevice = {
    firebase_installation_id: string;
};

function base64UrlEncodeBytes(
    bytes: Uint8Array
): string {
    let binary = "";

    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }

    return btoa(binary)
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/g, "");
}

function base64UrlEncodeText(
    value: string
): string {
    return base64UrlEncodeBytes(
        new TextEncoder().encode(value)
    );
}

function decodeBase64Json(
    encoded: string
): FirebaseServiceAccount {
    const json =
        new TextDecoder().decode(
            Uint8Array.from(
                atob(encoded),
                (character) =>
                    character.charCodeAt(0)
            )
        );

    return JSON.parse(json);
}

function pemToArrayBuffer(
    pem: string
): ArrayBuffer {
    const base64 =
        pem
            .replace(
                "-----BEGIN PRIVATE KEY-----",
                ""
            )
            .replace(
                "-----END PRIVATE KEY-----",
                ""
            )
            .replace(/\s+/g, "");

    return Uint8Array.from(
        atob(base64),
        (character) =>
            character.charCodeAt(0)
    ).buffer;
}

async function createGoogleAccessToken(
    serviceAccount: FirebaseServiceAccount
): Promise<string> {
    const now =
        Math.floor(Date.now() / 1000);

    const signingInput =
        base64UrlEncodeText(
            JSON.stringify({
                alg: "RS256",
                typ: "JWT",
            })
        ) +
        "." +
        base64UrlEncodeText(
            JSON.stringify({
                iss:
                    serviceAccount.client_email,

                scope:
                    "https://www.googleapis.com/auth/firebase.messaging",

                aud:
                    serviceAccount.token_uri ??
                    "https://oauth2.googleapis.com/token",

                iat:
                    now,

                exp:
                    now + 3600,
            })
        );

    const privateKey =
        await crypto.subtle.importKey(
            "pkcs8",
            pemToArrayBuffer(
                serviceAccount.private_key
            ),
            {
                name: "RSASSA-PKCS1-v1_5",
                hash: "SHA-256",
            },
            false,
            ["sign"]
        );

    const signature =
        await crypto.subtle.sign(
            "RSASSA-PKCS1-v1_5",
            privateKey,
            new TextEncoder().encode(
                signingInput
            )
        );

    const jwt =
        signingInput +
        "." +
        base64UrlEncodeBytes(
            new Uint8Array(signature)
        );

    const response =
        await fetch(
            serviceAccount.token_uri ??
                "https://oauth2.googleapis.com/token",
            {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded",
                },
                body: new URLSearchParams({
                    grant_type:
                        "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    assertion:
                        jwt,
                }),
            }
        );

    if (!response.ok) {
        throw new Error(
            "Google OAuth failed: " +
            response.status +
            " " +
            await response.text()
        );
    }

    const body =
        await response.json();

    if (
        typeof body.access_token !== "string" ||
        body.access_token.trim() === ""
    ) {
        throw new Error(
            "Google OAuth returned no access token"
        );
    }

    return body.access_token;
}

async function sendPush(
    serviceAccount: FirebaseServiceAccount,
    accessToken: string,
    fid: string,
    row: PushOutboxRow
): Promise<void> {
    const response =
        await fetch(
            "https://fcm.googleapis.com/v1/projects/" +
                encodeURIComponent(
                    serviceAccount.project_id
                ) +
                "/messages:send",
            {
                method: "POST",
                headers: {
                    Authorization:
                        `Bearer ${accessToken}`,

                    "Content-Type":
                        "application/json",
                },
                body: JSON.stringify({
                    message: {
                        fid,

                        data: {
                            event_type:
                                row.event_type,

                            recipient_user_id:
                                row.recipient_user_id,

                            notification_id:
                                row.notification_id,

                            list_id:
                                row.list_id ?? "",

                            list_name:
                                row.list_name,

                            actor_display_name:
                                row.actor_display_name,
                        },

                        android: {
                            priority: "HIGH",
                        },
                    },
                }),
            }
        );

    if (!response.ok) {
        throw new Error(
            "FCM send failed: " +
            response.status +
            " " +
            await response.text()
        );
    }
}

Deno.serve(async (req: Request) => {
    if (req.method !== "POST") {
        return Response.json(
            { error: "Method not allowed" },
            { status: 405 }
        );
    }

    const supabaseUrl =
        Deno.env.get("SUPABASE_URL");

    const publishableKeysJson =
        Deno.env.get(
            "SUPABASE_PUBLISHABLE_KEYS"
        );

    const serviceRoleKey =
        Deno.env.get(
            "SUPABASE_SERVICE_ROLE_KEY"
        );

    const firebaseServiceAccountB64 =
        Deno.env.get(
            "FIREBASE_SERVICE_ACCOUNT_B64"
        );

    const authorization =
        req.headers.get("Authorization");

    if (
        !supabaseUrl ||
        !publishableKeysJson ||
        !serviceRoleKey ||
        !firebaseServiceAccountB64
    ) {
        return Response.json(
            { error: "Server configuration error" },
            { status: 500 }
        );
    }

    if (!authorization) {
        return Response.json(
            { error: "Authentication required" },
            { status: 401 }
        );
    }

    try {
        /*
         * Require a genuine signed-in JSimpleList user to nudge the worker.
         * The caller receives no outbox contents or FIDs.
         */
        const publishableKeys =
            JSON.parse(
                publishableKeysJson
            );

        const publishableKey =
            publishableKeys.default;

        if (!publishableKey) {
            throw new Error(
                "Publishable key unavailable"
            );
        }

        const userClient =
            createClient(
                supabaseUrl,
                publishableKey,
                {
                    global: {
                        headers: {
                            Authorization:
                                authorization,
                        },
                    },
                    auth: {
                        persistSession: false,
                        autoRefreshToken: false,
                    },
                }
            );

        const {
            data: { user },
            error: userError,
        } =
            await userClient.auth.getUser();

        if (userError || !user) {
            return Response.json(
                { error: "Authentication required" },
                { status: 401 }
            );
        }

        const adminClient =
            createClient(
                supabaseUrl,
                serviceRoleKey,
                {
                    auth: {
                        persistSession: false,
                        autoRefreshToken: false,
                    },
                }
            );

        const {
            data: pendingRows,
            error: pendingError,
        } =
            await adminClient
                .schema("jsimplelist")
                .from("push_outbox")
                .select(
                    "id,notification_id,recipient_user_id,event_type,list_id,actor_user_id,list_name,actor_display_name,attempt_count"
                )
                .is("sent_at", null)
                .lte(
                    "next_attempt_at",
                    new Date().toISOString()
                )
                .order(
                    "created_at",
                    { ascending: true }
                )
                .limit(20);

        if (pendingError) {
            throw pendingError;
        }

        const rows =
            (pendingRows ?? []) as PushOutboxRow[];

        if (rows.length === 0) {
            return Response.json(
                {
                    processed: 0,
                    sent: 0,
                    failed: 0,
                },
                { status: 200 }
            );
        }

        const serviceAccount =
            decodeBase64Json(
                firebaseServiceAccountB64
            );

        const accessToken =
            await createGoogleAccessToken(
                serviceAccount
            );

        let sent = 0;
        let failed = 0;

        for (const row of rows) {
            const attemptTime =
                new Date().toISOString();

            try {
                const {
                    data: devices,
                    error: deviceError,
                } =
                    await adminClient
                        .schema("jsimplelist")
                        .from("push_devices")
                        .select(
                            "firebase_installation_id"
                        )
                        .eq(
                            "user_id",
                            row.recipient_user_id
                        );

                if (deviceError) {
                    throw deviceError;
                }

                const targets =
                    (devices ?? []) as PushDevice[];

                if (targets.length === 0) {
                    throw new Error(
                        "Recipient has no registered push device"
                    );
                }

                let successfulTargets = 0;

                for (const target of targets) {
                    try {
                        await sendPush(
                            serviceAccount,
                            accessToken,
                            target.firebase_installation_id,
                            row
                        );

                        successfulTargets += 1;
                    } catch (targetError) {
                        console.error(
                            "JSimpleList outbox target failed",
                            targetError
                        );
                    }
                }

                if (successfulTargets === 0) {
                    throw new Error(
                        "FCM failed for all registered devices"
                    );
                }

                const {
                    error: updateError,
                } =
                    await adminClient
                        .schema("jsimplelist")
                        .from("push_outbox")
                        .update({
                            attempt_count:
                                row.attempt_count + 1,

                            last_attempt_at:
                                attemptTime,

                            sent_at:
                                attemptTime,

                            last_error:
                                null,
                        })
                        .eq(
                            "id",
                            row.id
                        )
                        .is(
                            "sent_at",
                            null
                        );

                if (updateError) {
                    throw updateError;
                }

                sent += 1;
            } catch (error) {
                failed += 1;

                const message =
                    error instanceof Error
                        ? error.message
                        : String(error);

                console.error(
                    "JSimpleList outbox delivery failed",
                    {
                        outboxId:
                            row.id,

                        error:
                            message,
                    }
                );

                const nextAttempt =
                    new Date(
                        Date.now() +
                        5 * 60 * 1000
                    ).toISOString();

                await adminClient
                    .schema("jsimplelist")
                    .from("push_outbox")
                    .update({
                        attempt_count:
                            row.attempt_count + 1,

                        last_attempt_at:
                            attemptTime,

                        next_attempt_at:
                            nextAttempt,

                        last_error:
                            message.slice(0, 2000),
                    })
                    .eq(
                        "id",
                        row.id
                    )
                    .is(
                        "sent_at",
                        null
                    );
            }
        }

        console.log(
            "JSimpleList push outbox processed",
            {
                processed:
                    rows.length,

                sent,
                failed,
            }
        );

        return Response.json(
            {
                processed:
                    rows.length,

                sent,
                failed,
            },
            { status: 200 }
        );
    } catch (error) {
        console.error(
            "JSimpleList push outbox worker failed",
            error
        );

        return Response.json(
            {
                error:
                    "Could not process push outbox",
            },
            { status: 500 }
        );
    }
});
