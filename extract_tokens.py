import json
d = json.load(open('/tmp/login.json'))['data']
print(d['token'])
print(d['refreshToken'])
print(json.dumps(d['userInfo'], ensure_ascii=False, separators=(',', ':')))
