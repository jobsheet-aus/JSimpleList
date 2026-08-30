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
    const secretKeysJson =
        Deno.env.get("SUPABASE_SECRET_KEYS");

    if (!supabaseUrl || !publishableKeysJson || !secretKeysJson) {
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
        const publishableKeys = JSON.parse(publishableKeysJson);
        const secretKeys = JSON.parse(secretKeysJson);

        const publishableKey = publishableKeys.default;
        const secretKey = secretKeys.default;

        if (!publishableKey || !secretKey) {
            return Response.json(
                { error: "Server configuration error" },
                { status: 500 }
            );
        }

        /*
         * Caller-scoped client.
         *
         * The Authorization header is forwarded so auth.uid() and auth.jwt()
         * inside PostgreSQL resolve to the authenticated caller.
         */
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

        if (userError || !user) {
            return Response.json(
                { error: "Authentication required" },
                { status: 401 }
            );
        }

        /*
         * First remove all JSimpleList application data belonging to the
         * authenticated account. The RPC derives identity from auth.uid();
         * no caller-supplied user ID is accepted.
         */
        const { error: cleanupError } = await userClient
            .schema("jsimplelist")
            .rpc("delete_online_account_data");

        if (cleanupError) {
            console.error(
                "JSimpleList account data cleanup failed",
                cleanupError
            );

            return Response.json(
                { error: "Could not delete online account data" },
                { status: 500 }
            );
        }

        /*
         * Admin client exists only inside this server-side Edge Function.
         * The secret key must never be exposed to Android or the browser.
         */
        const adminClient = createClient(
            supabaseUrl,
            secretKey,
            {
                auth: {
                    persistSession: false,
                    autoRefreshToken: false,
                },
            }
        );

        const { error: deleteUserError } =
            await adminClient.auth.admin.deleteUser(user.id);

        if (deleteUserError) {
            console.error(
                "Supabase Auth user deletion failed",
                deleteUserError
            );

            return Response.json(
                {
                    error:
                        "Online account data was deleted, but account " +
                        "removal could not be completed",
                },
                { status: 500 }
            );
        }

        return Response.json(
            { deleted: true },
            { status: 200 }
        );
    } catch (error) {
        console.error("Delete account failed", error);

        return Response.json(
            { error: "Could not delete online account" },
            { status: 500 }
        );
    }
});
