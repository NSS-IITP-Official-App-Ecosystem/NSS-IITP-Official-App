"""
Count total students in each NSS wing whose roll number starts with '26' (2026 batch).
Usage:
    python count_students_by_wing.py
    python count_students_by_wing.py --cred_path ./serviceAccountKey.json
    python count_students_by_wing.py --prefix 25   # for 2025 batch
"""

import firebase_admin
from firebase_admin import credentials, firestore
import argparse
import sys
import os


def initialize_firebase(cred_path=None):
    if not firebase_admin._apps:
        try:
            if cred_path and os.path.exists(cred_path):
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred)
            else:
                firebase_admin.initialize_app()
        except Exception as e:
            print(f"Failed to initialize Firebase: {e}")
            print("Please provide a path to a service account JSON file using --cred_path")
            sys.exit(1)


def count_students_by_wing(prefix="26", cred_path=None):
    initialize_firebase(cred_path)
    try:
        db = firestore.client()
    except Exception as e:
        print("\n❌ Error: Google Application Default Credentials not found.")
        print('Usage: python count_students_by_wing.py --cred_path "./serviceAccountKey.json"')
        return

    print(f"\nFetching all users with roll number prefix '{prefix}'...\n")

    try:
        users = db.collection("users").stream()

        # Wing -> list of roll numbers
        wing_counts = {}
        no_wing_students = []
        total_matched = 0

        for doc in users:
            data = doc.to_dict()
            roll = data.get("rollNumber") or data.get("roll_number") or doc.id

            # Check if roll number starts with the given prefix
            if not str(roll).startswith(prefix):
                continue

            total_matched += 1

            # Extract wings from the document
            wings_list = []
            wings_obj = data.get("wings") or data.get("wing")
            if isinstance(wings_obj, list):
                wings_list = [str(w) for w in wings_obj if w]
            elif isinstance(wings_obj, str) and wings_obj.strip():
                wings_list = [wings_obj.strip()]

            # Fallback to legacy NSS_gro field
            if not wings_list:
                nss_gro = data.get("NSS_gro", "")
                if nss_gro and str(nss_gro).strip():
                    wings_list = [str(nss_gro).strip()]

            if not wings_list:
                no_wing_students.append(roll)
            else:
                for wing in wings_list:
                    if wing not in wing_counts:
                        wing_counts[wing] = []
                    wing_counts[wing].append(roll)

        # Print results
        print("=" * 60)
        print(f"  STUDENTS BY WING (Roll Number Prefix: '{prefix}')")
        print("=" * 60)

        if not wing_counts and not no_wing_students:
            print(f"\n  No students found with roll number prefix '{prefix}'.")
            return

        # Sort wings by count (descending)
        sorted_wings = sorted(wing_counts.items(), key=lambda x: len(x[1]), reverse=True)

        grand_total = 0
        for wing, rolls in sorted_wings:
            count = len(rolls)
            grand_total += count
            print(f"\n  [*] {wing}")
            print(f"      Count: {count}")

        if no_wing_students:
            print(f"\n  [!] No Wing Assigned")
            print(f"      Count: {len(no_wing_students)}")
            grand_total += len(no_wing_students)

        print(f"\n{'=' * 60}")
        print(f"  TOTAL STUDENTS WITH PREFIX '{prefix}': {total_matched}")
        print(f"  TOTAL WING ASSIGNMENTS: {grand_total}")
        if grand_total > total_matched:
            print(f"     (Some students belong to multiple wings)")
        print(f"{'=' * 60}\n")

    except Exception as e:
        print(f"Error querying Firestore: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Count students in each wing with a given roll number prefix."
    )
    parser.add_argument(
        "--prefix",
        type=str,
        default="26",
        help="Roll number prefix to filter by (default: '26')",
    )
    parser.add_argument(
        "--cred_path",
        type=str,
        default=None,
        help="Path to Firebase service account JSON file.",
    )

    args = parser.parse_args()
    count_students_by_wing(args.prefix, args.cred_path)

