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
# Cuts a Galta release, in three moves:
#
#   1. Builds, tests and (unless a dry run) *stages* the published modules to
#      Maven Central through the Sonatype Central Portal (`mvn -P central
#      deploy`; autoPublish=false, so nothing is public yet). It then installs
#      the jars locally and runs the standalone smoke-test project against them,
#      and only after that passes does it pause for you to review and click
#      Publish in the Portal.
#   2. Tags the release branch (master) with v<version> and pushes the tag.
#   3. Creates a GitHub release carrying the runnable jars: the desktop
#      playground and the GaltaJS fat jar.
#
# It does NOT publish the documentation. The site (docs, javadoc and the browser
# playground) is deployed by the publish-docs workflow on every push to master,
# and GitHub Pages takes its source from that workflow rather than from a
# branch - so cutting a release from a pushed master has already rebuilt it.
#
# Before a real run, set the release version everywhere it is declared (see
# "Versioning" in docs/BuildAndRelease.md); this script checks that they agree.
#
# SECURITY - the script holds no secrets and puts none on a command line:
#   * Central token   -> kept in the macOS Keychain (generic password, service
#                        "central-token"; CENTRAL_TOKEN_ITEM to change it). The
#                        script reads it into CENTRAL_TOKEN for this run only,
#                        and ~/.m2/settings.xml <server id=central> uses it as
#                        <password>${env.CENTRAL_TOKEN}</password>. An already
#                        set CENTRAL_TOKEN is used as is (e.g. on CI).
#   * GPG passphrase  -> supplied interactively by gpg-agent/pinentry at sign
#                        time; never passed as -Dgpg.passphrase.
#   * GitHub auth     -> the `gh` CLI keyring; the script never sees a token.
# It also refuses to run on a dirty tree, the wrong branch, or an existing tag,
# and asks for an explicit confirmation before anything leaves your machine.
#
# Usage:
#   buildtools/release.sh
#
# Dry run:
#   RELEASE_DRY_RUN=1 buildtools/release.sh
#     rehearses the whole thing locally and pushes NOTHING outward: it does the
#     release build unsigned and without tests (mvn -P central verify, no upload
#     and no passphrase prompt), installs the jars locally and runs the smoke
#     test, checks the tag name is free without creating it, lists the
#     GitHub-release assets without creating the release. Preconditions that
#     would only matter for a real run (clean/synced master, a free tag, the
#     token) are downgraded to warnings, so you can rehearse from any branch and
#     before the secrets are set up. Signing is exercised only by a real run.
#
# Environment (all optional):
#   REPO_SLUG=monflabs/Galta-Java  the GitHub repository
#   RELEASE_BRANCH=master          the branch that is tagged and released from
#   RELEASE_DRY_RUN=1              rehearse locally, publish/push/tag nothing
#   RELEASE_YES=1                  skip the interactive confirmation and the
#                                  "have you clicked Publish" pause (for a hands
#                                  -off run once you trust it)
#   RELEASE_TEST262=1              first run the full test262 compliance suite,
#                                  all three execution modes (hours)
#   RELEASE_SKIP_CENTRAL=1         skip step 1 (e.g. Central already published)
#   RELEASE_SKIP_SMOKE=1           skip the smoke test of the built jars
#   RELEASE_SKIP_GHRELEASE=1       skip step 3
#   MVN=/path/to/mvn               the Maven binary (default: mvn on the PATH)

set -euo pipefail

# Temporary files to remove on the way out (the deploy log, the release notes,
# the renamed release assets).
RELEASE_TEMPS=""
cleanup_temps() {
  local f
  for f in $RELEASE_TEMPS; do rm -rf "$f"; done
}
trap cleanup_temps EXIT

REPO_SLUG="${REPO_SLUG:-monflabs/Galta-Java}"
RELEASE_BRANCH="${RELEASE_BRANCH:-master}"
PORTAL_URL="https://central.sonatype.com/publishing/deployments"
MVN="${MVN:-mvn}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

note() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mwarning:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

confirm() {
  [ "${RELEASE_YES:-}" = "1" ] && return 0
  local reply
  printf '%s [y/N] ' "$1"
  read -r reply
  case "$reply" in y|Y|yes|Yes) return 0 ;; *) die "aborted." ;; esac
}

DRY="${RELEASE_DRY_RUN:-}"
dry() { [ "$DRY" = "1" ]; }
# A precondition that only matters for a real release: fatal normally, a warning
# in a dry run so the rehearsal can proceed from any state.
gate() { if dry; then warn "$1"; else die "$1"; fi; }

# --- preflight ----------------------------------------------------------------

for tool in "$MVN" git gh java awk python3; do
  command -v "$tool" >/dev/null 2>&1 || die "missing required tool: $tool"
done

# Signing is the first thing a real run does, and it is the first thing that
# goes wrong: maven-gpg-plugin forks gpg, and a pinentry with no terminal fails
# with "Inappropriate ioctl for device" and exit 2 - after the whole build.
# Prove it here, in a second, instead.
test_signing() {
  local probe
  probe="$(mktemp)"
  printf 'release-preflight\n' > "$probe"
  if gpg --batch --yes --detach-sign -o "${probe}.sig" "$probe" >/dev/null 2>&1 && [ -s "${probe}.sig" ]; then
    rm -f "$probe" "${probe}.sig"
    return 0
  fi
  rm -f "$probe" "${probe}.sig"
  return 1
}

gh auth status >/dev/null 2>&1 || gate "gh is not authenticated (run: gh auth login)"

# Signing: a real release must be signed (gpg + a secret key). A dry run never
# uploads, so it always builds UNSIGNED - no passphrase prompt, and it runs
# unattended; signing is exercised only by a real run.
GPG_SKIP=""
if dry; then
  GPG_SKIP="-Dgpg.skip=true"
  note "[dry] signing is skipped in a dry run (a real release signs)"
elif command -v gpg >/dev/null 2>&1 && [ -n "$(gpg --list-secret-keys 2>/dev/null)" ]; then
  :
else
  die "gpg with a secret key is required; Central needs signed artifacts"
fi

# The Central Portal token: settings.xml reads it from the environment, and it is
# kept in the macOS Keychain, read here for this run only (never printed).
CENTRAL_TOKEN_ITEM="${CENTRAL_TOKEN_ITEM:-central-token}"
if [ "${RELEASE_SKIP_CENTRAL:-}" != "1" ]; then
  if ! grep -q '<id>central</id>' "${HOME}/.m2/settings.xml" 2>/dev/null; then
    gate "no <server><id>central</id> in ~/.m2/settings.xml; a real deploy needs the Portal token"
  elif ! grep -qF '${env.CENTRAL_TOKEN}' "${HOME}/.m2/settings.xml"; then
    warn "the central server of ~/.m2/settings.xml does not use \${env.CENTRAL_TOKEN}: the token is read from settings.xml, not from the Keychain"
  elif [ -n "${CENTRAL_TOKEN:-}" ]; then
    note "Central token: from the CENTRAL_TOKEN environment variable"
  elif command -v security >/dev/null 2>&1 \
      && CENTRAL_TOKEN="$(security find-generic-password -s "$CENTRAL_TOKEN_ITEM" -w 2>/dev/null)" \
      && [ -n "$CENTRAL_TOKEN" ]; then
    export CENTRAL_TOKEN
    note "Central token: read from the Keychain (item ${CENTRAL_TOKEN_ITEM})"
  else
    gate "no Central token: store it in the Keychain once with
      security add-generic-password -a \"\$USER\" -s ${CENTRAL_TOKEN_ITEM} -w
    (it prompts for the token), or set CENTRAL_TOKEN"
  fi
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "$RELEASE_BRANCH" ] || gate "on branch '$branch', expected '$RELEASE_BRANCH' (set RELEASE_BRANCH to override)"
[ -z "$(git status --porcelain)" ] || gate "working tree is not clean; commit or stash first"

