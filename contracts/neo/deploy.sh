#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

fail() {
  echo "$1" >&2
  exit 1
}

usage='Usage: ./deploy.sh <testnet|mainnet> <guestbook|credits|all> [--owner <address>] [--gate <public key>] [--rpc <url>] [--dry-run]
       ./deploy.sh wallet <name>'

network=${1:-}
case "$network" in
  testnet | mainnet | wallet) ;;
  *) fail "$usage" ;;
esac

java_major() {
  "$1/bin/java" -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p'
}

use_jdk_21() {
  local candidate
  for candidate in "${JAVA_HOME:-}" "$HOME"/.sdkman/candidates/java/21* /usr/lib/jvm/java-21-*; do
    if [ -x "$candidate/bin/javac" ] && [ "$(java_major "$candidate")" = 21 ]; then
      export JAVA_HOME=$candidate
      return
    fi
  done
  fail 'JDK 21 not found. Set JAVA_HOME to it: the NEF is only verified as reproducible on 21.'
}

artifact_hashes() {
  sha256sum ./*/build/neow3j/*.nef ./*/build/neow3j/*.manifest.json
}

build_for_mainnet() {
  [ -z "$changes" ] || fail "Mainnet needs a clean tree. Commit or stash first:"$'\n'"$changes"
  ./gradlew -q clean test neow3jCompile
  local first
  first=$(artifact_hashes)
  ./gradlew -q clean neow3jCompile
  [ "$first" = "$(artifact_hashes)" ] || fail 'Two clean builds gave different artifacts.'
}

use_jdk_21

if [ "$network" = wallet ]; then
  ./gradlew -q :deploy:installDist
  exec "$JAVA_HOME/bin/java" -cp 'deploy/build/install/deploy/lib/*' com.smartargs.mempolis.deploy.NewWallet "${2:-}"
fi

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

changes=$(git status --porcelain -- . ':!deployments')
commit=$(git rev-parse HEAD)
[ -z "$changes" ] || commit="$commit-dirty"

if [ "$network" = mainnet ]; then
  build_for_mainnet
else
  ./gradlew -q neow3jCompile
fi
./gradlew -q :deploy:installDist

exec deploy/build/install/deploy/bin/deploy "$@" --commit "$commit"
