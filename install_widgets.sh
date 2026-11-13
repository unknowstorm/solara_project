#!/usr/bin/env bash
set -euo pipefail

SRC="${1:-$HOME/Загрузки}"
ROOT="$(pwd)"
MAIN="$ROOT/androidApp/src/main"
KT="$MAIN/kotlin/com/unknowstorm/musicapp/widget"
RES="$MAIN/res"

[ -d "$SRC" ] || { echo "Нет папки $SRC"; exit 1; }
[ -d "$ROOT/androidApp" ] || { echo "Запустите скрипт из корня проекта (там, где папка androidApp)"; exit 1; }

find_src() {
  local name="$1" base ext pat
  base="${name%.*}"; ext="${name##*.}"
  pat="$(echo "$base" | sed 's/[_ ]/?/g')"
  find "$SRC" -maxdepth 1 -type f \( -iname "$pat.$ext" -o -iname "$pat ([0-9]).$ext" \) \
    -printf '%T@ %p\n' | sort -rn | head -1 | cut -d' ' -f2-
}

put() {
  local name="$1" dest="$2" target="${3:-$1}" f
  f="$(find_src "$name" || true)"
  if [ -z "$f" ]; then echo "НЕ НАЙДЕН: $name"; return 0; fi
  mkdir -p "$dest"
  if [ -f "$dest/$target" ]; then cp "$dest/$target" "$dest/$target.bak"; fi
  cp "$f" "$dest/$target"
  echo "OK: $name -> ${dest#$ROOT/}/$target"
}

echo "== Kotlin-файлы виджетов =="
for f in SolaraWidgets.kt WidgetStore.kt WidgetPlayerListener.kt WidgetActions.kt WidgetCovers.kt; do
  put "$f" "$KT"
done

echo "== Ресурсы =="
put widget_2x2_info.xml   "$RES/xml"
put widget_4x2_info.xml   "$RES/xml"
put widget_loading.xml    "$RES/layout"
put widget_loading_bg.xml "$RES/drawable"
for f in ic_widget_play ic_widget_pause ic_widget_next ic_widget_previous ic_widget_music_note; do
  put "$f.xml" "$RES/drawable"
done
put widget_strings.xml    "$RES/values"

echo "== Замена существующих файлов =="
put AndroidManifest.xml "$MAIN"
put libs.versions.toml  "$ROOT/gradle"

PS_OLD="$(find "$ROOT" -name PlaybackService.kt -not -path '*/build/*' | head -1 || true)"
if [ -n "$PS_OLD" ]; then
  put PlaybackService.kt "$(dirname "$PS_OLD")"
else
  echo "PlaybackService.kt в проекте не найден, положите вручную"
fi

echo "Готово. Бэкапы лежат рядом с заменёнными файлами (*.bak)."
