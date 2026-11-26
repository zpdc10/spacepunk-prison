# Server wiki

Player-facing documentation for every system, built with MkDocs Material. Pages live in `docs/`; the
navigation is in `mkdocs.yml`. Update a system's page in the same PR that changes the system, using the real numbers
from its config file. CI runs `mkdocs build --strict`, so broken links fail the build.

Preview locally: `pip install -r requirements.txt && mkdocs serve`.

## Hosting

The wiki runs in its own Docker container on the Unraid server, next to the Minecraft container. It clones `main` on
every start, builds the site and serves it on port 8080 (mapped to 8085 on the host):

```
docker run -d --name spacepunk-wiki --restart unless-stopped -p 8085:8080 \
  -v /mnt/user/appdata/spacepunk-wiki/pip-cache:/root/.cache/pip \
  python:3.12-alpine sh -c "apk add --no-cache git >/dev/null && rm -rf /src && \
  git clone --depth 1 https://github.com/zpdc10/spacepunk-prison /src && exec sh /src/wiki/serve.sh"
```

Deploy a wiki change: merge to `main`, then `docker restart spacepunk-wiki`.
