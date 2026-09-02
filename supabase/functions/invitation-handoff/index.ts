import { createClient } from "npm:@supabase/supabase-js@2";

const allowedBrowserOrigin =
    "https://jslist.jobsheet.com.au";

type HandoffAction =
    | "inspect"
    | "join";

type HandoffDelivery = {
    invitation_id: string;
    recipient_email: string;
    handoff_expires_at: string | null;
};

type InvitationRecord = {
    id: string;
    list_id: string;
    invited_email: string;
    invited_by: string;
    accepted_at: string | null;
    cancelled_at: string | null;
};

type ListRecord = {
    id: string;
    name: string;
    kind: string;
    deleted_at: string | null;
};

type ActiveHandoff = {
    delivery: HandoffDelivery;
    invitation: InvitationRecord;
    list: ListRecord;
    inviterDisplayName: string;
};

function corsHeaders(): Record<string, string> {
    return {
        "Access-Control-Allow-Origin":
            allowedBrowserOrigin,

        "Access-Control-Allow-Headers":
            "authorization, x-client-info, apikey, content-type",

        "Access-Control-Allow-Methods":
            "POST, OPTIONS",

        "Vary":
            "Origin",
    };
}

function jsonResponse(
    body: unknown,
    status = 200
): Response {
    return Response.json(
        body,
        {
            status,
            headers:
                corsHeaders(),
        }
    );
}