note "fetching origin"
git fetch --quiet origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse "origin/$RELEASE_BRANCH")" ] \
  || gate "$RELEASE_BRANCH is not in sync with origin/$RELEASE_BRANCH; push or pull first"

# The version is declared in several places (a parent is located before any
# property is known, and galta-bom has no parent to inherit from); a release
# with any of them out of step would publish poms that point at each other's
# wrong version.
note "checking the version is the same everywhere it is declared"
VERSIONS="$(python3 - "$ROOT" <<'EOF'
import os, re, sys
root = sys.argv[1]
def read(path):
    return re.sub(r"<!--.*?-->", "", open(os.path.join(root, path), encoding="utf-8").read(), flags=re.S)
def one(path, pattern, what):
    m = re.search(pattern, read(path), re.S)
    if not m:
        sys.exit("cannot find %s in %s" % (what, path))
    return m.group(1).strip()
found = [
    ("pom.xml <revision>",                         one("pom.xml", r"<revision>([^<]+)</revision>", "<revision>")),
    ("pom.xml <parent><version>",                  one("pom.xml", r"<parent>.*?<version>([^<]+)</version>.*?</parent>", "the parent version")),
    ("galta/galta-bom/pom.xml <revision>",         one("galta/galta-bom/pom.xml", r"<revision>([^<]+)</revision>", "<revision>")),
    ("tools/monflabs-parent/pom.xml <version>",    one("tools/monflabs-parent/pom.xml", r"<artifactId>monflabs-parent</artifactId>\s*<version>([^<]+)</version>", "the project version")),
    ("tools/monflabs-parent/pom.xml galta.version", one("tools/monflabs-parent/pom.xml", r"<galta.version>([^<]+)</galta.version>", "galta.version")),
]
for where, v in found:
    print("%s\t%s" % (v, where))
EOF
)" || die "could not read the version declarations"
echo "$VERSIONS" | sed 's/^/      /'
[ "$(echo "$VERSIONS" | cut -f1 | sort -u | wc -l | tr -d ' ')" = "1" ] \
  || die "the version declarations disagree (see above); set the same version everywhere"

note "reading the project version"
VERSION="$("$MVN" -q -N help:evaluate -Dexpression=project.version -DforceStdout)"
[ -n "$VERSION" ] || die "could not read project.version from the root pom"
[ "$VERSION" = "$(echo "$VERSIONS" | head -1 | cut -f1)" ] || die "Maven reports version $VERSION, the poms declare another one"
case "$VERSION" in *SNAPSHOT*) die "version is a SNAPSHOT ($VERSION); set a release version first" ;; esac
TAG="v${VERSION}"

git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null && gate "tag ${TAG} already exists locally"
git ls-remote --exit-code --tags origin "${TAG}" >/dev/null 2>&1 && gate "tag ${TAG} already exists on origin"

note "checking CHANGELOG.md has a dated ${VERSION} section"
changelog_head="$(grep -E "^#+ *${VERSION//./\\.} \\(" CHANGELOG.md | head -1 || true)"
[ -n "$changelog_head" ] || gate "CHANGELOG.md has no '## ${VERSION} (<date>)' section (the release notes come from it)"
case "$changelog_head" in *nreleased*) gate "CHANGELOG.md still says '${changelog_head}': set the release date" ;; esac

note "checking the unpublished modules are excluded from the Central upload"
# The publishing plugin filters the upload by its excludeArtifacts list only;
# maven.deploy.skip alone does not keep a module out of the bundle.
# (Written to a file first: bash 3.2, macOS's, mis-parses a here-document
# inside $(...).)
unpublished_py="$(mktemp)"
RELEASE_TEMPS="$RELEASE_TEMPS $unpublished_py"
cat > "$unpublished_py" <<'PYEOF'
import re, subprocess
root = open("pom.xml", encoding="utf-8").read()
excluded = set(re.findall(r"<excludeArtifact>([^<]+)</excludeArtifact>", root))
skipped = set()
# The tracked poms only: the build folders can hold symbolic link loops
poms = subprocess.run(["git", "ls-files", "--", "galta/*pom.xml", "tools/*pom.xml"],
                      capture_output=True, text=True, check=True).stdout.split()
