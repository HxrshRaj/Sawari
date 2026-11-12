#!/usr/bin/env bash
# End-to-end deep link check on a running emulator, against a LOCAL backend on :8000 (see README "Run the backend").
# Usage: bash scripts/deeplink_check.sh     (debug app must be installed: ./gradlew :app:installLiteDebug)
# Prints PASS/FAIL per scenario; exit code is the number of failures.
. "$(dirname "$0")/ui.sh"
export MSYS_NO_PATHCONV=1
API=http://localhost:8000
EMAIL="d$RANDOM@x.io"; PASS_="pass1234"   # throwaway user on the LOCAL dev backend only
fails=0
ok()   { echo "PASS  $1"; }
bad()  { echo "FAIL  $1"; echo "      screen: $(dump | python -c "import sys,re;print(re.findall(r'text=\"([^\"]+)\"',sys.stdin.read())[:12])")"; fails=$((fails+1)); }
has_text() { dump | grep -q "text=\"$1\""; }
wait_text() { for i in $(seq 1 ${2:-20}); do has_text "$1" && return 0; sleep 1; done; return 1; }
link() { "$ADB" shell am start -a android.intent.action.VIEW -d "$1" $PKG >/dev/null; }

# a booking for the test user, so "My bookings" has a list
tok=$(curl -s -X POST $API/auth/register -H 'content-type: application/json' -d "{\"name\":\"Deeplink Tester\",\"email\":\"$EMAIL\",\"password\":\"$PASS_\"}" | python -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
DAYS=$((RANDOM%3000+5))
s=$(python -c "import datetime as d;print((d.datetime.utcnow()+d.timedelta(days=$DAYS)).strftime('%Y-%m-%dT%H:00:00Z'))")
e=$(python -c "import datetime as d;print((d.datetime.utcnow()+d.timedelta(days=$DAYS,hours=3)).strftime('%Y-%m-%dT%H:00:00Z'))")
curl -s -X POST $API/bookings -H "authorization: Bearer $tok" -H 'content-type: application/json' -d "{\"car_id\":1,\"start_time\":\"$s\",\"end_time\":\"$e\"}" >/dev/null

name_of() { curl -s $API/cars | python -c "import sys,json;print([c['name'] for c in json.load(sys.stdin) if c['id']==$1][0])"; }
N1=$(name_of 1); N2=$(name_of 2); N3=$(name_of 3)   # seed data decides the names; the app must show the one matching the id

"$ADB" shell am force-stop $PKG; "$ADB" shell pm clear $PKG >/dev/null; sleep 3

# typing on a slow emulator drops characters: type in small chunks and check the field afterwards
type_slow() {  # $1=tag $2=text
  tap_id "$1"; sleep 0.7
  for ((i=0;i<${#2};i+=3)); do "$ADB" shell input text "${2:$i:3}"; sleep 0.5; done
}
login_ui() {
  for attempt in 1 2 3; do
    type_slow email_field "$EMAIL"; type_slow password_field "$PASS_"; sleep 1
    "$ADB" shell input keyevent 4; sleep 1; tap_id submit_button; sleep 6
    has_id email_field || return 0                       # login screen gone -> logged in
    # failed (probably a dropped character): wipe both fields and retry
    for t in email_field password_field; do tap_id $t; "$ADB" shell input keyevent KEYCODE_MOVE_END; for k in $(seq 1 25); do "$ADB" shell input keyevent KEYCODE_DEL; done; done
  done
  return 1
}

echo "== 1. cold start, NOT logged in: link -> login -> lands on the car =="
link "sawari://car/2"
if wait_id email_field 60; then ok "login screen shown first"; else bad "login screen shown first"; fi
login_ui
if wait_text "$N2" 30; then ok "after login, landed on car 2 ($N2)"; else bad "after login, landed on car 2"; fi

echo "== 2. app already open: sawari://bookings =="
link "sawari://bookings"
if wait_id bookings_list 20; then ok "bookings list shown"; else bad "bookings list shown"; fi

echo "== 3. app already open: sawari://car/3 =="
link "sawari://car/3"
if wait_text "$N3" 20; then ok "car 3 ($N3) shown"; else bad "car 3 shown"; fi

echo "== 4. unknown car id =="
link "sawari://car/9999"
if wait_text "Car not found" 20; then ok "clear not-found state"; else bad "clear not-found state"; fi

echo "== 5. malformed link =="
link "sawari://car/abc"
if wait_text "That link is not valid: Car id must be a positive number" 10; then ok "error snackbar shown, no crash"; else bad "error snackbar shown"; fi
"$ADB" shell pidof $PKG >/dev/null && ok "app still running" || bad "app still running"

echo "== 6. cold start, logged in: https link =="
"$ADB" shell am force-stop $PKG
link "https://sawari-api-1krd.onrender.com/app/car/1"
if wait_text "$N1" 30; then ok "https link opened car 1"; else bad "https link opened car 1"; fi

echo "== 7. cold start, logged in: custom scheme bookings =="
"$ADB" shell am force-stop $PKG
link "sawari://bookings"
if wait_id bookings_list 30; then ok "cold start straight to bookings"; else bad "cold start straight to bookings"; fi
echo "failures: $fails"
exit $fails
