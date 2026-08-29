import io
import re
import sys
import urllib.request
import pandas as pd
import firebase_admin
from firebase_admin import credentials, firestore

sys.stdout.reconfigure(encoding='utf-8')

import os

cert_path = os.path.join(os.path.dirname(__file__), '..', 'serviceAccountKey.json')
if not os.path.exists(cert_path):
    cert_path = 'serviceAccountKey.json'

cred = credentials.Certificate(cert_path)
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# Fetch Sheet CSV
url = 'https://docs.google.com/spreadsheets/d/1UNX_6LCymh8YHki7_YfRIzj2UbmRbR1g-MvCmiuzTWc/export?format=csv'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as resp:
    content = resp.read().decode('utf-8')
    df = pd.read_csv(io.StringIO(content))

roll_col = [c for c in df.columns if 'roll' in c.lower()][0]
group_col = [c for c in df.columns if 'group' in c.lower()][0]
name_col = 'Name'

sheet_groups = {}
for idx, row in df.iterrows():
    r = str(row[roll_col]).strip().upper()
    g = str(row[group_col]).strip()
    name = str(row[name_col]).strip() if name_col in row else ""
    if r and r != 'NAN' and r != 'NONE':
        match = re.search(r'\d+', g)
        if match:
            group_num = match.group(0)
            sheet_groups[r] = {
                'raw': g,
                'num_str': group_num,
                'num_int': int(group_num),
                'g_str': f'G{group_num}',
                'name': name
            }

print(f"Total valid entries from Google Sheet: {len(sheet_groups)}")

# Fetch existing ttwStudents and users
ttw_docs = {d.id.upper().strip(): d.to_dict() for d in db.collection('ttwStudents').stream()}
users_docs = {d.id.upper().strip(): d.to_dict() for d in db.collection('users').stream()}

print(f"Total documents in ttwStudents: {len(ttw_docs)}")
print(f"Total documents in users: {len(users_docs)}")

ttw_updates = 0
users_updates = 0

batch = db.batch()
batch_size = 0

for roll, info in sorted(sheet_groups.items()):
    # 1. Update ttwStudents
    ttw_ref = db.collection('ttwStudents').document(roll)
    ttw_payload = {
        'academicGroup': info['num_str'],
        'group': info['num_int']
    }
    batch.set(ttw_ref, ttw_payload, merge=True)
    ttw_updates += 1
    batch_size += 1

    # 2. Update users collection if doc exists
    if roll in users_docs:
        user_ref = db.collection('users').document(roll)
        batch.update(user_ref, {
            'academicGroup': info['g_str']
        })
        users_updates += 1
        batch_size += 1

    if batch_size >= 400:
        batch.commit()
        batch = db.batch()
        batch_size = 0

if batch_size > 0:
    batch.commit()

print(f"\nSuccessfully updated academic groups from Google Sheet:")
print(f"  -> {ttw_updates} ttwStudents documents updated (academicGroup: 'X', group: X)")
print(f"  -> {users_updates} users documents updated (academicGroup: 'GX')")