for p in poms:
    s = open(p, encoding="utf-8").read()
    if re.search(r"<maven\.deploy\.skip>\s*true\s*</maven\.deploy\.skip>", s):
        body = re.sub(r"<parent>.*?</parent>", "", s, count=1, flags=re.S)
        skipped.add(re.search(r"<artifactId>([^<]+)</artifactId>", body).group(1))
for a in sorted(skipped - excluded):
    print("not in the root pom excludeArtifacts (would be published): " + a)
for a in sorted(excluded - skipped):
    print("in excludeArtifacts but does not set maven.deploy.skip: " + a)
PYEOF
UNPUBLISHED_CHECK="$(python3 "$unpublished_py")" || die "could not compare the unpublished module lists"
[ -z "$UNPUBLISHED_CHECK" ] || gate "$(printf 'the unpublished module lists disagree:\n%s' "$UNPUBLISHED_CHECK")"

# The GitHub release assets: built by the release build (shade), renamed with the
# version when attached. "<built file>|<name in the release>".
ASSETS=(
  "galta/parent-js/js-playground/target/jsplayground.jar|galtajs-playground-${VERSION}.jar"
  "galta/parent-js/js-all/target/galtajs-all.jar|galtajs-all-${VERSION}.jar"
)

cat <<EOF

  Repository : ${REPO_SLUG}
  Branch     : ${RELEASE_BRANCH} ($(git rev-parse --short HEAD))
  Version    : ${VERSION}
  Tag        : ${TAG}
  Central    : the modules managed by galta-bom, plus galta-bom, the parent poms
               and the Galta Maven plugins  (staged, manual Publish)
  GitHub rel : the desktop playground and the GaltaJS fat jar
  Docs       : published from ${RELEASE_BRANCH} by the publish-docs workflow, not by this script

EOF
if dry; then
  note "DRY RUN - building and staging locally; nothing is published, pushed or tagged."
else
  note "checking that gpg can sign (a passphrase prompt here is expected)"
  test_signing || die "gpg could not sign. A pinentry with no terminal is the usual cause:
    a GUI pinentry works from a forked gpg where the curses one does not -
      echo \"pinentry-program \$(command -v pinentry-mac || command -v pinentry-gtk-2)\" >> ~/.gnupg/gpg-agent.conf
      gpgconf --kill gpg-agent
    then re-check with:  echo hi | gpg --clearsign >/dev/null
    (export GPG_TTY=\$(tty) is the other fix, less reliable under Maven.)"
  note "gpg signs"
  confirm "Release ${VERSION}? This publishes outside your machine."
fi

# --- 0. test262 compliance (optional, hours) ----------------------------------

if [ "${RELEASE_TEST262:-}" = "1" ]; then
  note "running the full test262 suite, all three execution modes (hours)"
  "$MVN" -B -P test262 clean install || die "the test262 build failed; nothing was tagged or published"
fi

# --- 1. Maven Central (staged) ------------------------------------------------

if dry; then
  note "[dry] release build (unsigned, no tests), no upload:  $MVN -P central ${GPG_SKIP} -DskipTests clean verify"
  # shellcheck disable=SC2086
  "$MVN" -B -P central $GPG_SKIP -DskipTests clean verify
  note "[dry] would then deploy to the Central Portal and wait for a manual Publish"
elif [ "${RELEASE_SKIP_CENTRAL:-}" = "1" ]; then
  note "skipping Central deploy (RELEASE_SKIP_CENTRAL=1); building the jars for the release"
  "$MVN" -B -DskipTests clean package
