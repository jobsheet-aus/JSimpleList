import { createClient } from "npm:@supabase/supabase-js@2";

type FirebaseServiceAccount = {
    project_id: string;
    client_email: string;
    private_key: string;
    token_uri?: string;
};

type PushDevice = {
    user_id: string;
    firebase_installation_id: string;
};

function base64UrlEncodeBytes(bytes: Uint8Array): string {
    let binary = "";

    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }

    return btoa(binary)
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/g, "");
}

function base64UrlEncodeText(value: string): string {
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
                (character) => character.charCodeAt(0)
            )
        );

    return JSON.parse(json);
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
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

    const bytes =
        Uint8Array.from(
            atob(base64),
            (character) => character.charCodeAt(0)
        );

    return bytes.buffer;
}

async function createGoogleAccessToken(
    serviceAccount: FirebaseServiceAccount
): Promise<string> {
    const now =
        Math.floor(Date.now() / 1000);

    const header = {
        alg: "RS256",
        typ: "JWT",
    };

    const claims = {
        iss: serviceAccount.client_email,
        scope:
            "https://www.googleapis.com/auth/firebase.messaging",
        aud:
            serviceAccount.token_uri ??
            "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
    };

    const encodedHeader =
        base64UrlEncodeText(
            JSON.stringify(header)
        );

    const encodedClaims =
        base64UrlEncodeText(
            JSON.stringify(claims)
        );

    const signingInput =
        `${encodedHeader}.${encodedClaims}`;

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
        `${signingInput}.` +
        base64UrlEncodeBytes(
            new Uint8Array(signature)
        );

    const tokenResponse =
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
                    assertion: jwt,
                }),
            }
        );

    if (!tokenResponse.ok) {
        const details =
            await tokenResponse.text();

        throw new Error(
            "Google OAuth token request failed: " +
            `${tokenResponse.status} ${details}`
        );
    }

    const tokenJson =
        await tokenResponse.json();

    if (
        typeof tokenJson.access_token !== "string" ||
        tokenJson.access_token.trim() === ""
    ) {
        throw new Error(
            "Google OAuth response did not contain an access token"
        );
    }

    return tokenJson.access_token;
}

async function sendInvitationPush(
    serviceAccount: FirebaseServiceAccount,
    accessToken: string,
    target: PushDevice,
    invitationId: string,
    listId: string,
    listName: string,
    actorDisplayName: string
): Promise<boolean> {
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
                        fid:
                            target.firebase_installation_id,

                        data: {
                            event_type:
                                "list_invitation",

                            recipient_user_id:
                                target.user_id,

                            invitation_id:
                                invitationId,

                            list_id:
                                listId,

                            list_name:
                                listName,

                            actor_display_name:
                                actorDisplayName,
                        },

                        android: {
                            priority: "HIGH",
                        },
                    },
                }),
            }
        );

    if (!response.ok) {
        const details =
            await response.text();

        console.error(
            "JSimpleList invitation push failed",
            {
                status: response.status,
                details,
            }
        );

        return false;
    }

    return true;
}

