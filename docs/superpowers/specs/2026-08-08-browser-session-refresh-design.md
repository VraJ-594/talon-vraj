# Browser-session authentication refresh design

## Status and scope

Approved on 2026-08-08 for the Talon demo authentication path. This change restores an authenticated
session after a page refresh in the current browser session. It does not add persistent “remember
me” login, OAuth, 2FA, password reset, or browser-storage token persistence.

## Decision

The access JWT remains only in the frontend `ApiClient` memory. The opaque refresh token remains in
an HttpOnly, Secure, SameSite=Strict cookie scoped to `/api/v1/auth`. The cookie has no `Max-Age` or
`Expires`, making it a browser-session cookie. No token or authenticated identity is written to
Local Storage or Session Storage.

The backend adds:

- `POST /api/v1/auth/refresh`, which reads the refresh cookie, hashes it, validates the persisted
  session/account/workspace state, atomically consumes the old session, creates a child session in
  the same family, and returns a new access JWT plus the normal session projection. A newly rotated
  refresh cookie is returned.
- `POST /api/v1/auth/logout`, which revokes the refresh-token family when a valid cookie is present
  and always expires the browser cookie. The response is idempotent and reveals no token state.

Login uses the same browser-session cookie attributes. The refresh-token database lifetime remains
seven days as a server-side safety bound; absence of the browser-session cookie ends client access
earlier. Browsers configured to restore session cookies may preserve them, because reliable
server-side browser-close notification does not exist.

## Runtime flow

On application bootstrap, the frontend calls `restoreSession()`. If no access JWT exists, the HTTP
gateway calls `/api/v1/auth/refresh` with credentials included. A valid response installs the new
access JWT in memory and returns the session to React. An absent, expired, revoked, or replayed
refresh token returns an unauthenticated result and clears local in-memory state. Network/service
failures remain distinguishable from an ordinary signed-out state.

Logout calls the backend before clearing the in-memory JWT/session. Whether the backend reports an
already-absent session or a valid revocation, the frontend ends signed out and the cookie is
expired.

## Security and consistency

- Refresh tokens are never returned in JSON, logged, or stored reversibly in PostgreSQL.
- Rotation is one database transaction. Only one concurrent consumer can use a refresh session;
  reuse revokes the token family and fails closed.
- Active user and workspace membership are rechecked before issuing a new access JWT.
- Login, refresh, and logout use identical cookie name/path/Secure/HttpOnly/SameSite attributes.
- The refresh and logout routes are public at the Spring Security filter layer because they
  authenticate through the opaque cookie; application logic remains the authorization boundary.
- Error responses use stable safe codes and never disclose whether a raw token once existed.

## Verification

Backend tests cover successful rotation, expiry/revocation/replay, inactive account or membership,
atomic persistence, cookie attributes, logout revocation, cookie clearing, and anonymous endpoint
access. Frontend tests cover reload restoration without browser storage, rotated access-token
installation, signed-out restoration, network errors, and server logout. Final gates are the full
Maven verification, frontend lint/tests/build, and a manual login → refresh → reload → logout smoke.
