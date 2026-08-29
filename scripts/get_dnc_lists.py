import csv
import re
import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
user_map_by_roll = {}
for u in users:
    d = u.to_dict()
    r = u.id.upper().strip()
    user_map_by_roll[r] = (d.get('name', ''), d.get('instituteOutlookId', ''))

# 1. Selected DnC members
dnc_selected = [
    # Poster Making Team
    ("ARSIA", "2601MM23", "Poster Making Team"),
    ("Rahul Prasad", "2603AI03", "Poster Making Team"),
    ("Nehal Kandibanda", "2601AI32", "Poster Making Team"),
    ("Ameya Kamat", "2601CE26", "Poster Making Team"),
    ("Anirudh.M (Anirudh Makkapati)", "2602ST05", "Poster Making Team"),
    ("Shaurya Gupta", "2601CT01", "Poster Making Team"),
    # Video Editing Team
    ("Neha", "2601PH20", "Video Editing Team"),
    ("Jayshri Agarwal", "2601ES20", "Video Editing Team"),
    ("ABHISHEK", "2601MM15", "Video Editing Team"),
    ("Nishad Baviskar", "2603AI02", "Video Editing Team"),
    ("Banoth Siddharth", "2601MC37", "Video Editing Team"),
    ("Srinaina gowru", "2601EC27", "Video Editing Team"),
    # Photography Team
    ("Hemani Dhande", "2602VL08", "Photography Team"),
    ("Ahon Pansa", "2601CE51", "Photography Team"),
    ("Anubhu Das", "2601MC09", "Photography Team"),
    ("Lakshya Malkhede", "2601MC05", "Photography Team"),
    ("Sneha Kale", "2601EE42", "Photography Team"),
    ("Ayush Rajput (Ayush Singh)", "2603CE06", "Photography Team"),
    ("priyamgaurvi", "2601CE06", "Photography Team"),
    ("Parth Tokalwad", "2601AI44", "Photography Team"),
]

print("=== 20 SELECTED DNC TEAM MEMBERS ===")
for name, roll, team in dnc_selected:
    db_name, email = user_map_by_roll.get(roll, (name, 'N/A'))
    print(f"{roll}\t{email}\t{db_name}\t{team}")

# 2. Preference 1 list from the sheet
with open("scripts/sheet_users.csv", "r", encoding="utf-8") as f:
    rows = list(csv.reader(f))

pref1_entries = []
for r in rows[:50]:
    if len(r) > 0 and r[0].strip() and r[0].strip().lower() not in ['name', '']:
        name = r[0].strip()
        roll = r[1].strip().upper() if len(r) > 1 else ''
        if 'togarwad' in name.lower():
            roll = '2601AI44'
        if roll:
            pref1_entries.append((name, roll))
    if len(r) > 5 and r[5].strip() and r[5].strip().lower() not in ['name', '']:
        name = r[5].strip()
        roll = r[6].strip().upper() if len(r) > 6 else ''
        if 'togarwad' in name.lower():
            roll = '2601AI44'
        if roll:
            pref1_entries.append((name, roll))

print("\n=== PREFERENCE 1 SHEET APPLICANTS ===")
seen = set()
for name, roll in pref1_entries:
    if roll in seen:
        continue
    seen.add(roll)
    db_name, email = user_map_by_roll.get(roll, (name, 'N/A'))
    print(f"{roll}\t{email}\t{db_name}")
