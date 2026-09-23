#!/usr/bin/env bash
set -euo pipefail

repo=isaiahpettingill/Droid64
backup="${HOME}/droid64-signing"
keystore="${backup}/droid64-release.p12"
password_file="${backup}/password"

for tool in gh keytool openssl base64; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing $tool. Install it before continuing." >&2
    exit 1
  fi
done
gh auth status >/dev/null

umask 077
mkdir -p "$backup"
chmod 700 "$backup"
if [[ -e "$keystore" && ! -s "$password_file" || ! -e "$keystore" && -e "$password_file" ]]; then
  echo "Incomplete signing backup at $backup. Resolve it before retrying." >&2
  exit 1
fi
if [[ ! -e "$keystore" ]]; then
  password=$(openssl rand -hex 24)
  keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$keystore" \
    -alias droid64 -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Droid64, O=Droid64' -storepass "$password" -keypass "$password"
  printf '%s\n' "$password" > "$password_file"
  echo "Created signing backup at $backup; keep both files together."
fi

password=$(cat "$password_file")
temp_env=$(mktemp)
trap 'rm -f "$temp_env"' EXIT
{
  printf 'DROID64_KEYSTORE_B64=%s\n' "$(base64 < "$keystore" | tr -d '\n')"
  printf 'DROID64_STORE_PASSWORD=%s\n' "$password"
  printf 'DROID64_KEY_ALIAS=droid64\n'
  printf 'DROID64_KEY_PASSWORD=%s\n' "$password"
} > "$temp_env"
gh secret set --repo "$repo" -f "$temp_env"
echo 'Signing key and passwords saved as private repository Actions secrets.'
gh workflow run android.yml --repo "$repo" --ref master
echo "Release build started: https://github.com/$repo/actions/workflows/android.yml"
