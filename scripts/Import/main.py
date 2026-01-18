#!/usr/bin/env python3
"""
Import admins from the attached CSV into Firebase Auth and Firestore (users collection).
Only defined fields are written:
- name (from CSV Full Name)
- rollNumber (from CSV Roll No...)
- instituteOutlookId (from CSV Institute outlook ID)
- uid (from Firebase Auth)
- unreadCount (0)
- userType ('admin')
- wings (parsed from CSV Wings...)

Password is NOT written to Firestore; Auth manages credentials.
"""

import argparse
import csv
import os
import re
import sys
from typing import Dict, List, Optional, Tuple

import firebase_admin
from firebase_admin import credentials, auth
from google.cloud import firestore


# Map normalized CSV header -> logical key
HEADER_ALIASES: Dict[str, str] = {
    "fullname": "name",
    "rollnolettersshouldbecapitaleg2401cs16": "roll_number",
    "instituteoutlookid": "institute_outlook_id",
    "wingsyouarepartofselectallofthem": "wings",
}

REQUIRED_INTERNAL_KEYS = ["name", "roll_number", "institute_outlook_id"]


def normalize(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", (s or "").strip().lower())


def to_roll_number(value: str) -> str:
    return (value or "").strip().upper()


def to_email(value: str) -> str:
    return (value or "").strip().lower()


def parse_wings(value: str) -> List[str]:
    if not value:
        return []
    return [p.strip() for p in value.split(",") if p.strip()]


def init_firebase(service_account_path: str, project_id: Optional[str]) -> firestore.Client:
    if not firebase_admin._apps:
        cred = credentials.Certificate(service_account_path)
        firebase_admin.initialize_app(cred, {"projectId": project_id} if project_id else None)
    return firestore.Client(project=project_id) if project_id else firestore.Client()


def get_or_create_auth_user(email: str, default_password: Optional[str]) -> Tuple[str, bool]:
    try:
        user_record = auth.get_user_by_email(email)
        return user_record.uid, False
    except auth.UserNotFoundError:
        if default_password and len(default_password) >= 8:
            password_to_use = default_password
        else:
            password_to_use = "Temp@" + re.sub(r"[^A-Za-z0-9]", "", os.urandom(8).hex())
        user_record = auth.create_user(email=email, password=password_to_use, email_verified=True)
        return user_record.uid, True


def upsert_user_doc(db: firestore.Client, roll_number: str, data: dict, dry_run: bool) -> None:
    if dry_run:
        return
    db.collection("users").document(roll_number).set(data, merge=True)


def map_headers(fieldnames: List[str]) -> Dict[str, str]:
    mapping: Dict[str, str] = {}
    norm_to_actual: Dict[str, str] = {normalize(h): h for h in fieldnames}
    for norm, key in HEADER_ALIASES.items():
        if norm in norm_to_actual:
            mapping.setdefault(key, norm_to_actual[norm])
    return mapping


def import_csv(csv_path: str, service_account: str, project_id: Optional[str],
               default_password: Optional[str], dry_run: bool) -> None:
    if not os.path.exists(csv_path):
        print(f"CSV not found: {csv_path}")
        sys.exit(1)

    db = init_firebase(service_account, project_id)

    with open(csv_path, newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        header_map = map_headers(reader.fieldnames or [])

        missing = [k for k in REQUIRED_INTERNAL_KEYS if k not in header_map]
        if missing:
            print("Missing required CSV columns:", ", ".join(missing))
            print("Headers found:", ", ".join(reader.fieldnames or []))
            sys.exit(1)

        success = skipped = errors = 0
        for row in reader:
            try:
                name = (row.get(header_map["name"]) or "").strip()
                roll_number = to_roll_number(row.get(header_map["roll_number"]) or "")
                institute_outlook_id = to_email(row.get(header_map["institute_outlook_id"]) or "")
                wings_raw_header = header_map.get("wings")
                wings = parse_wings(row.get(wings_raw_header) or "") if wings_raw_header else []

                if not roll_number or not institute_outlook_id:
                    skipped += 1
                    continue

                uid, created = get_or_create_auth_user(institute_outlook_id, default_password)

                user_doc = {
                    "name": name,
                    "rollNumber": roll_number,
                    "instituteOutlookId": institute_outlook_id,
                    "uid": uid,
                    "unreadCount": 0,
                    "userType": "student",  # 🔥 changed here
                    "wings": wings,
                }

                upsert_user_doc(db, roll_number, user_doc, dry_run)
                success += 1
                print(f"OK [{'created' if created else 'reused'}]: {roll_number} -> {institute_outlook_id}")
            except Exception as e:
                errors += 1
                print(f"ERROR importing row: {e}")

    print(f"Import complete: success={success}, skipped={skipped}, errors={errors}")


def main():
    p = argparse.ArgumentParser(description="Import admins from CSV into Firebase Auth and Firestore (users)")
    p.add_argument("--csv", required=True, help="Path to the input CSV file")
    p.add_argument("--service-account", required=True, help="Path to Firebase service account JSON")
    p.add_argument("--project-id", required=False, help="GCP project ID (optional)")
    p.add_argument("--default-password", required=False, help="Default password for newly created Auth users")
    p.add_argument("--dry-run", action="store_true", help="Validate and print without writing")
    args = p.parse_args()

    if not os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"):
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = args.service_account

    import_csv(
        csv_path=args.csv,
        service_account=args.service_account,
        project_id=args.project_id,
        default_password=args.default_password,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
