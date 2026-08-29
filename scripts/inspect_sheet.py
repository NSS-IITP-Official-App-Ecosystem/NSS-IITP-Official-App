import csv

with open("scripts/sheet_users.csv", "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    rows = list(reader)

print(f"Total rows in sheet: {len(rows)}")
for i, r in enumerate(rows[:20]):
    print(f"Row {i}: {r}")
