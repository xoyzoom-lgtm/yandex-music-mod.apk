#!/usr/bin/env bash
# Скачивает Discord Social SDK для Android в app/libs/discord_partner_sdk.aar.
#
# SDK нельзя хранить в публичном репозитории (лицензия Discord), поэтому его ссылка
# передаётся через секрет DISCORD_SDK_AAR_URL. Если секрета нет — собирается версия
# без Discord, и это не ошибка.
#
# Ссылка может указывать как на сам .aar, так и на zip-архив SDK из Developer Portal
# (в нём ищется discord_partner_sdk.aar).
set -euo pipefail

target="app/libs/discord_partner_sdk.aar"

if [ -z "${DISCORD_SDK_AAR_URL:-}" ]; then
  echo "DISCORD_SDK_AAR_URL не задан — сборка без Discord Rich Presence."
  exit 0
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

curl --fail --silent --show-error --location --retry 3 --output "$workdir/download" "$DISCORD_SDK_AAR_URL"

mkdir -p "$(dirname "$target")"
if unzip -l "$workdir/download" 2>/dev/null | grep -q "AndroidManifest.xml"; then
  # Это сам AAR.
  cp "$workdir/download" "$target"
else
  unzip -q "$workdir/download" -d "$workdir/sdk"
  aar="$(find "$workdir/sdk" -name 'discord_partner_sdk.aar' -print -quit)"
  if [ -z "$aar" ]; then
    echo "::error::В скачанном архиве нет discord_partner_sdk.aar"
    exit 1
  fi
  cp "$aar" "$target"
fi

echo "Discord Social SDK: $(du -h "$target" | cut -f1) → $target"
if [ -z "${DISCORD_APPLICATION_ID:-}" ]; then
  echo "::warning::SDK скачан, но переменная DISCORD_APPLICATION_ID не задана — Rich Presence будет выключен."
fi
