# -*- coding: utf-8 -*-
"""临时脚本：从工作量统计模板.xlsx 生成后端标准作业清单 JSON 资源"""
import openpyxl
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

wb = openpyxl.load_workbook(r'c:\Users\QianLong\Desktop\Hbsz\工作量统计模板.xlsx', data_only=True)
ws = wb['Sheet1']

items = []
category = None
seq = 0
for row in ws.iter_rows(min_row=4, max_row=109):
    a, b, c, d, e, f = row[0].value, row[1].value, row[2].value, row[3].value, row[4].value, row[5].value
    if c is None:
        continue
    # 合并单元格：作业类型只在组首行有值
    if b is not None:
        category = str(b).strip()
    # 额定工作量：模板 D27 为占位文本 'A'，按默认 1 处理；数值异常同样兜底
    try:
        rated = float(d) if d is not None else 1.0
        if rated <= 0:
            rated = 1.0
    except (TypeError, ValueError):
        rated = 1.0
    remark = None
    if f is not None and str(f).strip() not in ('0', ''):
        remark = str(f).strip()
    seq += 1
    items.append({
        'category': category,
        'workScope': str(c).strip(),
        'ratedWorkload': rated,
        'unit': str(e).strip() if e else '条',
        'remark': remark,
        'sortOrder': seq
    })

out = r'c:\Users\QianLong\Desktop\Hbsz\Hbsz_IDC_System_end\src\main\resources\workload-template.json'
with open(out, 'w', encoding='utf-8') as fp:
    json.dump(items, fp, ensure_ascii=False, indent=2)

cats = {}
for it in items:
    cats.setdefault(it['category'], 0)
    cats[it['category']] += 1
print(f"共 {len(items)} 项，{len(cats)} 类：")
for k, v in cats.items():
    print(f"  {k}: {v} 项")
