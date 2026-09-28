# Docker Setup for Stirling-PDF

This directory contains the organized Docker configurations for the split frontend/backend architecture.

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
This host reports overlay mounts at 100% on `/`, so verify the actual Docker
root and mount backing those overlay directories before assuming the `/mnt/apps`
path is already in use for Docker storage.

Include `docker/compose/nginx-editor-pdf.conf` inside the HTTPS `server` block
of the host Nginx virtual host, then validate and reload Nginx:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

The `proxy_pass` intentionally has no trailing slash, so Nginx preserves the
`/editor-pdf` prefix required by `SYSTEM_ROOTURIPATH`. The Compose mapping keeps
port `8080` private to the host; for an Nginx container, put both containers on
the same Docker network and use `proxy_pass http://stirling-pdf:8080` instead.


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

