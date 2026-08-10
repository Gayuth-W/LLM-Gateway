-- =====================================================================
-- Console operators.
--
-- Deliberately separate from `teams`. A team is a MACHINE client that
-- calls /v1/** with a long-lived API key. An admin_user is a HUMAN who
-- logs in to /admin/** and gets a short-lived JWT. Conflating the two
-- would force API consumers into a login/refresh loop for every
-- completion call, so the two auth surfaces stay independent.
-- =====================================================================

CREATE TABLE admin_users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,          -- bcrypt $2a$, 60 chars
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_users_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);

-- One seeded operator per role so `docker-compose up` gives a working login
-- immediately. These credentials are intentionally well-known for local
-- testing, exactly like the seeded team keys in V2. Rotate before any
-- deployment that is reachable from outside localhost.
--
--   admin    / admin123      ADMIN     everything, including issuing API keys
--   operator / operator123   OPERATOR  reads + limit/budget/alert changes
--   viewer   / viewer123     VIEWER    reads only
INSERT INTO admin_users (username, password_hash, role) VALUES
    ('admin',    '$2a$10$wtBmkOKVzyqDs3cz6Uy.eezVyAnnszPbCJAc9SVUc2O1j.sOJDHRa', 'ADMIN'),
    ('operator', '$2a$10$oaSLpyVtKzHhBeqGltzacuGav1SFxU/gQ6554xDPHO5J5XwNVl62.', 'OPERATOR'),
    ('viewer',   '$2a$10$qea5lKf1bFgcpqmoJTXE/uoe1luWw3Z.s2nzxMbaqQuOTahy2nVGW', 'VIEWER');
