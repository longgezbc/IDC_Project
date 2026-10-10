#!/bin/bash
python3 - <<'EOF'
import json
d = json.load(open("/tmp/login.json"))["data"]
print("TOKEN=" + d["token"])
print("REFRESH=" + d["refreshToken"])
print("USER=" + json.dumps(d["userInfo"], ensure_ascii=False))
EOF
