CREATE UNIQUE INDEX ux_app_users_email_lower ON app_users (LOWER(email));
CREATE UNIQUE INDEX ux_app_users_username_lower ON app_users (LOWER(username));
