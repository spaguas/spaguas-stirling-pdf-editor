CREATE TABLE IF NOT EXISTS community_guard (id INTEGER PRIMARY KEY);
MERGE INTO community_guard KEY(id) VALUES (1);
CREATE TABLE IF NOT EXISTS community_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(2048) NOT NULL
);
CREATE TABLE IF NOT EXISTS community_users (
    id VARCHAR(80) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    user_role VARCHAR(10) NOT NULL CHECK (user_role IN ('ADMIN', 'USER')),
    enabled BOOLEAN NOT NULL,
    must_change_password BOOLEAN NOT NULL,
    credential_version INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS community_teams (
    id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL UNIQUE,
    can_process_pdf BOOLEAN NOT NULL,
    members_can_view_directory BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS community_memberships (
    team_id VARCHAR(80) NOT NULL REFERENCES community_teams(id) ON DELETE CASCADE,
    user_id VARCHAR(80) NOT NULL REFERENCES community_users(id) ON DELETE CASCADE,
    member_role VARCHAR(10) NOT NULL CHECK (member_role IN ('OWNER', 'MEMBER')),
    PRIMARY KEY (team_id, user_id)
);
CREATE INDEX IF NOT EXISTS community_memberships_by_user ON community_memberships(user_id);
