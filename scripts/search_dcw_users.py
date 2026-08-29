import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
print(f"Loaded {len(users)} users from Firestore.\n")

targets = [
    ("ARSIA", ["arsia"]),
    ("Rahul Prasad", ["rahul", "prasad"]),
    ("Nehal Kandibanda", ["kandibanda", "nehal"]),
    ("Ameya Kamat", ["ameya", "kamat"]),
    ("Anirudh.M", ["anirudh"]),
    ("Shaurya Gupta", ["shaurya", "gupta"]),
    ("Neha", ["neha"]),
    ("Jayshri Agarwal", ["jayshri", "agarwal"]),
    ("ABHISHEK", ["abhishek"]),
    ("Nishad Baviskar", ["nishad", "baviskar"]),
    ("Banoth Siddharth", ["banoth", "sid"]),
    ("Srinaina gowru", ["srinaina", "gowru"]),
    ("Hemani Dhande", ["hemani", "dhande"]),
    ("Ahon Pansa", ["ahon", "pansa"]),
    ("Anubhu Das", ["anubhu", "das"]),
    ("Lakshya Malkhede", ["lakshya", "malkhede"]),
    ("Sneha Kale", ["sneha", "kale"]),
    ("Ayush Rajput", ["rajput", "ayush"]),
    ("priyamgaurvi", ["priyamgaurvi"]),
    ("Parth Tokalwad", ["tokalwad", "parth"]),
]

for label, keywords in targets:
    print(f"=== Search: {label} ===")
    matches = []
    for u in users:
        d = u.to_dict()
        n = (d.get('name') or '').lower()
        e = (d.get('instituteOutlookId') or '').lower()
        r = u.id.lower()
        
        # Check if any keyword in name/email
        matched_kw = [k for k in keywords if k in n or k in e or k in r]
        if len(matched_kw) == len(keywords):
            matches.append((u.id, d.get('name'), d.get('instituteOutlookId'), d.get('wings'), d.get('userType'), "FULL"))
        elif len(matched_kw) > 0:
            matches.append((u.id, d.get('name'), d.get('instituteOutlookId'), d.get('wings'), d.get('userType'), f"PARTIAL({matched_kw})"))

    for m in matches:
        print(f"  [{m[5]}] Roll: {m[0]} | Name: {m[1]} | Email: {m[2]} | Wings: {m[3]} | Type: {m[4]}")
    if not matches:
        print("  NO MATCH FOUND")
    print()