else
  note "building, testing, signing and staging to the Central Portal (gpg-agent will prompt to sign)"
  deploy_log="$(mktemp)"
  RELEASE_TEMPS="$RELEASE_TEMPS $deploy_log"
  "$MVN" -B -P central clean deploy 2>&1 | tee "$deploy_log"
  [ "${PIPESTATUS[0]}" -eq 0 ] || die "the release build failed; nothing was tagged or published"

  # Maven exits 0 whether or not the bundle was uploaded: the publishing plugin
  # uploads from the LAST project of the reactor and silently does nothing if
  # that one is skipped, and offline mode skips it with a warning nobody reads.
  # So look.
  if grep -q "requires online mode for execution but Maven is currently offline" "$deploy_log"; then
    die "the publish goal was skipped: Maven ran offline. Re-run without -o/--offline."
  fi
  if ! grep -qiE "deployment|uploaded" "$deploy_log"; then
    warn "the build log mentions no deployment - the upload may have been skipped"
    warn "check ${PORTAL_URL} before continuing; if it is empty, nothing was staged"
    confirm "Continue anyway?"
  fi
fi

for a in "${ASSETS[@]}"; do
  f="${a%%|*}"
  [ -f "$f" ] || die "expected artifact missing: $f (did the build run?)"
done

note "checking every built jar carries META-INF/LICENSE and META-INF/NOTICE (the sources and javadoc jars do not need them)"
missing_legal=""
while IFS= read -r jar; do
  listing="$(unzip -Z1 "$jar" 2>/dev/null || true)"
  if ! grep -qx "META-INF/LICENSE" <<<"$listing" || ! grep -qx "META-INF/NOTICE" <<<"$listing"; then
    missing_legal="${missing_legal}
    ${jar}"
  fi
done < <(find galta tools -path '*/target/*.jar' -not -name 'original-*' -not -name '*-sources.jar' -not -name '*-javadoc.jar' -not -path '*/target/*/*' | sort)
[ -z "$missing_legal" ] || die "jars without META-INF/LICENSE or NOTICE:${missing_legal}$(dry && echo '' || echo "
    If a Central deployment was staged, DROP it in the Portal: ${PORTAL_URL}")"

# --- 1b. smoke-test the built jars from ~/.m2 ---------------------------------
# Installs the jars locally and runs the standalone smoke-test project against
# them - a consumer's view: galta-bom imported, json and js from the repository -
# so a functional or packaging break is caught BEFORE tagging and before the
# Central Publish gate below (a real deploy has only staged at this point, so it
# can still be dropped in the Portal).
if [ "${RELEASE_SKIP_SMOKE:-}" = "1" ] || [ ! -d smoke-test ]; then
  note "skipping smoke test${RELEASE_SKIP_SMOKE:+ (RELEASE_SKIP_SMOKE=1)}"
else
  note "smoke-testing the ${VERSION} jars from ~/.m2"
  "$MVN" -B -q -DskipTests -Dgpg.skip=true -pl galta/galta-bom,galta/parent-js/js -am install
  if ! "$MVN" -B -q -f smoke-test/pom.xml -Dgalta.version="${VERSION}" clean test; then
    die "smoke test FAILED: the ${VERSION} jars do not work. Nothing was tagged or published.$(dry && echo '' || echo " If a Central deployment was staged, DROP it in the Portal: ${PORTAL_URL}")"
  fi
  note "smoke test passed"
fi

# --- 1c. Central manual Publish gate (real deploy only) -----------------------
if ! dry && [ "${RELEASE_SKIP_CENTRAL:-}" != "1" ]; then
  echo
  note "A deployment has been STAGED (autoPublish=false). Review and Publish it here:"
  echo "    ${PORTAL_URL}"
  if [ "${RELEASE_YES:-}" != "1" ]; then
    printf 'Press Enter once the deployment is Published (or Ctrl-C to stop and finish later)... '
    read -r _
  fi
fi

# --- 2. tag the release branch ------------------------------------------------

if dry; then
  note "[dry] would tag ${RELEASE_BRANCH} as ${TAG} and push it"
