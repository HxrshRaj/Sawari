#!/usr/bin/env bash
# End-to-end check of the licence-scan flow in the real debug app on a running emulator, against a LOCAL backend on :8000.
# Covers: permission explain/ask/allow, live camera, capture, review, rotation, camera release, retake, confirm, attach/remove,
# and (debug builds) whether LeakCanary reports a leak afterwards. Prints PASS/FAIL per check; exit code = number of failures.
. "$(dirname "$0")/ui.sh"
export MSYS_NO_PATHCONV=1
API=http://localhost:8000
EMAIL="l$RANDOM@x.io"; PASS_="pass1234"
fails=0
ok()  { echo "PASS  $1"; }
bad() { echo "FAIL  $1"; echo "      screen: $(dump | python -c "import sys,re;print(re.findall(r'text=\"([^\"]+)\"',sys.stdin.read())[:12])")"; fails=$((fails+1)); }
has_text() { dump | grep -q "text=\"$1\""; }
wait_text() { for i in $(seq 1 ${2:-20}); do has_text "$1" && return 0; sleep 1; done; return 1; }
# "Active Camera Clients: []" when nobody holds a camera; the app's package is listed there while it does
camera_clients() { "$ADB" shell dumpsys media.camera | sed -n '/Active Camera Clients/,/Allowed user IDs/p' | grep -c "com.hxrsh.sawari"; }
rotate() { "$ADB" shell settings put system accelerometer_rotation 0; "$ADB" shell settings put system user_rotation $1; sleep 3; }
swipe_up() { "$ADB" shell input swipe 360 1000 360 300 300; sleep 1; }
type_slow() { tap_id "$1"; sleep 0.7; for ((i=0;i<${#2};i+=3)); do "$ADB" shell input text "${2:$i:3}"; sleep 0.5; done; }
login_ui() {
  for a in 1 2 3; do
    type_slow email_field "$EMAIL"; type_slow password_field "$PASS_"; sleep 1
    "$ADB" shell input keyevent 4; sleep 1; tap_id submit_button; sleep 6
    has_id email_field || return 0
    for t in email_field password_field; do tap_id $t; "$ADB" shell input keyevent KEYCODE_MOVE_END; for k in $(seq 1 25); do "$ADB" shell input keyevent KEYCODE_DEL; done; done
  done; return 1
}

curl -s -X POST $API/auth/register -H 'content-type: application/json' -d "{\"name\":\"Licence Tester\",\"email\":\"$EMAIL\",\"password\":\"$PASS_\"}" >/dev/null
"$ADB" shell am force-stop $PKG; "$ADB" shell pm clear $PKG >/dev/null; sleep 3
"$ADB" shell pm revoke $PKG android.permission.CAMERA 2>/dev/null
# Debug builds include LeakCanary, which pops up its OWN "Allow notifications?" system dialog on Android 13+ and would sit on
# top of this flow. Pre-grant it so only the app's behaviour is under test (release builds have no LeakCanary).
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
rotate 0
[ "$(camera_clients)" -eq 0 ] && ok "no camera client before the scanner opens" || bad "no camera client before the scanner opens"
"$ADB" logcat -c
"$ADB" shell am start -a android.intent.action.VIEW -d "sawari://car/2" $PKG >/dev/null
wait_id email_field 60 && login_ui && ok "logged in" || bad "logged in"
wait_id book_button 40 && ok "car detail via deep link" || bad "car detail via deep link"
tap_id book_button; wait_id confirm_button 30 && ok "booking screen" || bad "booking screen"
swipe_up; swipe_up
tap_id scan_licence_button; wait_id camera_enable 20 && ok "scanner opens at the explain step (permission not asked yet)" || bad "scanner opens at explain step"
tap_id camera_enable
# Compose dialogs are separate windows: their testTags are not exported as resource-ids, so match the visible text
wait_text "Why we ask" 10 && ok "rationale dialog shown before the system prompt" || bad "rationale dialog"
tap_text "Continue"; sleep 2
# system dialog: "While using the app"
for i in $(seq 1 15); do
  c=$(dump | python -c "
import sys,re
x=sys.stdin.read()
m=re.search(r'resource-id=\"[^\"]*permission_allow_foreground_only_button\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x)
if m:
    a,b,c,d=map(int,m.groups());print((a+c)//2,(b+d)//2)")
  [ -n "$c" ] && { "$ADB" shell input tap $c; break; }; sleep 1
done
wait_text "Take photo" 40 && ok "live camera shown after allowing (shutter enabled)" || bad "live camera shown after allowing"
[ "$(camera_clients)" -ge 1 ] && ok "camera is in use by the app while previewing" || bad "camera in use while previewing"

echo "== rotation during preview =="
rotate 1; wait_text "Take photo" 30 && ok "preview survives rotation to landscape" || bad "preview survives rotation"
rotate 0; wait_text "Take photo" 30 && ok "preview survives rotation back to portrait" || bad "preview survives rotation back"

echo "== capture =="
tap_id capture_button; wait_id review_image 40 && ok "photo captured, review shown" || bad "photo captured"
sleep 2
[ "$(camera_clients)" -eq 0 ] && ok "camera released while reviewing" || bad "camera released while reviewing"
rotate 1; wait_id review_image 20 && ok "review survives rotation (state kept)" || bad "review survives rotation"
rotate 0; wait_id review_image 20 && ok "review survives rotation back" || bad "review survives rotation back"
files=$("$ADB" shell run-as $PKG ls files/licence 2>/dev/null | grep -c jpg); [ "$files" = "1" ] && ok "exactly one photo in app-private storage" || bad "one photo in private storage (found $files)"

echo "== retake then confirm =="
tap_id retake_button; wait_text "Take photo" 30 && ok "retake returns to live preview" || bad "retake"
sleep 2; files=$("$ADB" shell run-as $PKG ls files/licence 2>/dev/null | grep -c jpg); [ "$files" = "0" ] && ok "rejected photo deleted" || bad "rejected photo deleted (found $files)"
tap_id capture_button; wait_id review_image 40; tap_id use_photo_button
wait_id licence_attached 20 && ok "confirmed photo attached to the booking draft" || bad "photo attached to draft"
sleep 3   # the camera service closes the device asynchronously after unbind
[ "$(camera_clients)" -eq 0 ] && ok "camera released after leaving the scanner" || bad "camera released after leaving scanner"
tap_id remove_licence_button; sleep 2
wait_id scan_licence_button 10 && ok "remove returns to the scan button" || bad "remove"
files=$("$ADB" shell run-as $PKG ls files/licence 2>/dev/null | grep -c jpg); [ "$files" = "0" ] && ok "removed photo deleted from storage" || bad "removed photo deleted (found $files)"

echo "== back out of the scanner =="
tap_id scan_licence_button; wait_id capture_button 20; "$ADB" shell input keyevent 4; wait_id scan_licence_button 15 && ok "system Back closes the scanner" || bad "Back closes the scanner"

echo "== LeakCanary (debug build) =="
for i in 1 2 3; do tap_id scan_licence_button; sleep 2; "$ADB" shell input keyevent 4; sleep 2; done
"$ADB" shell input keyevent KEYCODE_HOME; sleep 20     # app invisible: LeakCanary dumps and analyses retained objects
lc=$("$ADB" logcat -d | grep -i "leakcanary")
echo "$lc" | grep -iE "retained|leak|No leak|analysis" | tail -6
if echo "$lc" | grep -qiE "HEAP ANALYSIS RESULT|Found [0-9]+ (new )?leak|LeakTrace|application leak|Dumping the heap|heap dump"; then bad "LeakCanary dumped the heap / reported a leak"; else ok "LeakCanary: no heap dump, no leak (retained objects were all garbage collected)"; fi
rotate 0; "$ADB" shell settings put system accelerometer_rotation 1
echo "failures: $fails"
exit $fails
