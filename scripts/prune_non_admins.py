import csv
import firebase_admin
from firebase_admin import credentials, firestore, auth

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

allowed_rolls = set()

# Read the CSV to get allowed roll numbers
with open("scripts/admins_2026.csv", "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    next(reader)
    for row in reader:
        if len(row) >= 3 and row[2].strip():
            roll = row[2].strip().upper()
            allowed_rolls.add(roll)

# Explicitly add Sourav and Ayush
allowed_rolls.add("2501EE04")
allowed_rolls.add("2501CS20")

print(f"Loaded {len(allowed_rolls)} allowed admin roll numbers.")
print("Starting mass deletion of all other users...")

users = db.collection("users").get()
firestore_deletes = 0
auth_deletes = 0

batch = db.batch()
count = 0

for doc in users:
    roll = doc.id.upper()
    data = doc.to_dict()
    
    if roll not in allowed_rolls:
        # Delete from Firestore
        batch.delete(doc.reference)
        count += 1
        firestore_deletes += 1
        
        # Try to delete from Firebase Auth
        email = data.get("instituteOutlookId")
        if email:
            try:
                user_record = auth.get_user_by_email(email)
                auth.delete_user(user_record.uid)
                auth_deletes += 1
            except auth.UserNotFoundError:
                pass # Already deleted or never created in Auth
            except Exception as e:
                print(f"Error deleting auth for {email}: {e}")
                
        if count >= 450:
            batch.commit()
            batch = db.batch()
            count = 0

if count > 0:
    batch.commit()

# Also clean up Auth for any dangling accounts not in the allowed list
# (In case they exist in Auth but not in Firestore)
print("\nChecking Firebase Auth for dangling accounts...")
try:
    page = auth.list_users()
    while page:
        for user in page.users:
            # Assuming email format is typically rollnumber@iitp.ac.in or similar,
            # but we can check if their email starts with an allowed roll number.
            # Safest is just to parse the roll number from the email:
            email = user.email
            if email:
                prefix = email.split('@')[0]
                # Try to extract a roll number (e.g., 'name_2501cs01' -> '2501CS01')
                import re
                match = re.search(r'([0-9]{4}[A-Za-z]{2}[0-9]{2})', prefix)
                if match:
                    auth_roll = match.group(1).upper()
                    if auth_roll not in allowed_rolls:
                        auth.delete_user(user.uid)
                        auth_deletes += 1
                        
        page = page.get_next_page()
except Exception as e:
    print(f"Error checking Auth list: {e}")

print(f"\n✅ SUCCESS!")
print(f"Deleted {firestore_deletes} non-admin profiles from Firestore.")
print(f"Deleted {auth_deletes} non-admin accounts from Firebase Auth.")
