import sys
try:
    from firebase_admin import credentials, firestore, initialize_app
except ImportError:
    sys.exit(1)

cred = credentials.Certificate('../serviceAccountKey.json')
initialize_app(cred)
db = firestore.client()

print("--- FINAL STATE SUMMARY (PRODUCTION) ---")

# 1. Count in NSS_Events_Attendence
live_events = list(db.collection('NSS_Events_Attendence').stream())
print(f"1. Active Events (NSS_Events_Attendence): {len(live_events)}")

# 2. Count in NSS_Events_Attendence_2024_2025
archive_events = list(db.collection('NSS_Events_Attendence_2024_2025').stream())
print(f"2. Archived Events (NSS_Events_Attendence_2024_2025): {len(archive_events)}")

# Query Users
users = list(db.collection('users').stream())

non_zero_students = 0
admins = 0
admin_with_hours_preserved = 0
legacy_wings = 0
prerna_wings = 0
total_users = len(users)

for doc in users:
    d = doc.to_dict() or {}
    user_type = str(d.get('userType', '')).lower().strip()
    hours = d.get('hours', 0) or 0
    sem1 = d.get('sem1Hours', 0) or 0
    sem2 = d.get('sem2Hours', 0) or 0
    
    # 3 & 5. Admin and Hours count
    if user_type == 'admin':
        admins += 1
        if hours > 0 or sem1 > 0 or sem2 > 0:
            admin_with_hours_preserved += 1
    else:
        if hours > 0 or sem1 > 0 or sem2 > 0:
            non_zero_students += 1
            
    # 4. Wings logic
    wings = d.get('wings', [])
    if isinstance(wings, list):
        if any('chetna' in str(w).lower() or 'prayatna' in str(w).lower() for w in wings):
            legacy_wings += 1
        if any('prerna' in str(w).lower() for w in wings):
            prerna_wings += 1
            
print(f"3. Students with non-zero hours: {non_zero_students}")
print(f"4. Users with legacy Chetna/Prayatna Wings: {legacy_wings}")
print(f"   Users correctly tagged with Prerna Wing: {prerna_wings}")
print(f"5. Total Admins: {admins}")
print(f"   Admins with hours preserved (not reset): {admin_with_hours_preserved}")
print(f"   Total Users Analyzed: {total_users}")
