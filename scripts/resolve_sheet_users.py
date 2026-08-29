import csv
import re
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
user_map_by_roll = {}
user_map_by_email = {}
user_map_by_name = []

for u in users:
    d = u.to_dict()
    r = u.id.upper().strip()
    name = (d.get('name') or '').strip()
    email = (d.get('instituteOutlookId') or '').strip().lower()
    user_map_by_roll[r] = (name, email)
    if email:
        user_map_by_email[email] = (r, name)
    if name:
        user_map_by_name.append((r, name, email))

with open("scripts/sheet_users.csv", "r", encoding="utf-8") as f:
    rows = list(csv.reader(f))

entries = []

# Scan for structured blocks across columns
for row_idx, r in enumerate(rows):
    # Check left block (cols 0..4)
    if len(r) > 0 and r[0].strip() and r[0].strip().lower() not in ['name', '']:
        name = r[0].strip()
        roll = r[1].strip().upper() if len(r) > 1 else ''
        # could be outlook in col 2 or col 3
        col2 = r[2].strip() if len(r) > 2 else ''
        col3 = r[3].strip() if len(r) > 3 else ''
        col4 = r[4].strip() if len(r) > 4 else ''
        
        email = ''
        contact = ''
        pref = ''
        for val in [col2, col3, col4]:
            if '@' in val:
                email = val.lower()
            elif val.isdigit() and len(val) >= 9:
                contact = val
            elif val in ['1', '2', '3', '4', '5']:
                pref = val
        
        entries.append({
            'source_row': row_idx + 1,
            'source_block': 'left',
            'name': name,
            'roll': roll,
            'email': email,
            'contact': contact,
            'pref': pref
        })

    # Check right block (cols 5..10)
    if len(r) > 5 and r[5].strip() and r[5].strip().lower() not in ['name', '']:
        name = r[5].strip()
        roll = r[6].strip().upper() if len(r) > 6 else ''
        col7 = r[7].strip() if len(r) > 7 else ''
        col8 = r[8].strip() if len(r) > 8 else ''
        col9 = r[9].strip() if len(r) > 9 else ''
        
        email = ''
        contact = ''
        pref = ''
        for val in [col7, col8, col9]:
            if '@' in val:
                email = val.lower()
            elif val.isdigit() and len(val) >= 9:
                contact = val
            elif val in ['1', '2', '3', '4', '5']:
                pref = val
                
        entries.append({
            'source_row': row_idx + 1,
            'source_block': 'right',
            'name': name,
            'roll': roll,
            'email': email,
            'contact': contact,
            'pref': pref
        })

print(f"Extracted {len(entries)} raw entries from the spreadsheet.\n")

# Match each entry with Firestore DB
resolved_entries = []
for entry in entries:
    name = entry['name']
    roll = entry['roll']
    email = entry['email']
    
    # 1. Match by roll if valid roll
    matched_roll = roll
    matched_name = name
    matched_email = email
    
    if roll in user_map_by_roll:
        matched_name = user_map_by_roll[roll][0]
        matched_email = user_map_by_roll[roll][1]
    elif email in user_map_by_email:
        matched_roll = user_map_by_email[email][0]
        matched_name = user_map_by_email[email][1]
    else:
        # fuzzy match by name
        clean_name = re.sub(r'[^a-zA-Z\s]', '', name).lower().strip()
        name_tokens = [t for t in clean_name.split() if len(t) > 1]
        best_match = None
        for r_db, n_db, e_db in user_map_by_name:
            n_clean = re.sub(r'[^a-zA-Z\s]', '', n_db).lower().strip()
            if all(t in n_clean for t in name_tokens):
                best_match = (r_db, n_db, e_db)
                break
        if best_match:
            matched_roll, matched_name, matched_email = best_match

    entry['matched_roll'] = matched_roll
    entry['matched_name'] = matched_name
    entry['matched_email'] = matched_email
    resolved_entries.append(entry)

# Print missing or resolved
missing = [e for e in resolved_entries if not e['matched_roll'] or not e['matched_email']]
print(f"Entries missing roll or email: {len(missing)}")
for m in missing:
    print(m)

# Deduplicate unique people
unique_people = {}
for e in resolved_entries:
    key = e['matched_roll'] if e['matched_roll'] else e['name'].lower()
    if key not in unique_people:
        unique_people[key] = e

print(f"\nTotal unique users found in sheet: {len(unique_people)}")
