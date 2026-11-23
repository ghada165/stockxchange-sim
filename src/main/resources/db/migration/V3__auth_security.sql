-- Verrouillage temporaire apres trop d'echecs de connexion
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;

-- Version des jetons du compte, recopiee dans chaque JWT (claim "ver").
-- L'incrementer (changement de mot de passe, de role, suspension) invalide
-- d'un coup tous les jetons deja emis, pourtant non expires.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- Liste de revocation: jetons invalides par une deconnexion (claim "jti").
-- Une ligne n'est utile que jusqu'a l'expiration naturelle du jeton.
CREATE TABLE revoked_tokens (
    jti         VARCHAR(36)     PRIMARY KEY,
    username    VARCHAR(64)     NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    revoked_at  TIMESTAMP       NOT NULL
);
CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
