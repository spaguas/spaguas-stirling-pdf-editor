# Docker Setup for Stirling-PDF

This directory contains the organized Docker configurations for the split frontend/backend architecture.

## Community accounts, teams and permissions

Use the `community` flavor for independent local accounts, teams and permissions.
The [Community deployment guide (Portuguese)](COMMUNITY.md) covers the image build,
H2 migration, Nginx at `/editor-pdf`, validation and rollback. The default production
configuration continues to select the original edition.

## Using Taskfile (Recommended)

All Docker commands can be run from the project root using [Task](https://taskfile.dev/):

```bash
task docker:build            # Build standard image
task docker:build:fat        # Build fat image (all features)
task docker:build:ultra-lite # Build ultra-lite image
task docker:build:frontend   # Build frontend-only image
task docker:build:engine     # Build engine image
task docker:up               # Start standard compose stack
task docker:up:fat           # Start fat compose stack
task docker:up:ultra-lite    # Start ultra-lite compose stack
task docker:down             # Stop all running stacks
task docker:logs             # Tail compose logs
```

## Directory Structure

```
docker/
├── backend/           # Backend Docker files
│   ├── Dockerfile            # Standard backend
│   ├── Dockerfile.ultra-lite # Minimal backend
│   └── Dockerfile.fat        # Full-featured backend
├── frontend/          # Frontend Docker files
│   ├── Dockerfile     # React/Vite frontend with nginx
│   ├── nginx.conf     # Nginx configuration
│   └── entrypoint.sh  # Dynamic backend URL setup
└── compose/           # Docker Compose files
    ├── docker-compose.yml           # Standard setup
    ├── docker-compose.ultra-lite.yml # Ultra-lite setup
    └── docker-compose.fat.yml       # Full-featured setup
```

## Usage

### Separate Containers (Recommended)

From the project root directory:

```bash
# Standard version
docker-compose -f docker/compose/docker-compose.yml up --build

# Ultra-lite version
docker-compose -f docker/compose/docker-compose.ultra-lite.yml up --build

# Fat version
docker-compose -f docker/compose/docker-compose.fat.yml up --build
```

### Production on `/editor-pdf`

The single-container production setup binds the app to loopback port `8080` and
configures Spring to serve it under `/editor-pdf`. Persistent application data
is stored under `/mnt/apps/stirling-pdf` by default. On the server, create the
directories and start it from the repository root:

```bash
sudo mkdir -p /mnt/apps/stirling-pdf/{data,config,logs,customFiles}
docker compose -f docker/compose/docker-compose.production-editor-pdf.yml up -d --build
```

Set `STIRLING_DATA_ROOT` in the environment or a Compose `.env` file to use a
different directory. These bind mounts store configuration, logs, tessdata and
custom files on Alfresco's filesystem, but do not relocate Docker images or the
container writable layer. Check where Docker actually stores those with:

```bash
docker info --format 'DockerRootDir={{.DockerRootDir}}'
df -hT /mnt/apps /mnt/apps/docker
docker system df
```

If `DockerRootDir` is not on `/mnt/apps`, configure Docker's daemon `data-root`
on that filesystem before building/running the image. Preserve any existing
`/etc/docker/daemon.json` settings, stop Docker before migrating existing data,
and verify `docker info` and the filesystem with `df` after restarting. Do not
remove the old Docker data until containers and images are confirmed healthy.
If overlay mounts appear to use a different filesystem from `DockerRootDir`,
inspect their mount sources before assuming which filesystem holds the image and
container layers.

Install the Nginx snippet and add its `include` inside the existing HTTPS
`server` block (not inside another `location`) of the host virtual host:

```bash
sudo install -m 644 docker/compose/nginx-editor-pdf.conf /etc/nginx/snippets/stirling-editor-pdf.conf
```

Add this line inside the HTTPS `server { ... }` block:

```nginx
include /etc/nginx/snippets/stirling-editor-pdf.conf;
```

Then validate/reload Nginx and test the upstream and public URL:

```bash
sudo nginx -t && sudo systemctl reload nginx
curl -fsS http://127.0.0.1:8080/editor-pdf/api/v1/info/status
curl -I https://YOUR-DOMAIN/editor-pdf/
```

The `proxy_pass` intentionally has no URI or trailing slash, so Nginx preserves
the `/editor-pdf` prefix required by `SYSTEM_ROOTURIPATH`. Do not replace it
with `proxy_pass http://127.0.0.1:8080/;`, which strips the prefix. The Compose
mapping keeps port `8080` private to the host; for an Nginx container, put both
containers on the same Docker network and use `proxy_pass http://stirling-pdf:8080`
instead.

#### Portal API routing under `/editor-pdf`

The editor and its portal share `getApiBaseUrl()` from
`@app/services/apiClientConfig`. Spring injects the deployment's context path
through `window.STIRLING_PDF_API_BASE_URL`; the portal normalizes its trailing
slash before joining API paths. This applies to JSON, form, multipart, and
binary requests, including member and team management.

Publish the updated `frontend/editor/src/portal/api/localBackend.ts` source and
rebuild the application image using the production build commands below. A
container restart alone does not update the embedded frontend bundle.

After the new image is healthy:

1. Reload the browser with its cache disabled and sign in with the current
   password. A password change ends the previous session.
2. Open Members and inspect the browser's Network panel. Its roster request
   must target `/editor-pdf/api/v1/proprietary/ui-data/admin-settings` and
   return HTTP 200 with JSON.
3. Remove the temporary root `location /api/v1/` that proxies to Stirling, or
   its `stirling-editor-pdf-api-compat.conf` include, from this domain's
   `server` block. Preserve any routes belonging to other applications.
4. Run `sudo nginx -t && sudo systemctl reload nginx`, then reload Members
   and verify the roster, teams, and member actions again.

The permanent Nginx configuration needs only
`include /etc/nginx/snippets/stirling-editor-pdf.conf;` for this application.
The `/api/v1/` root path remains available for the domain's other applications.

#### Building behind an outbound proxy

For a server that accesses the internet through `http://10.1.6.20:80`, use the
optional build override below. Docker passes the proxy to command-line download
tools; Gradle also needs Java proxy properties. The private properties file is
mounted only during the two Gradle build instructions using a BuildKit secret.

From the repository root, prepare the file outside the Docker build context:

```bash
mkdir -p "$HOME/.config/stirling-pdf"
install -m 600 docker/compose/gradle-proxy.properties.example \
  "$HOME/.config/stirling-pdf/gradle-proxy.properties"
export STIRLING_BUILD_PROXY=http://10.1.6.20:80
export STIRLING_GRADLE_PROXY_FILE="$HOME/.config/stirling-pdf/gradle-proxy.properties"
```

The example assumes the proxy does not require authentication. If authentication
is required, configure the HTTP and HTTPS `systemProp.*.proxyUser` and
`systemProp.*.proxyPassword` entries in the private file. Do not commit credentials.
For another proxy, update both the exported URL and the host/port in that file.
Gradle's `proxyHost` values contain only the host, without a scheme or port.

Validate, build, and start the application:

```bash
docker compose \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.build-proxy.yml config --quiet
docker compose --progress plain \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.build-proxy.yml build stirling-pdf
docker compose -f docker/compose/docker-compose.production-editor-pdf.yml \
  up -d --no-build --wait --wait-timeout 300
curl -fsS http://127.0.0.1:8080/editor-pdf/api/v1/info/status
```

The override configures outbound access during the build. The application still
uses the existing Nginx snippet for inbound requests on `/editor-pdf`. It does
not configure the Docker daemon's image pulls; if those fail, the daemon or
builder needs its own proxy configuration. A `407 Proxy Authentication Required`
response means the proxy requires credentials; a Java certificate trust error
requires trusting the organization's CA in the build environment.

Run `task docker:check` to validate both production Compose configurations.

## Access Points

- **Frontend**: http://localhost:3000
- **Backend API (debugging)**: http://localhost:8080 (TODO: Remove in production)
- **Backend API (via frontend)**: http://localhost:3000/api/*

## Configuration

- **Backend URL**: Set `VITE_API_BASE_URL` environment variable for custom backend locations
- **Custom Ports**: Modify port mappings in docker-compose files
- **Memory Limits**: Adjust memory limits per variant (2G ultra-lite, 4G standard, 6G fat)

### [Google Drive Integration](https://developers.google.com/workspace/drive/picker/guides/overview)

- **VITE_GOOGLE_DRIVE_CLIENT_ID**: [OAuth 2.0 Client ID](https://console.cloud.google.com/auth/clients/create)
- **VITE_GOOGLE_DRIVE_API_KEY**: [Create New API](https://console.cloud.google.com/apis)
- **VITE_GOOGLE_DRIVE_APP_ID**: This is your [project number](https://console.cloud.google.com/iam-admin/settings) in the GoogleCloud Settings

## Development vs Production

- **Development**: Keep backend port 8080 exposed for debugging
- **Production**: Remove backend port exposure, use only frontend proxy

