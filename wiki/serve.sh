#!/bin/sh
# Builds the wiki from this checkout and serves it on port 8080. Used by the spacepunk-wiki Docker container,
# which clones the repo's main branch on every start, so "docker restart spacepunk-wiki" deploys the latest wiki.
set -e
cd "$(dirname "$0")"
pip install --quiet --disable-pip-version-check -r requirements.txt
NO_MKDOCS_2_WARNING=1 mkdocs build --clean -d /tmp/site
exec python3 -m http.server 8080 --directory /tmp/site
