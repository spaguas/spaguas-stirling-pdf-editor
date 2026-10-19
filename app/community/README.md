# Community identity

This module is an independent implementation of local accounts, teams and permissions,
covered by the repository's [MIT license](../../LICENSE). The `community` build does not
include the proprietary or SaaS modules. Existing commercial directories retain their licenses.

Administrators manage accounts, global roles, team policy and all memberships. Team owners
manage membership in their own teams. Members see only their teams and, when permitted,
their directories. PDF processing requires an enabled team with processing permission;
server administrators have processing access. Processing permission is additive across teams.
There are no seat counts or commercial entitlements in this module.

The identity database is H2 at `/configs/community/identity.mv.db` in Docker. It is separate
from the commercial edition's database. Writes serialize in database transactions to protect
the last active administrator and team owner. Sessions are memory-only and restart requires
sign-in. Password, role and enabled-state changes revoke existing sessions and internal API
credentials. Team permission changes take effect on the next request. Browser sessions use
CSRF protection and BCrypt passwords. First sign-in and administrator password resets require
changing the password. Use HTTPS at the external reverse proxy.

The frontend resolves account identity before importing the editor and uses per-account
IndexedDB databases. Login, logout and password changes reload all open Community tabs.
Existing browser databases are retained but are not automatically assigned to another account.
Teams do not provide shared document storage; documents remain in each user's browser.

Production setup, migration and rollback: [Docker guide](../../docker/COMMUNITY.md).

```bash
task backend:check:community
task frontend:check:community
```

For development, set `COMMUNITY_INITIAL_ADMIN_PASSWORD` to a private initial password
(at least 12 characters), then run `task backend:dev:community` and
`task frontend:dev:community` in separate terminals. Use a dedicated `STIRLING_BASE_PATH`
for isolated instances. Bootstrap credentials are ignored after accounts exist.
