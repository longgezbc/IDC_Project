#!/bin/bash
TOKEN=$(python3 -c 'import json;print(json.load(open("/tmp/login.json"))["data"]["token"])')
echo "=== current layout of room 2 ==="
curl -s "http://localhost/api/room/2/layout" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);data=d.get("data") or {};print(json.dumps({"objects":data.get("objects"),"cables":len(data.get("cables") or [])},ensure_ascii=False)[:500])'
echo "=== clearing layout ==="
curl -s -X PUT "http://localhost/api/room/2/layout" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"objects":[],"cables":[]}' | head -c 200
echo
echo "=== verify ==="
curl -s "http://localhost/api/room/2/layout" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);data=d.get("data") or {};print("objects:",len(data.get("objects") or []),"cables:",len(data.get("cables") or []))'
