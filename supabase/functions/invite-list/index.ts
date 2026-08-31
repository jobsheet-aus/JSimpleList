import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req: Request) => {
    if (req.method !== "POST") {
        return Response.json(
            { error: "Method not allowed" },
            { status: 405 }
        );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const publishableKeysJson =
        Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");

    if (!supabaseUrl || !publishableKeysJson) {
        return Response.json(
            { error: "Server configuration error" },
            { status: 500 }
        );
    }

    const authorization = req.headers.get("Authorization");

    if (!authorization) {
        return Response.json(
            { error: "Authentication required" },
            { status: 401 }
        );
    }

    try {
        const body = await req.json();

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
                { error: "List and email address are required" },
                { status: 400 }
            );
        }

        const publishableKeys =
            JSON.parse(publishableKeysJson);

        const publishableKey =
            publishableKeys.default;

        if (!publishableKey) {
            return Response.json(
                { error: "Server configuration error" },
                { status: 500 }
            );
        }

        const userClient = createClient(
            supabaseUrl,
            publishableKey,
            {
                global: {
                    headers: {
                        Authorization: authorization,
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
        } = await userClient.auth.getUser();

        if (userError || !user || !user.email) {
            return Response.json(
                { error: "Authentication required" },
                { status: 401 }
            );
        }

        if (user.email.toLowerCase() === email) {
            return Response.json(
                { error: "You are already using this account" },
                { status: 400 }
            );
        }

        const { data: list, error: listError } =
            await userClient
                .schema("jsimplelist")
                .from("lists")
                .select("id,name,owner_id")
                .eq("id", listId)
                .is("deleted_at", null)
                .single();

        if (listError || !list) {
            return Response.json(
                { error: "List not found" },
                { status: 404 }
            );
        }

        if (list.owner_id !== user.id) {
            return Response.json(
                { error: "Only the list owner can invite people" },
                { status: 403 }
            );
        }

        const { data: existingInvitation } =
            await userClient
                .schema("jsimplelist")
                .from("list_invitations")
                .select("id")
                .eq("list_id", listId)
                .ilike("invited_email", email)
                .is("accepted_at", null)
                .is("cancelled_at", null)
                .maybeSingle();

        let invitationId = existingInvitation?.id;

        if (!invitationId) {
            const { data: invitation, error: invitationError } =
                await userClient
                    .schema("jsimplelist")
                    .from("list_invitations")
                    .insert({
                        list_id: listId,
                        invited_email: email,
                        invited_by: user.id,
                        role: "member",
                    })
                    .select("id")
                    .single();

            if (invitationError || !invitation) {
                console.error(
                    "JSimpleList invitation creation failed",
                    invitationError
                );

                return Response.json(
                    { error: "Could not create invitation" },
                    { status: 500 }
                );
            }

            invitationId = invitation.id;
        }

        /*
         * Authentication is deliberately kept separate from invitation
         * semantics. The invitation row records what the authenticated
         * recipient may accept; Supabase Auth only proves control of the
         * invited email address.
         *
         * Use the normal OTP flow for every recipient so Android has one
         * verification path regardless of whether the Auth user already
         * exists.
         */
        const authClient = createClient(
            supabaseUrl,
            publishableKey,
            {
                auth: {
                    persistSession: false,
                    autoRefreshToken: false,
                },
            }
        );

        const { error: otpError } =
            await authClient.auth.signInWithOtp({
                email,
                options: {
                    shouldCreateUser: true,
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

        return Response.json(
            {
                invitationId,
                email,
                listId,
            },
            { status: 200 }
        );
    } catch (error) {
        console.error(
            "JSimpleList invitation failed",
            error
        );

        return Response.json(
            { error: "Could not send invitation" },
            { status: 500 }
        );
    }
});
