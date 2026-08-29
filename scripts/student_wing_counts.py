import firebase_admin
from firebase_admin import credentials, firestore
from collections import defaultdict

cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

users = list(db.collection('users').stream())
wing_counts = defaultdict(int)
total_students = 0
unassigned = []
multi_wing = []

for u in users:
    d = u.to_dict()
    if (d.get('userType') or 'student').lower().strip() == 'student':
        total_students += 1
        wings = d.get('wings', [])
        if not isinstance(wings, list):
            wings = [wings] if wings else []
        
        if not wings:
            unassigned.append((u.id, d.get('name', 'Unknown'), d.get('instituteOutlookId', '')))
        else:
            if len(wings) > 1:
                multi_wing.append((u.id, d.get('name', 'Unknown'), wings))
            for w in wings:
                wing_counts[w] += 1

print("=" * 60)
print(" STUDENT WING DISTRIBUTION:")
print("=" * 60)
for w, c in sorted(wing_counts.items(), key=lambda x: x[1], reverse=True):
    print(f"  • {w:<30}: {c:>3} students")

print("-" * 60)
print(f"  • Total Registered Students     : {total_students}")
print(f"  • Total Wing Allocations        : {sum(wing_counts.values())}")
print(f"  • Unassigned Students (No Wing) : {len(unassigned)}")
print(f"  • Multi-Wing Students           : {len(multi_wing)}")
print("=" * 60)

if unassigned:
    print("\nUnassigned Students:")
    for r, n, e in unassigned:
        print(f"  - {r}: {n} ({e})")

if multi_wing:
    print("\nStudents in Multiple Wings:")
    for r, n, ws in multi_wing:
        print(f"  - {r}: {n} -> {ws}")
