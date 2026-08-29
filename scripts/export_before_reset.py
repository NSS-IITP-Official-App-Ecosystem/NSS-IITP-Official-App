"""
NSS IITP — Pre-Reset Local Backup
====================================================
READ-ONLY. Does NOT modify any data.

Exports to local JSON files:
  - All documents from 'users' collection
  - All documents from 'NSS_Events_Attendence' (including attendance subcollections)
  - Summary of PhotoAttendanceLog and scheduledNotifications counts

Output files (timestamped, in backups/ subfolder):
  backups/
    YYYYMMDD_HHMMSS_users.json
    YYYYMMDD_HHMMSS_events.json
    YYYYMMDD_HHMMSS_summary.txt

USAGE:
  python export_before_reset.py
"""

import os
import sys
import json
from datetime import datetime

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("Run: pip install firebase-admin")
    sys.exit(1)

# ─── CONFIG ───────────────────────────────────────────────────────────────────

# Output folder (relative to this script)
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "backups")

# Service account key locations to try
POSSIBLE_KEY_PATHS = [
    "C:/Users/itses/CodeVault/Projects/Keys/IITP App/service-account.json",
    "serviceAccountKey.json",
    "scripts/serviceAccountKey.json",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "serviceAccountKey.json"),
]

# ─── HELPERS ──────────────────────────────────────────────────────────────────

def find_service_account():
    for p in POSSIBLE_KEY_PATHS:
        if os.path.exists(p):
            print(f"  Found service account key: {os.path.abspath(p)}")
            return os.path.abspath(p)
    path = input("  Path to serviceAccountKey.json: ").strip().strip("'\"")
    if not os.path.exists(path):
        print(f"  ERROR: File not found: {path}")
        sys.exit(1)
    return path

def firestore_to_dict(doc):
    """Convert a Firestore document snapshot to a plain dict, handling Timestamps."""
    data = doc.to_dict() or {}
    return serialize(data)

