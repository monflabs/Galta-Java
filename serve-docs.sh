#!/usr/bin/env bash
#
# Copyright (c) 2019-2026 Philippe Riand
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Serves the documentation site (docs/) over HTTP for a local preview, the same
# way GitHub Pages serves it. Docsify is client-side and fetches its markdown at
# runtime, so it needs an HTTP server (a file:// URL does not work): docsify-cli's,
# run through npx (Node.js required; the first run downloads it).
#
# The API reference (docs/api/) and the browser playground's jar
# (docs/playground/*.jar) are generated, not checked in: run
# ./buildtools/build-site.sh first to preview them.
#
# Usage:
#   ./serve-docs.sh [port]      # default port 3030 (not 8000, the default of the nashorn docs, so the browser never shows one site's cached page for the other), or set PORT=...
# then open http://localhost:<port>/ ; Ctrl-C to stop.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DOCS="$ROOT/docs"
PORT="${1:-${PORT:-3030}}"

command -v npx >/dev/null 2>&1 || { echo "error: npx not found on PATH (install Node.js)" >&2; exit 1; }
[ -f "$DOCS/index.html" ] || { echo "error: no docsify site at $DOCS" >&2; exit 1; }
[ -d "$DOCS/api" ] || echo "note: no API reference yet (docs/api/); run ./buildtools/build-site.sh to generate it"

exec npx --yes docsify-cli serve "$DOCS" --port "$PORT"
