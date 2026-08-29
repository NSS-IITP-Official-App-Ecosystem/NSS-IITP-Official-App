"""
Read-only: Full userType audit - lists EVERY user document grouped by userType.
No emoji, ASCII-safe output to avoid encoding issues on Windows.
"""
import os, sys
# Force UTF-8 output
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("Run: pip install firebase-admin")
    sys.exit(1)

possible_paths = [
    "C:/Users/itses/CodeVault/Projects/Keys/IITP App/service-account.json",
    "serviceAccountKey.json",
    "scripts/serviceAccountKey.json",
]
key_path = None
for p in possible_paths:
    if os.path.exists(p):
        key_path = os.path.abspath(p)
        break
if not key_path:
    key_path = input("Path to serviceAccountKey.json: ").strip().strip("'\"")

cred = credentials.Certificate(key_path)
firebase_admin.initialize_app(cred)
db = firestore.client()

print()
print("Querying users collection (READ-ONLY, no changes)...")
print()

docs = list(db.collection("users").stream())
print(f"Total documents in users collection: {len(docs)}")
print()

type_map = {}   # userType raw value -> list of (docId, name, roll)
missing  = []

for doc in docs:
    data = doc.to_dict()
    ut   = data.get("userType")          # exact raw value from Firestore
    name = data.get("name", "?")
    roll = data.get("rollNumber", doc.id)
    if ut is None:
        missing.append((doc.id, name, roll, ut))
    else:
        type_map.setdefault(ut, []).append((doc.id, name, roll))

# Sort by userType for readability
print("=" * 70)
print(f"  DISTINCT userType VALUES: {len(type_map)}")
print("=" * 70)

for ut in sorted(type_map.keys()):
    users = type_map[ut]
    # Show the raw repr so any hidden chars (spaces, tabs) are visible
    print(f"\nuserType = {repr(ut)}   ({len(users)} document(s))")
    print(f"  {'Roll Number':<16} {'Name':<40} Doc ID")
    print(f"  {'-'*16} {'-'*40} {'-'*16}")
    for (doc_id, name, roll) in sorted(users, key=lambda x: x[2]):
        name_trunc = name[:38] if len(name) > 38 else name
        print(f"  {roll:<16} {name_trunc:<40} {doc_id}")

if missing:
    print(f"\nuserType = MISSING   ({len(missing)} document(s))")
    print(f"  {'Roll Number':<16} {'Name':<40} Doc ID")
    print(f"  {'-'*16} {'-'*40} {'-'*16}")
    for (doc_id, name, roll, _) in missing:
        name_trunc = name[:38] if len(name) > 38 else name
        print(f"  {roll:<16} {name_trunc:<40} {doc_id}")
else:
    print("\n  No documents with missing userType field.")

print()
print("=" * 70)
print("  ADMIN EXCLUSION CHECK ANALYSIS")
print("  (Reset script uses: userType.trim().lower() == 'admin')")
print("=" * 70)

for ut in sorted(type_map.keys()):
    stripped = ut.strip().lower()
    count = len(type_map[ut])
    if stripped == 'admin':
        status = "EXCLUDED (safe - admin)"
    elif 'admin' in stripped:
        status = "*** RISK - contains 'admin' but won't match exactly ***"
    else:
        status = "included (student - hours will be reset)"
    print(f"  {repr(ut):<20}  {count:>4} docs  -> {status}")

if missing:
    print(f"  {'MISSING':<20}  {len(missing):>4} docs  -> included (treated as student)")

print("=" * 70)
print()