function base64UrlTokenLooksValid(
    value: string
): boolean {
    /*
     * 32 random bytes encoded without Base64 padding produce
     * a 43-character URL-safe token.
     */
    return /^[A-Za-z0-9_-]{43}$/.test(value);
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

function maskEmail(
    email: string
): string {
    const parts =
        email.split("@");

    if (parts.length !== 2) {
        return "your invited email address";
    }

    const local =
        parts[0];

    const domain =
        parts[1];

    if (!local || !domain) {
        return "your invited email address";
    }

    const visibleLocal =
        local.length === 1
            ? local
            : local.slice(0, 1) +
                "*".repeat(
                    Math.min(
                        local.length - 1,
                        6
                    )
                );

    return `${visibleLocal}@${domain}`;
}

function fallbackDisplayName(
    email: string | null | undefined
): string {
    if (!email) {
        return "A JSimpleList user";
    }

    const localPart =
        email
            .split("@", 1)[0]
            .trim();

    if (!localPart) {
        return "A JSimpleList user";
    }

    return `${localPart.slice(0, 49)}@`;
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

async function resolveActiveHandoff(
    adminClient: any,
    handoffToken: string
): Promise<ActiveHandoff | null> {
    if (
        !base64UrlTokenLooksValid(
            handoffToken
        )
    ) {
        return null;
    }

    const tokenHash =
        await sha256Hex(
            handoffToken
        );

    const {
        data: delivery,
        error: deliveryError,
    } =
        await adminClient
            .schema("jsimplelist")
            .from("invitation_delivery")
            .select(
                "invitation_id,recipient_email,handoff_expires_at"
            )
            .eq(
                "handoff_token_hash",
                tokenHash
            )
            .maybeSingle();

    if (
        deliveryError ||
        !delivery
    ) {
        return null;
    }

    const typedDelivery =
        delivery as HandoffDelivery;

    if (
        !typedDelivery.handoff_expires_at ||
        Date.parse(
            typedDelivery.handoff_expires_at
        ) <= Date.now()
    ) {
        return null;
    }

    const {
        data: invitation,
        error: invitationError,
    } =
        await adminClient
            .schema("jsimplelist")
            .from("list_invitations")
            .select(
                "id,list_id,invited_email,invited_by,accepted_at,cancelled_at"
            )
            .eq(
                "id",
                typedDelivery.invitation_id
            )
            .maybeSingle();

    if (
        invitationError ||
        !invitation
    ) {
        return null;
    }

    const typedInvitation =
        invitation as InvitationRecord;

    if (
        typedInvitation.accepted_at !== null ||
        typedInvitation.cancelled_at !== null
    ) {
        return null;
    }

    if (
        typedInvitation
            .invited_email
            .trim()
            .toLowerCase() !==
        typedDelivery
            .recipient_email
            .trim()
            .toLowerCase()
    ) {
        console.error(
            "JSimpleList handoff recipient mismatch"
        );

        return null;
    }

    const {
        data: list,
        error: listError,
    } =
        await adminClient
            .schema("jsimplelist")
            .from("lists")
            .select(
                "id,name,kind,deleted_at"
            )
            .eq(
                "id",
                typedInvitation.list_id
            )
            .maybeSingle();

    if (
        listError ||
        !list
    ) {
        return null;
    }

    const typedList =
        list as ListRecord;

    if (
        typedList.deleted_at !== null
    ) {
        return null;
    }

    const {
        data: inviterProfile,
    } =
        await adminClient
            .schema("jsimplelist")
            .from("profiles")
            .select(
                "display_name"
            )
            .eq(
                "user_id",
                typedInvitation.invited_by
            )
            .maybeSingle();

    let inviterDisplayName =
        typeof inviterProfile
            ?.display_name ===
            "string" &&
        inviterProfile
            .display_name
            .trim() !== ""
            ? inviterProfile
                .display_name
                .trim()
            : "";

    if (!inviterDisplayName) {
        const {
            data: inviterUser,
        } =
            await adminClient
                .auth
                .admin
                .getUserById(
                    typedInvitation.invited_by
                );

        inviterDisplayName =
            fallbackDisplayName(
                inviterUser?.user?.email
            );
    }

    return {
        delivery:
            typedDelivery,

        invitation:
            typedInvitation,

        list:
            typedList,

        inviterDisplayName,
    };
}

function safeHandoffContext(
    handoff: ActiveHandoff
) {
    return {
        state:
            "active",

        inviterDisplayName:
            handoff.inviterDisplayName,

        listName:
            handoff.list.name,

        listKind:
            handoff.list.kind,

        maskedEmail:
            maskEmail(
                handoff.delivery
                    .recipient_email
            ),

        expiresAt:
            handoff.delivery
                .handoff_expires_at,
    };
}

Deno.serve(
    async (
        req: Request
    ) => {
        const origin =
            req.headers.get(
                "Origin"
            );

        if (
            origin &&
            origin !== allowedBrowserOrigin
        ) {
            return jsonResponse(
                {
                    error:
                        "Origin not allowed",
                },
                403
            );
        }

        if (
            req.method === "OPTIONS"
        ) {
            return new Response(
                null,
                {
                    status:
                        204,

                    headers:
                        corsHeaders(),
                }
            );
        }

        if (
            req.method !== "POST"
        ) {
            return jsonResponse(
                {
                    error:
                        "Method not allowed",
                },
                405
            );
        }

        const supabaseUrl =
            Deno.env.get(
                "SUPABASE_URL"
            );

        const serviceRoleKey =
            Deno.env.get(
                "SUPABASE_SERVICE_ROLE_KEY"
            );

        const publishableKeysJson =
            Deno.env.get(
                "SUPABASE_PUBLISHABLE_KEYS"
            );

        if (
            !supabaseUrl ||
            !serviceRoleKey ||
            !publishableKeysJson
        ) {
            return jsonResponse(
                {
                    error:
                        "Server configuration error",
                },
                500
            );
        }

        try {
            const body =
                await req.json();

            const action =
                typeof body.action ===
                    "string"
                    ? body.action.trim()
                    : "";

            const handoffToken =
                typeof body.handoff ===
                    "string"
                    ? body.handoff.trim()
                    : "";

            if (
                action !== "inspect" &&
                action !== "join"
            ) {
                return jsonResponse(
                    {
                        error:
                            "Unsupported handoff action",
                    },
                    400
                );
            }

            if (
                !base64UrlTokenLooksValid(
                    handoffToken
                )
            ) {
                return jsonResponse(
                    {
                        state:
                            "invalid",
                    },
                    404
                );
            }

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

                            detectSessionInUrl:
                                false,
                        },
                    }
                );

            const handoff =
                await resolveActiveHandoff(
                    adminClient,
                    handoffToken
                );

            if (!handoff) {
                return jsonResponse(
                    {
                        state:
                            "invalid",
                    },
                    404
                );
            }

            if (
                action === "inspect"
            ) {
                return jsonResponse(
                    safeHandoffContext(
                        handoff
                    )
                );
            }

            const publishableKeys =
                JSON.parse(
                    publishableKeysJson
                );

            const publishableKey =
                publishableKeys.default;

            if (!publishableKey) {
                return jsonResponse(
                    {
                        error:
                            "Server configuration error",
                    },
                    500
                );
            }

            /*
             * Explicit Join is the scanner-safe redemption boundary.
             *
             * Merely opening the emailed handoff performs only `inspect`.
             * A deliberate Join request uses the opaque handoff to bootstrap
             * the invited Supabase identity, but membership authority still
             * remains accept_list_invitation().
             */
            const {
                data: generatedLink,
                error: generatedLinkError,
            } =
                await adminClient
                    .auth
                    .admin
                    .generateLink({
                        type:
                            "magiclink",

                        email:
                            handoff.delivery
                                .recipient_email,
                    });

            const tokenHash =
                generatedLink
                    ?.properties
                    ?.hashed_token
                    ?.trim();

            if (
                generatedLinkError ||
                !tokenHash
            ) {
                console.error(
                    "JSimpleList handoff Auth bootstrap generation failed",
                    generatedLinkError
                );

                return jsonResponse(
                    {
                        error:
                            "The invitation could not be prepared",
                    },
                    500
                );
            }

            /*
             * The generated Auth credential never leaves this Edge Function.
             * It is immediately exchanged for the invited user's session.
             */
            const authClient =
                createClient(
                    supabaseUrl,
                    publishableKey,
                    {
                        auth: {
                            persistSession:
                                false,

                            autoRefreshToken:
                                false,

                            detectSessionInUrl:
                                false,
                        },
                    }
                );

            const {
                data: verification,
                error: verificationError,
            } =
                await authClient
                    .auth
                    .verifyOtp({
                        token_hash:
                            tokenHash,

                        type:
                            "email",
                    });

            const session =
                verification
                    ?.session;

            if (
                verificationError ||
                !session
            ) {
                console.error(
                    "JSimpleList handoff Auth bootstrap verification failed",
                    verificationError
                );

                return jsonResponse(
                    {
                        error:
                            "The invitation could not be verified",
                    },
                    500
                );
            }

            /*
             * Final product authority remains the existing invitation RPC.
             * The newly authenticated session is forwarded as the caller so
             * auth.uid() and auth.jwt()->>'email' are checked by PostgreSQL.
             */
            const authenticatedClient =
                createClient(
                    supabaseUrl,
                    publishableKey,
                    {
                        global: {
                            headers: {
                                Authorization:
                                    `Bearer ${session.access_token}`,
                            },
                        },

                        auth: {
                            persistSession:
                                false,

                            autoRefreshToken:
                                false,

                            detectSessionInUrl:
                                false,
                        },
                    }
                );

            const {
                data: acceptedListId,
                error: acceptanceError,
            } =
                await authenticatedClient
                    .schema("jsimplelist")
                    .rpc(
                        "accept_list_invitation",
                        {
                            target_invitation_id:
                                handoff.invitation.id,
                        }
                    );

            if (
                acceptanceError ||
                typeof acceptedListId !==
                    "string" ||
                acceptedListId.trim() ===
                    ""
            ) {
                console.error(
                    "JSimpleList handoff invitation acceptance failed",
                    acceptanceError
                );

                return jsonResponse(
                    {
                        error:
                            "The invitation could not be accepted",
                    },
                    409
                );
            }

            /*
             * Successful acceptance deletes invitation_delivery through the
             * existing database trigger. That consumes this handoff without
             * treating the token itself as membership authority.
             */
            return jsonResponse(
                {
                    state:
                        "accepted",

                    listId:
                        acceptedListId,

                    session: {
                        accessToken:
                            session.access_token,

                        refreshToken:
                            session.refresh_token,

                        expiresAt:
                            session.expires_at ??
                            null,
                    },
                }
            );
        } catch (error) {
            console.error(
                "JSimpleList invitation handoff failed",
                error
            );

            return jsonResponse(
                {
                    error:
                        "Could not continue invitation",
                },
                500
            );
        }
    }
);
