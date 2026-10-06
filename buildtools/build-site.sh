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
# Generates the parts of the documentation site (docs/) that are not checked in:
#
#   docs/api/<artifactId>/          the javadoc of every published module
#   docs/playground/*.jar           the CheerpJ (browser) build of the GaltaJS playground
#   docs/java-playground/*.jar      the CheerpJ (browser) build of the Java playground
#
# Both are gitignored. Run this before ./serve-docs.sh to preview the API pages
# and the playground locally; the publish-docs workflow
# (.github/workflows/publish-docs.yml) runs it on every push to master, so the
# site always documents the revision it was published from.
#
# The published modules are read from galta/galta-bom/pom.xml, the one list of
# what Galta publishes, so this script never drifts from it. The script fails if
# docs/API.md does not link exactly those modules, which catches a module added
# to the BOM but not to the API reference page (or the other way round).
#
# Only the published modules, the CheerpJ playground and what they depend on are
# built (-pl ... -am): the test262/Rhino compliance modules, whose first build
# fetches their suites from GitHub, are left out. Each module keeps its own
# maven-javadoc-plugin settings, which is why the javadoc is generated per module
# rather than with javadoc:aggregate.
#
# Usage:
#   ./buildtools/build-site.sh
#
# Environment (optional):
#   MVN=/path/to/mvn     the Maven binary to use (default: mvn on the PATH)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DOCS="$ROOT/docs"
API="$DOCS/api"
MVN="${MVN:-mvn}"
# The browser (CheerpJ) playgrounds: "<module> <built jar> <docs folder>"
CHEERPJ_PLAYGROUNDS="galta/parent-js/js-playground-cheerpj jsplayground-cheerpj.jar playground
galta/parent-java/playground-java-cheerpj javaplayground-cheerpj.jar java-playground"

command -v python3 >/dev/null 2>&1 || { echo "error: python3 not found on PATH" >&2; exit 1; }

# "<artifactId> <module directory>" for every jar artifact managed by galta-bom
# (test-jars excluded: they are not API anyone reads).
MODULES="$(python3 - "$ROOT" <<'EOF'
import os, re, sys
root = sys.argv[1]
bom = open(os.path.join(root, "galta/galta-bom/pom.xml"), encoding="utf-8").read()
bom = re.sub(r"<!--.*?-->", "", bom, flags=re.S)  # the pom documents itself with XML examples
dm = bom[bom.index("<dependencyManagement>"):bom.index("</dependencyManagement>")]
published = []
for dep in re.findall(r"<dependency>(.*?)</dependency>", dm, re.S):
    if re.search(r"<type>\s*test-jar\s*</type>|<classifier>", dep):
        continue
    aid = re.search(r"<artifactId>\s*([^<\s]+)\s*</artifactId>", dep).group(1)
    if aid not in published:
        published.append(aid)
# artifactId -> module directory, from every pom.xml under galta/
dirs = {}
for d, subdirs, files in os.walk(os.path.join(root, "galta")):
    subdirs[:] = [s for s in subdirs if s not in ("target", "src", "node_modules", ".git")]
    if "pom.xml" in files:
        pom = open(os.path.join(d, "pom.xml"), encoding="utf-8").read()
        pom = re.sub(r"<!--.*?-->", "", pom, flags=re.S)
        pom = re.sub(r"<parent>.*?</parent>", "", pom, count=1, flags=re.S)
        m = re.search(r"<artifactId>\s*([^<\s]+)\s*</artifactId>", pom)
        if m:
            dirs[m.group(1)] = os.path.relpath(d, root)
missing = [a for a in published if a not in dirs]
if missing:
    sys.exit("error: no module directory for " + ", ".join(missing))
for a in published:
    print(a, dirs[a])
EOF
)"
[ -n "$MODULES" ] || { echo "error: no published module found in galta-bom" >&2; exit 1; }

# docs/API.md must link exactly the published modules.
python3 - "$DOCS/API.md" "$MODULES" <<'EOF'
import re, sys
page = open(sys.argv[1], encoding="utf-8").read()
linked = set(re.findall(r"\(api/([^/)]+)/index\.html", page))
published = {line.split()[0] for line in sys.argv[2].splitlines() if line.strip()}
problems = []
if published - linked:
    problems.append("published but missing from docs/API.md: " + ", ".join(sorted(published - linked)))
if linked - published:
    problems.append("linked from docs/API.md but not published (not in galta-bom): " + ", ".join(sorted(linked - published)))
if problems:
    sys.exit("error: docs/API.md and galta/galta-bom/pom.xml disagree\n  " + "\n  ".join(problems))
EOF

PL="$(echo "$MODULES" | awk '{print $2}' | paste -sd, -),$(echo "$CHEERPJ_PLAYGROUNDS" | awk '{print $1}' | paste -sd, -)"
echo "Building $(echo "$MODULES" | wc -l | tr -d ' ') published modules and the CheerpJ playgrounds, with their javadoc"
# One reactor invocation: modules resolve each other (and the Galta Maven
# plugins) from the reactor, so nothing needs to be installed first.
"$MVN" -B -q -f "$ROOT/pom.xml" -DskipTests -Dgpg.skip=true -pl "$PL" -am package javadoc:javadoc

rm -rf "$API"
mkdir -p "$API"
while read -r artifact dir; do
    src=""
    for candidate in "$ROOT/$dir/target/reports/apidocs" "$ROOT/$dir/target/site/apidocs"; do
        if [ -f "$candidate/index.html" ]; then src="$candidate"; break; fi
    done
    if [ -z "$src" ]; then
        echo "error: no javadoc generated for $artifact (looked in $dir/target/reports/apidocs)" >&2
        exit 1
    fi
    mkdir -p "$API/$artifact"
    cp -R "$src/." "$API/$artifact/"
    echo "  $artifact -> docs/api/$artifact/"
done <<< "$MODULES"

while read -r module jarName folder; do
    jar="$ROOT/$module/target/$jarName"
    [ -f "$jar" ] || { echo "error: the CheerpJ playground jar was not built ($jar)" >&2; exit 1; }
    mkdir -p "$DOCS/$folder"
    cp "$jar" "$DOCS/$folder/$jarName"
    echo "  playground -> docs/$folder/$jarName"
done <<< "$CHEERPJ_PLAYGROUNDS"

echo "Site generated in $DOCS (serve it with ./serve-docs.sh)"
