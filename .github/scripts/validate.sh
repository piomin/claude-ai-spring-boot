#!/usr/bin/env bash
# Checks what the loaders of Claude Code and OpenCode depend on: frontmatter of skills and agents,
# and the same version in both plugin manifests. With an argument, the version must equal it (the release tag).
set -euo pipefail
cd "$(dirname "$0")/../.."

fail=0
err() { echo "ERROR: $*" >&2; fail=1; }

# value of a top-level frontmatter key, empty when absent
fm() { awk -v key="$2" 'NR==1 { if ($0 != "---") exit } NR>1 && $0=="---" { exit } index($0, key ":")==1 { sub("^" key ":[ ]*", ""); print; exit }' "$1"; }

for f in skills/*/SKILL.md; do
  dir="$(basename "$(dirname "$f")")"
  [ "$(fm "$f" name)" = "$dir" ] || err "$f: name must equal the directory name '$dir'"
  [ -n "$(fm "$f" description)" ] || err "$f: missing description"
done

for f in agents/*.md; do
  name="$(basename "$f" .md)"
  [ "$(fm "$f" name)" = "$name" ] || err "$f: name must equal the file name '$name'"
  [ -n "$(fm "$f" description)" ] || err "$f: missing description"
  [ "$(fm "$f" mode)" = "subagent" ] || err "$f: mode must be 'subagent' (OpenCode)"
done

claude_version="$(jq -r .version .claude-plugin/plugin.json)"
codex_version="$(jq -r .version .codex-plugin/plugin.json)"
[ "$claude_version" = "$codex_version" ] || err "plugin.json versions differ: claude=$claude_version codex=$codex_version"
grep -q "^# .*(v${claude_version})" README.md || err "README title does not show v${claude_version}"
if [ $# -ge 1 ] && [ "$1" != "$claude_version" ]; then err "tag $1 does not match plugin version $claude_version"; fi

[ "$fail" -eq 0 ] && echo "OK: $(ls skills | wc -l | tr -d ' ') skills, $(ls agents | wc -l | tr -d ' ') agents, version ${claude_version}"
exit "$fail"
