import json
import re

transcript_path = r'C:\Users\sm273\.gemini\antigravity-ide\brain\8006f607-7f91-4ebe-be78-17a52f90cfb8\.system_generated\logs\transcript.jsonl'

found_lines = []
with open(transcript_path, 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        if 'Vivek Goyal' in line:
            obj = json.loads(line)
            found_lines.append(obj.get('content', ''))

print(f"Found {len(found_lines)} occurrences in transcript.")
if found_lines:
    with open('scripts/recovered_ttw_classes.txt', 'w', encoding='utf-8') as out:
        out.write(found_lines[-1])
    print("Wrote output to scripts/recovered_ttw_classes.txt")