function fallbackDisplayName(
    email: string
): string {
    const localPart =
        email
            .split("@", 1)[0]
            .trim();

    const base =
        (localPart || "user")
            .slice(0, 49);

    return `${base}@`;
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

    if (
        !supabaseUrl ||
        !publishableKeysJson
    ) {
        return Response.json(
            { error: "Server configuration error" },
            { status: 500 }
        );
    }

    const authorization =
        req.headers.get("Authorization");

    if (!authorization) {
        return Response.json(
            { error: "Authentication required" },
            { status: 401 }
        );
    }

    try {
        const body =
            await req.json();

        const listId =
            typeof body.listId === "string"
                ? body.listId.trim()
                : "";

        const email =
            typeof body.email === "string"
                ? body.email.trim().toLowerCase()
                : "";

        if (!listId || !email) {
            return Response.json(
                {
                    error:
                        "List and email address are required",
                },
                { status: 400 }
            );
        }

        const publishableKeys =
            JSON.parse(
                publishableKeysJson
            );

        const publishableKey =
            publishableKeys.default;

        if (!publishableKey) {
            return Response.json(
                {
                    error:
                        "Server configuration error",
                },
                { status: 500 }
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

        if (
            userError ||
            !user ||
            !user.email
        ) {
            return Response.json(
                {
                    error:
                        "Authentication required",
                },
                { status: 401 }
            );
        }

        if (
            user.email.toLowerCase() ===
            email
        ) {
            return Response.json(
                {
                    error:
                        "You are already using this account",
                },
                { status: 400 }
            );
        }

        const {
            data: list,
            error: listError,
        } =
            await userClient
                .schema("jsimplelist")
                .from("lists")
                .select(
                    "id,name,owner_id"
                )
                .eq(
                    "id",
                    listId
                )
                .is(
                    "deleted_at",
                    null
                )
                .single();

        if (
            listError ||
            !list
        ) {
            return Response.json(
                {
                    error:
                        "List not found",
                },
                { status: 404 }
            );
        }

        if (
            list.owner_id !==
            user.id
        ) {
            return Response.json(
                {
                    error:
                        "Only the list owner can invite people",
                },
                { status: 403 }
            );
        }

        const {
            data: invitationId,
            error: invitationError,
        } =
            await userClient
                .schema("jsimplelist")
                .rpc(
                    "replace_list_invitation",
                    {
                        target_list_id:
                            listId,

                        target_email:
                            email,
                    }
                );

        if (
            invitationError ||
            typeof invitationId !== "string" ||
            invitationId.trim() === ""
        ) {
            console.error(
                "JSimpleList invitation replacement failed",
                invitationError
            );

            const message =
                invitationError?.message ??
                "Could not create invitation";

            const alreadyMember =
                message
                    .toLowerCase()
                    .includes(
                        "already a member"
                    );

            return Response.json(
                {
                    error:
                        alreadyMember
                            ? "This person is already a member of the list"
                            : "Could not create invitation",
                },
                {
                    status:
                        alreadyMember
                            ? 400
                            : 500,
                }
            );
        }

        /*
         * Authentication remains separate from invitation semantics.
         * The invitation row is authoritative; Auth only proves control
         * of the invited email address.
         */
        const authClient =
            createClient(
                supabaseUrl,
                publishableKey,
                {
                    auth: {
                        persistSession: false,
                        autoRefreshToken: false,
                    },
                }
            );

        const {
            error: otpError,
        } =
            await authClient.auth
                .signInWithOtp({
                    email,
                    options: {
                        shouldCreateUser:
                            true,

                        emailRedirectTo:
                            "https://jslist.jobsheet.com.au/auth/invite",
                    },
                });

        if (otpError) {
            console.error(
                "JSimpleList invitation OTP request failed",
                otpError
            );

            return Response.json(
                {
                    error:
                        "Invitation was created, but the sign-in code could not be sent",
                },
                { status: 500 }
            );
        }

        /*
         * Push is deliberately best-effort. Invitation creation and the
         * email OTP remain successful even when Firebase is unavailable.
         */
        let pushTargets = 0;
        let pushSent = 0;

        if (
            serviceRoleKey &&
            firebaseServiceAccountB64
        ) {
            try {
                const adminClient =
                    createClient(
                        supabaseUrl,
                        serviceRoleKey,
                        {
                            auth: {
                                persistSession:
                                    false,

                                autoRefreshToken:
                                    false,
                            },
                        }
                    );

                const {
                    data: pushDevices,
                    error: pushLookupError,
                } =
                    await adminClient
                        .schema(
                            "jsimplelist"
                        )
                        .from(
                            "push_devices"
                        )
                        .select(
                            "user_id,firebase_installation_id"
                        )
                        .eq(
                            "user_email",
                            email
                        );

                if (pushLookupError) {
                    throw pushLookupError;
                }

                const devices =
                    (
                        pushDevices ??
                        []
                    ) as PushDevice[];

                pushTargets =
                    devices.length;

                if (
                    devices.length > 0
                ) {
                    const {
                        data: profile,
                    } =
                        await adminClient
                            .schema(
                                "jsimplelist"
                            )
                            .from(
                                "profiles"
                            )
                            .select(
                                "display_name"
                            )
                            .eq(
                                "user_id",
                                user.id
                            )
                            .maybeSingle();

                    const actorDisplayName =
                        typeof profile?.display_name ===
                                "string" &&
                            profile.display_name.trim() !==
                                ""
                            ? profile.display_name.trim()
                            : fallbackDisplayName(
                                user.email
                            );

                    const serviceAccount =
                        decodeBase64Json(
                            firebaseServiceAccountB64
                        );

                    if (
                        !serviceAccount.project_id ||
                        !serviceAccount.client_email ||
                        !serviceAccount.private_key
                    ) {
                        throw new Error(
                            "Firebase service account is incomplete"
                        );
                    }

                    const accessToken =
                        await createGoogleAccessToken(
                            serviceAccount
                        );

                    for (
                        const device
                        of devices
                    ) {
                        const sent =
                            await sendInvitationPush(
                                serviceAccount,
                                accessToken,
                                device,
                                invitationId,
                                listId,
                                list.name,
                                actorDisplayName
                            );

                        if (sent) {
                            pushSent += 1;
                        }
                    }
                }
            } catch (pushError) {
                console.error(
                    "JSimpleList invitation push processing failed",
                    pushError
                );
            }
        } else {
            console.warn(
                "JSimpleList invitation push skipped: server push configuration missing"
            );
        }

        console.log(
            "JSimpleList invitation completed",
            {
                pushTargets,
                pushSent,
            }
        );

        return Response.json(
            {
                invitationId,
                email,
                listId,
                pushTargets,
                pushSent,
            },
            { status: 200 }
        );
    } catch (error) {
        console.error(
            "JSimpleList invitation failed",
            error
        );

        return Response.json(
            {
                error:
                    "Could not send invitation",
            },
            { status: 500 }
        );
    }
});
