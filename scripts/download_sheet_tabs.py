import urllib.request
import re
import csv
import json

url = 'https://docs.google.com/spreadsheets/d/1c5R6JCF84dEK9n3I3G02GPrKSMkN_-wcnknOgbuZvNA/edit?usp=sharing'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
html = urllib.request.urlopen(req).read().decode('utf-8')

# Find sheet metadata
gids = re.findall(r'gid=(\d+)', html)
print(f"Unique GIDs found in HTML: {set(gids)}")

# Look for bootstrap data / sheet names
matches = re.findall(r'\{\s*"name":\s*"([^"]+)",\s*"id":\s*(\d+)', html)
if not matches:
    matches = re.findall(r'\[\s*"([^"]+)",\s*(\d+),\s*\d+,\s*\d+,\s*\[', html)
print(f"Sheet matches: {matches}")

# Download each sheet gid as CSV
sheet_id = '1c5R6JCF84dEK9n3I3G02GPrKSMkN_-wcnknOgbuZvNA'
gids_to_try = set(gids) if gids else {'0'}
if not gids_to_try:
    gids_to_try = {'0'}

all_data = {}
for gid in gids_to_try:
    csv_url = f"https://docs.google.com/spreadsheets/d/{sheet_id}/export?format=csv&gid={gid}"
    try:
        req = urllib.request.Request(csv_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode('utf-8')
            all_data[gid] = content
            print(f"Successfully downloaded sheet gid={gid} ({len(content)} bytes)")
    except Exception as e:
        print(f"Failed to download gid={gid}: {e}")

with open("scripts/sheet_export_all.json", "w", encoding="utf-8") as f:
    json.dump(all_data, f)
