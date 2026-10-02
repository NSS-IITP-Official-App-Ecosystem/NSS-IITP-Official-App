import firebase_admin
from firebase_admin import credentials, firestore
import openpyxl
import os

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

excel_path = os.path.join('students data', 'NSS_Allocation_2026-27.xlsx')
wb = openpyxl.load_workbook(excel_path)
sheet = wb['Sheet1']

excel_rolls = set()
excel_records = []
for row in sheet.iter_rows(min_row=2, values_only=True):
    if row and any(row):
        email, roll_no, name, allocation = (row[0], row[1], row[2], row[3]) if len(row) >= 4 else (None, None, None, None)
        if roll_no:
            roll_clean = str(roll_no).strip().upper()
            excel_rolls.add(roll_clean)
            excel_records.append({
                'email': email,
                'roll': roll_clean,
                'name': name,
                'allocation': allocation
            })

print(f"Total rows in Excel: {len(excel_records)}")
print(f"Total unique rolls in Excel: {len(excel_rolls)}")

users_ref = db.collection('users')
docs = list(users_ref.stream())
print(f"Total documents in 'users' collection: {len(docs)}")

db_users_26 = []
for doc in docs:
    data = doc.to_dict() or {}
    doc_id = doc.id.strip().upper()
    
    # Check fields for roll
    roll_candidate = data.get('roll_no') or data.get('rollNo') or data.get('roll') or data.get('rollNumber') or doc.id
    roll_str = str(roll_candidate).strip().upper()
    
    if roll_str.startswith('26') or doc_id.startswith('26'):
        name = data.get('name') or data.get('userName') or data.get('full_name') or data.get('displayName') or 'N/A'
        wing = data.get('wing') or data.get('wings') or data.get('allocated_wing') or data.get('wing_name') or 'N/A'
        
        # If wing is a list or dict
        if isinstance(wing, list):
            wing = ", ".join(str(w) for w in wing)
            
        db_users_26.append({
            'doc_id': doc.id,
            'roll': roll_str,
            'name': name,
            'wing': wing,
            'email': data.get('email', 'N/A'),
            'raw_data': data
        })

print(f"Total users in DB starting with '26': {len(db_users_26)}")

# Identify those not in Excel
missing_in_excel = []
for u in db_users_26:
    if u['roll'] not in excel_rolls and u['doc_id'].upper() not in excel_rolls:
        missing_in_excel.append(u)

print(f"\n==========================================")
print(f"COUNT OF USERS IN DB BUT NOT IN EXCEL: {len(missing_in_excel)}")
print(f"==========================================\n")

for idx, u in enumerate(missing_in_excel, 1):
    print(f"{idx}. Name: {u['name']} | Roll: {u['roll']} | Wing: {u['wing']} | Email: {u['email']}")
    print(f"   Doc ID: {u['doc_id']}")
    # print keys of raw_data just in case
    print(f"   Fields present: {list(u['raw_data'].keys())}")
    print()