def serialize(obj):
    """Recursively convert Firestore types to JSON-serializable Python types."""
    from google.cloud.firestore_v1 import base_document
    from google.cloud.firestore_v1._helpers import GeoPoint
    from google.protobuf.timestamp_pb2 import Timestamp as ProtoTimestamp

    if hasattr(obj, 'isoformat'):
        # datetime
        return obj.isoformat()
    if hasattr(obj, '_seconds') and hasattr(obj, '_nanoseconds'):
        # Firestore Timestamp
        try:
            return obj.strftime('%Y-%m-%dT%H:%M:%SZ')
        except Exception:
            return str(obj)
    if isinstance(obj, dict):
        return {k: serialize(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [serialize(item) for item in obj]
    if isinstance(obj, bytes):
        return obj.hex()
    return obj

def write_json(filepath, data):
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False, default=str)
    size_kb = os.path.getsize(filepath) / 1024
    print(f"  Saved: {filepath}  ({size_kb:.1f} KB)")

# ─── MAIN ─────────────────────────────────────────────────────────────────────

def main():
    print()
    print("=" * 65)
    print("  NSS IITP — Pre-Reset Local Backup (READ-ONLY)")
    print("=" * 65)
    print()
    print("  This script reads Firestore and saves JSON files locally.")
    print("  It does NOT write, delete, or modify any data.")
    print()

    # 1. Connect
    key_path = find_service_account()
    cred = credentials.Certificate(key_path)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    # 2. Create output dir
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"\n  Output directory: {OUTPUT_DIR}")
    print(f"  Timestamp prefix: {timestamp}\n")

    # ── Export users ──────────────────────────────────────────────────────────
    print("=" * 65)
    print("  STEP 1: Exporting users collection")
    print("=" * 65)

    users_docs = list(db.collection("users").stream())
    users_export = []

    admin_count   = 0
    student_count = 0
    nonzero_hours = 0

    for doc in users_docs:
        data = firestore_to_dict(doc)
        entry = {"_doc_id": doc.id, **data}
        users_export.append(entry)

        ut = (data.get("userType") or "").strip().lower()
        if ut == "admin":
            admin_count += 1
        else:
            student_count += 1
            hours = data.get("hours", 0) or 0
            if hours != 0:
                nonzero_hours += 1

    users_file = os.path.join(OUTPUT_DIR, f"{timestamp}_users.json")
    write_json(users_file, users_export)

    print(f"\n  Total user documents: {len(users_docs)}")
    print(f"    Admins:   {admin_count}")
    print(f"    Students: {student_count}  ({nonzero_hours} with non-zero hours)")

    # Print student hours preview
    print("\n  Student hours snapshot (before reset):")
    print(f"  {'Name':<35} {'Roll':<14} {'Hours':>6} {'Sem1':>6} {'Sem2':>6} {'Events':>6}")
    print(f"  {'-'*35} {'-'*14} {'-'*6} {'-'*6} {'-'*6} {'-'*6}")
    for entry in sorted(users_export, key=lambda x: x.get("rollNumber", "")):
        ut = (entry.get("userType") or "").strip().lower()
        if ut == "admin":
            continue
        name  = (entry.get("name") or "?")[:33]
        roll  = entry.get("rollNumber") or entry.get("_doc_id") or "?"
        hrs   = entry.get("hours", 0) or 0
        sem1  = entry.get("sem1Hours", 0) or 0
        sem2  = entry.get("sem2Hours", 0) or 0
        evts  = entry.get("eventsAttended", 0) or 0
        print(f"  {name:<35} {roll:<14} {hrs:>6} {sem1:>6} {sem2:>6} {evts:>6}")

    # ── Export events ─────────────────────────────────────────────────────────
    print()
    print("=" * 65)
    print("  STEP 2: Exporting NSS_Events_Attendence (incl. subcollections)")
    print("=" * 65)

    events_docs = list(db.collection("NSS_Events_Attendence").stream())
    events_export = []
    total_attendance_records = 0

    for doc in events_docs:
        data = firestore_to_dict(doc)
        entry = {"_doc_id": doc.id, **data}

        # Fetch attendance subcollection
        att_docs = list(doc.reference.collection("attendance").stream())
        total_attendance_records += len(att_docs)
        entry["_attendance_subcollection"] = [
            {"_doc_id": a.id, **firestore_to_dict(a)} for a in att_docs
        ]

        events_export.append(entry)
        mandatory = data.get("mandatory") or data.get("isMandatory") or False
        print(f"  [{data.get('eventDate','?')}] {data.get('eventName', doc.id)}"
              f"  |  {len(att_docs)} attendees"
              f"  |  {data.get('hours','?')}hrs"
              f"  {'(MANDATORY)' if mandatory else ''}")

    events_file = os.path.join(OUTPUT_DIR, f"{timestamp}_events.json")
    write_json(events_file, events_export)

    print(f"\n  Total events: {len(events_docs)}")
    print(f"  Total attendance records (across all events): {total_attendance_records}")

    # ── Count-only: other collections ─────────────────────────────────────────
    print()
    print("=" * 65)
    print("  STEP 3: Counting other collections (not exported to JSON)")
    print("=" * 65)

    photo_count = len(list(db.collection("PhotoAttendanceLog").stream()))
    notif_count = len(list(db.collection("scheduledNotifications").stream()))
    print(f"  PhotoAttendanceLog:      {photo_count} document(s)")
    print(f"  scheduledNotifications:  {notif_count} document(s)")

    # ── Summary file ──────────────────────────────────────────────────────────
    summary = (
        f"NSS IITP Pre-Reset Backup Summary\n"
        f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n"
        f"{'=' * 50}\n\n"
        f"users collection:\n"
        f"  Total documents:    {len(users_docs)}\n"
        f"  Admins:             {admin_count}\n"
        f"  Students:           {student_count}\n"
        f"  Non-zero hours:     {nonzero_hours}\n\n"
        f"NSS_Events_Attendence:\n"
        f"  Total events:       {len(events_docs)}\n"
        f"  Attendance records: {total_attendance_records}\n\n"
        f"Other (counted, not exported):\n"
        f"  PhotoAttendanceLog:      {photo_count}\n"
        f"  scheduledNotifications:  {notif_count}\n\n"
        f"Files written:\n"
        f"  {users_file}\n"
        f"  {events_file}\n\n"
        f"IMPORTANT: These JSON files are a human-readable snapshot.\n"
        f"For a true restore-capable backup, use the official\n"
        f"Firestore export to Cloud Storage.\n"
    )

    summary_file = os.path.join(OUTPUT_DIR, f"{timestamp}_summary.txt")
    with open(summary_file, 'w', encoding='utf-8') as f:
        f.write(summary)
    print(f"\n  Summary saved: {summary_file}")

    # ── Final ─────────────────────────────────────────────────────────────────
    print()
    print("=" * 65)
    print("  BACKUP COMPLETE — Nothing was modified in Firestore.")
    print("=" * 65)
    print(f"\n  Files are in: {OUTPUT_DIR}")
    print(f"  Open them in any text editor or JSON viewer to verify.")
    print()
    print("  Checklist before running the reset:")
    print("  [ ] Verify users JSON has the correct number of students")
    print("  [ ] Verify events JSON has all expected events with attendees")
    print("  [ ] Official Firestore Cloud Storage export also completed")
    print("  [ ] Then run: node scripts/reset_for_new_cycle.js --live")
    print()

if __name__ == "__main__":
    main()