else
  note "tagging ${RELEASE_BRANCH} as ${TAG}"
  git tag -a "${TAG}" -m "Galta ${VERSION}"
  git push origin "${TAG}"
fi

# --- 3. GitHub release --------------------------------------------------------

if dry; then
  note "[dry] would create GitHub release ${TAG} with these assets:"
  for a in "${ASSETS[@]}"; do echo "        ${a%%|*}  ->  ${a##*|}"; done
elif [ "${RELEASE_SKIP_GHRELEASE:-}" = "1" ]; then
  note "skipping GitHub release (RELEASE_SKIP_GHRELEASE=1)"
else
  # The release notes are the version's section of CHANGELOG.md: from the line
  # "<version> (" up to the next "<number> (" heading.
  notes="$(mktemp)"
  RELEASE_TEMPS="$RELEASE_TEMPS $notes"
  awk -v v="$VERSION" '
    { line = $0; sub(/^#+ */, "", line); ishdr = (line ~ /^[0-9][0-9.]* \(/) }
    ishdr && index(line, v" (")==1 { grab=1; print; next }
    ishdr && grab { exit }
    grab { print }
  ' CHANGELOG.md > "$notes" || true
  if [ ! -s "$notes" ]; then
    printf 'Galta %s\n\nPublished to Maven Central under org.monflabs.galta; import org.monflabs.galta:galta-bom:%s.\nThe playground jar below is runnable: java -jar galtajs-playground-%s.jar\n' \
      "$VERSION" "$VERSION" "$VERSION" > "$notes"
  fi
  assets_dir="$(mktemp -d)"
  RELEASE_TEMPS="$RELEASE_TEMPS $assets_dir"
  files=()
  for a in "${ASSETS[@]}"; do
    cp "${a%%|*}" "$assets_dir/${a##*|}"
    files+=("$assets_dir/${a##*|}")
  done
  note "creating GitHub release ${TAG}"
  if gh release view "${TAG}" --repo "${REPO_SLUG}" >/dev/null 2>&1; then
    warn "release ${TAG} already exists; uploading assets to it"
    gh release upload "${TAG}" --repo "${REPO_SLUG}" --clobber "${files[@]}"
  else
    gh release create "${TAG}" --repo "${REPO_SLUG}" --title "Galta ${VERSION}" --notes-file "$notes" "${files[@]}"
  fi
fi

# --- 4. documentation (not this script's job) ---------------------------------
#
# .github/workflows/publish-docs.yml generates the javadoc and the browser
# playground into docs/ (buildtools/build-site.sh) and deploys the whole site to
# GitHub Pages on every push to master; Pages is configured with that workflow as
# its source. A release is cut from a clean, pushed master, so the commit being
# released is already being published.

note "docs: published from ${RELEASE_BRANCH} by the publish-docs workflow (not by this script)"
note "      watch it at https://github.com/${REPO_SLUG}/actions/workflows/publish-docs.yml"

# --- done ---------------------------------------------------------------------

pages_url="https://$(echo "$REPO_SLUG" | sed 's#/#.github.io/#')/"
echo
if dry; then
  note "DRY RUN complete - nothing left your machine. A real run would have:"
  echo "    - staged the Galta ${VERSION} artifacts at ${PORTAL_URL} for a manual Publish"
  echo "    - tagged and pushed ${TAG}"
  echo "    - created https://github.com/${REPO_SLUG}/releases/tag/${TAG} with ${#ASSETS[@]} jars"
  echo "  Re-run without RELEASE_DRY_RUN=1 (on a clean, synced ${RELEASE_BRANCH}) to do it for real."
else
  note "Released ${VERSION}."
  echo "    Central : ${PORTAL_URL}   (confirm it shows Published)"
  echo "    GitHub  : https://github.com/${REPO_SLUG}/releases/tag/${TAG}"
  echo "    Docs    : ${pages_url}   (deployed by the publish-docs workflow)"
fi
