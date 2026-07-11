DO $$
DECLARE
    app_table TEXT;
BEGIN
    FOREACH app_table IN ARRAY ARRAY[
        'app_users', 'users', 'friendships', 'direct_messages', 'servers',
        'server_members', 'server_invitations', 'channels', 'messages'
    ]
    LOOP
        IF to_regclass('public.' || app_table) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', app_table);
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
                EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon', app_table);
            END IF;
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
                EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM authenticated', app_table);
            END IF;
        END IF;
    END LOOP;
END $$;
