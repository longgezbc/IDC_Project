# -*- coding: utf-8 -*-
"""临时脚本：解析工作量统计模板.xlsx 的结构（sheet、表头、合并单元格、示例数据）"""
import openpyxl
import sys

sys.stdout.reconfigure(encoding='utf-8')

wb = openpyxl.load_workbook(r'c:\Users\QianLong\Desktop\Hbsz\工作量统计模板.xlsx', data_only=True)
print(f"Sheet 列表: {wb.sheetnames}\n")

for ws in wb.worksheets:
    print(f"===== Sheet: {ws.title} (dims={ws.dimensions}, max_row={ws.max_row}, max_col={ws.max_column}) =====")
    print(f"合并单元格: {[str(r) for r in ws.merged_cells.ranges]}")
    # 打印 31 行以后内容
    for row in ws.iter_rows(min_row=31, max_row=ws.max_row):
        vals = []
        for c in row:
            if c.value is not None:
                vals.append(f"{c.coordinate}={repr(c.value)}")
        if vals:
            print(" | ".join(vals))
    print()
