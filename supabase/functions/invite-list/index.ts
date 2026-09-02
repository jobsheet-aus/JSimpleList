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

type RecipientMaturity =
    | "ACTIVE_ANDROID"
    | "KNOWN_USER"
    | "GREEN";

type RecipientClassification = {
    recipient_maturity: RecipientMaturity;
    auth_user_id: string | null;
    push_device_count: number;
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

function randomOpaqueToken(): string {
    const bytes =
        crypto.getRandomValues(
            new Uint8Array(32)
        );

    return base64UrlEncodeBytes(bytes);
}

async function sha256Hex(
    value: string
): Promise<string> {
    const digest =
        await crypto.subtle.digest(
            "SHA-256",
            new TextEncoder().encode(value)
        );

    return Array.from(
        new Uint8Array(digest)
    )
        .map(
            (byte) =>
                byte
                    .toString(16)
                    .padStart(2, "0")
        )
        .join("");
}

function escapeHtml(
    value: string
): string {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

async function sendGreenInvitationEmail(
    resendApiKey: string,
    recipientEmail: string,
    inviterDisplayName: string,
    listName: string,
    handoffUrl: string
): Promise<void> {
    const safeInviter =
        escapeHtml(inviterDisplayName);

    const safeList =
        escapeHtml(listName);

    const safeHandoffUrl =
        escapeHtml(handoffUrl);

    const subject =
        `${inviterDisplayName} invited you to join ${listName} in JSimpleList`;

    const response =
        await fetch(
            "https://api.resend.com/emails",
            {
                method: "POST",
                headers: {
                    Authorization:
                        `Bearer ${resendApiKey}`,
                    "Content-Type":
                        "application/json",
                },
                body: JSON.stringify({
                    from:
                        "JSimpleList <jsimplelist@jobsheet.com.au>",

                    to: [
                        recipientEmail,
                    ],

                    subject,

                    html: `
                        <div style="font-family:Arial,sans-serif;line-height:1.5;color:#1f2937">
                            <h2 style="margin-bottom:12px">You've been invited to JSimpleList</h2>

                            <p>
                                ${safeInviter} invited you to join
                                <strong>${safeList}</strong>.
                            </p>

                            <p>
                                JSimpleList is a simple app for keeping lists
                                on your phone and sharing selected lists privately.
                            </p>

                            <p style="margin:24px 0">
                                <a
                                    href="${safeHandoffUrl}"
                                    style="display:inline-block;padding:12px 18px;background:#1877c9;color:#ffffff;text-decoration:none;border-radius:6px"
                                >
                                    Open shared list
                                </a>
                            </p>

                            <p>
                                If JSimpleList is not installed yet, this link
                                will show you how to get it and continue.
                            </p>

                            <p>
                                To protect the shared list, JSimpleList will
                                verify the invited email address before access
                                is granted.
                            </p>

                            <p>
                                This invitation link expires after 24 hours.
                            </p>

                            <p>
                                If you were not expecting this invitation,
                                you can ignore this email.
                            </p>
                        </div>
                    `,
                }),
            }
        );

    if (!response.ok) {
        const details =
            await response.text();

        throw new Error(
            "Resend invitation email failed: " +
            `${response.status} ${details}`
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

    const resendApiKey =
        Deno.env.get(
            "RESEND_API_KEY"
        );

    if (
        !supabaseUrl ||
        !publishableKeysJson ||
        !serviceRoleKey
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
         * Invitation authority and invitation delivery are deliberately
         * separate.
         *
         * replace_list_invitation() has already committed the authoritative
         * product invitation. JSimpleList-owned evidence now decides the
         * least-friction delivery path.
         */
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
            data: classification,
            error: classificationError,
        } =
            await adminClient
                .schema("jsimplelist")
                .rpc(
                    "classify_invitation_recipient",
                    {
                        target_email: email,
                    }
                )
                .single();

        if (
            classificationError ||
            !classification
        ) {
            console.error(
                "JSimpleList recipient classification failed",
                classificationError
            );

            return Response.json(
                {
                    error:
                        "Invitation was created, but delivery could not be prepared",
                },
                { status: 500 }
            );
        }

        const recipientClassification =
            classification as RecipientClassification;

        const recipientMaturity =
            recipientClassification
                .recipient_maturity;

        if (
            recipientMaturity !== "ACTIVE_ANDROID" &&
            recipientMaturity !== "KNOWN_USER" &&
            recipientMaturity !== "GREEN"
        ) {
            console.error(
                "JSimpleList recipient classification returned unsupported maturity",
                recipientMaturity
            );

            return Response.json(
                {
                    error:
                        "Invitation was created, but delivery could not be prepared",
                },
                { status: 500 }
            );
        }

        /*
         * ACTIVE_ANDROID:
         *     push immediately, no immediate email
         *
         * KNOWN_USER:
         *     no immediate email; invitation remains discoverable in-app and
         *     receives a delayed reminder only if still pending
         *
         * GREEN:
         *     purpose-specific onboarding email with opaque handoff;
         *     invited-email verification begins only after handoff continuation
         */
        if (
            recipientMaturity === "ACTIVE_ANDROID" ||
            recipientMaturity === "KNOWN_USER"
        ) {
            const immediateChannel =
                recipientMaturity === "ACTIVE_ANDROID"
                    ? "PUSH"
                    : "NONE";

            const reminderDueAt =
                new Date(
                    Date.now() +
                    24 * 60 * 60 * 1000
                ).toISOString();

            const {
                error: deliveryInsertError,
            } =
                await adminClient
                    .schema("jsimplelist")
                    .from("invitation_delivery")
                    .insert({
                        invitation_id:
                            invitationId,

                        recipient_email:
                            email,

                        recipient_maturity:
                            recipientMaturity,

                        immediate_channel:
                            immediateChannel,

                        reminder_due_at:
                            reminderDueAt,
                    });

            if (deliveryInsertError) {
                console.error(
                    "JSimpleList invitation delivery state creation failed",
                    deliveryInsertError
                );

                return Response.json(
                    {
                        error:
                            "Invitation was created, but delivery could not be prepared",
                    },
                    { status: 500 }
                );
            }
        }

        if (
            recipientMaturity === "GREEN"
        ) {
            if (!resendApiKey) {
                console.error(
                    "JSimpleList GREEN invitation requires RESEND_API_KEY"
                );

                return Response.json(
                    {
                        error:
                            "Invitation was created, but onboarding email is unavailable",
                    },
                    { status: 500 }
                );
            }

            /*
             * GREEN onboarding uses a JSimpleList-owned opaque continuation
             * identifier. The public URL contains no Supabase Auth secret.
             *
             * The token itself is returned only to the recipient in the
             * purpose-specific email. The database stores only its SHA-256
             * hash.
             */
            const handoffToken =
                randomOpaqueToken();

            const handoffTokenHash =
                await sha256Hex(
                    handoffToken
                );

            const handoffExpiresAt =
                new Date(
                    Date.now() +
                    24 * 60 * 60 * 1000
                ).toISOString();

            const handoffUrl =
                "https://jslist.jobsheet.com.au/invite?h=" +
                encodeURIComponent(
                    handoffToken
                );

            const {
                data: inviterProfile,
            } =
                await adminClient
                    .schema("jsimplelist")
                    .from("profiles")
                    .select("display_name")
                    .eq(
                        "user_id",
                        user.id
                    )
                    .maybeSingle();

            const inviterDisplayName =
                typeof inviterProfile
                    ?.display_name ===
                    "string" &&
                inviterProfile
                    .display_name
                    .trim() !== ""
                    ? inviterProfile
                        .display_name
                        .trim()
                    : fallbackDisplayName(
                        user.email
                    );

            const {
                error: deliveryInsertError,
            } =
                await adminClient
                    .schema("jsimplelist")
                    .from("invitation_delivery")
                    .insert({
                        invitation_id:
                            invitationId,

                        recipient_email:
                            email,

                        recipient_maturity:
                            "GREEN",

                        immediate_channel:
                            "ONBOARDING_EMAIL",

                        handoff_token_hash:
                            handoffTokenHash,

                        handoff_expires_at:
                            handoffExpiresAt,
                    });

            if (deliveryInsertError) {
                console.error(
                    "JSimpleList GREEN delivery state creation failed",
                    deliveryInsertError
                );

                return Response.json(
                    {
                        error:
                            "Invitation was created, but onboarding could not be prepared",
                    },
                    { status: 500 }
                );
            }

            try {
                await sendGreenInvitationEmail(
                    resendApiKey,
                    email,
                    inviterDisplayName,
                    list.name,
                    handoffUrl
                );
            } catch (emailError) {
                console.error(
                    "JSimpleList GREEN onboarding email failed",
                    emailError
                );

                return Response.json(
                    {
                        error:
                            "Invitation was created, but onboarding email could not be sent",
                    },
                    { status: 500 }
                );
            }

            const {
                error: deliveryUpdateError,
            } =
                await adminClient
                    .schema("jsimplelist")
                    .from("invitation_delivery")
                    .update({
                        onboarding_email_sent_at:
                            new Date()
                                .toISOString(),

                        updated_at:
                            new Date()
                                .toISOString(),
                    })
                    .eq(
                        "invitation_id",
                        invitationId
                    );

            if (deliveryUpdateError) {
                console.error(
                    "JSimpleList GREEN delivery state update failed",
                    deliveryUpdateError
                );
            }
        }

        let pushTargets = 0;
        let pushSent = 0;

        if (
            recipientMaturity === "ACTIVE_ANDROID" &&
            firebaseServiceAccountB64
        ) {
            try {
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
        } else if (
            recipientMaturity === "ACTIVE_ANDROID"
        ) {
            console.warn(
                "JSimpleList invitation push skipped: server push configuration missing"
            );
        }

        if (
            recipientMaturity === "ACTIVE_ANDROID"
        ) {
            const now =
                new Date().toISOString();

            const deliveryUpdate: {
                push_attempted_at: string;
                push_sent_at?: string;
                updated_at: string;
            } = {
                push_attempted_at:
                    now,

                updated_at:
                    now,
            };

            if (pushSent > 0) {
                deliveryUpdate.push_sent_at =
                    now;
            }

            const {
                error: deliveryUpdateError,
            } =
                await adminClient
                    .schema("jsimplelist")
                    .from("invitation_delivery")
                    .update(deliveryUpdate)
                    .eq(
                        "invitation_id",
                        invitationId
                    );

            if (deliveryUpdateError) {
                console.error(
                    "JSimpleList invitation delivery state update failed",
                    deliveryUpdateError
                );
            }
        }

        console.log(
            "JSimpleList invitation completed",
            {
                recipientMaturity,
                pushTargets,
                pushSent,
            }
        );

        return Response.json(
            {
                invitationId,
                email,
                listId,
                recipientMaturity,
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
