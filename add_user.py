"""
Add a new user to the Firestore 'users' collection.
Usage:
    python add_user.py --cred_path ./serviceAccountKey.json
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
            sys.exit(1)


def add_user(cred_path=None):
    initialize_firebase(cred_path)
    try:
        db = firestore.client()
    except Exception as e:
        print(f"\nError connecting to Firestore: {e}")
        return

    # ── User details ──────────────────────────────────────────
    roll_number       = "2601CS44"
    name              = "POURA VENKATA BHAVESH"
    email             = "poura_2601cs44@iitp.ac.in"
    user_type         = "student"
    wings             = ["Rural Development Wing"]
    # ─────────────────────────────────────────────────────────

    doc_ref = db.collection("users").document(roll_number)

    # Check if already exists
    existing = doc_ref.get()
    if existing.exists:
        print(f"\n[!] User '{roll_number}' already exists in the database.")
        print(f"    Existing data: {existing.to_dict()}")
        print("\nAbort: no changes made.")
        return

    user_data = {
        "name":               name,
        "rollNumber":         roll_number,
        "instituteOutlookId": email,
        "userType":           user_type,
        "wings":              wings,
        "hours":              0,
        "unreadCount":        0,
        # uid and fcmToken will be populated on first login
    }

    doc_ref.set(user_data)

    print("\n" + "=" * 60)
    print("  USER ADDED SUCCESSFULLY")
    print("=" * 60)
    print(f"  Document ID  : {roll_number}")
    print(f"  Name         : {name}")
    print(f"  Email        : {email}")
    print(f"  User Type    : {user_type}")
    print(f"  Wing(s)      : {', '.join(wings)}")
    print(f"  Hours        : 0")
    print("=" * 60)
    print("\nNote: uid and fcmToken will be set automatically on first login.\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Add a new user to the Firestore database.")
    parser.add_argument(
        "--cred_path",
        type=str,
        default=None,
        help="Path to Firebase service account JSON file.",
    )
    args = parser.parse_args()
    add_user(args.cred_path)

