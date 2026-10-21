
import re
from collections import Counter

ids = []

with open("data/executions_insert.sql", encoding="utf-8") as file:
    for line_number, line in enumerate(file, 1):
        match = re.search(r"VALUES \((\d+),", line)
        if match:
            ids.append((int(match.group(1)), line_number))

counts = Counter(execution_id for execution_id, _ in ids)

print("Rows:", len(ids))
print("Unique IDs:", len(counts))
print("ID 1 occurrences:", [line for value, line in ids if value == 1])
print("Duplicate IDs:", [value for value, count in counts.items() if count > 1][:20])