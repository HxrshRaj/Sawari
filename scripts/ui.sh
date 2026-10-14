#!/usr/bin/env bash
# Tiny adb/uiautomator driver used for the end-to-end verification. Source it: . scripts/ui.sh
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
PKG=com.hxrsh.sawari
dump() { MSYS_NO_PATHCONV=1 "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; MSYS_NO_PATHCONV=1 "$ADB" exec-out cat /sdcard/ui.xml; }
# center of the node whose resource-id ends with $1 (prints "x y" or nothing)
center() { dump | python -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*?resource-id=\"[^\"]*$1\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x):
    a,b,c,d=map(int,m.groups());print((a+c)//2,(b+d)//2);break
"; }
tap_id() { for i in 1 2 3 4 5 6 7 8; do c=$(center "$1"); if [ -n "$c" ]; then "$ADB" shell input tap $c; return 0; fi; sleep 1; done; echo "NOT FOUND: $1" >&2; return 1; }
tap_text() { c=$(dump | python -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*?text=\"$1\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x):
    a,b,c,d=map(int,m.groups());print((a+c)//2,(b+d)//2);break
"); [ -n "$c" ] && "$ADB" shell input tap $c || { echo "NOT FOUND text: $1" >&2; return 1; }; }
type_in() { tap_id "$1"; sleep 0.5; "$ADB" shell input text "$2"; }
has_id() { dump | grep -q "resource-id=\"[^\"]*$1\""; }
wait_id() { for i in $(seq 1 ${2:-15}); do has_id "$1" && return 0; sleep 1; done; echo "TIMEOUT waiting $1" >&2; return 1; }
shot() { mkdir -p docs/screenshots; "$ADB" exec-out screencap -p > "docs/screenshots/$1.png"; }
