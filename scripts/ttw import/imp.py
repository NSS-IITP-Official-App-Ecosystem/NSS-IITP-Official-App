#!/usr/bin/env python3
"""
Upload Excel -> Firestore (preserves subject order, normalizes names)

Behaviors:
 - Uses exact column names from your sheet.
 - Normalizes subject strings via a mapping (common variants -> canonical).
 - Keeps provided preference order. If a single preference is missing/unknown, it is
   replaced by a default (from DEFAULT_PREFS) at that position.
 - If all three preferences are empty, uses DEFAULT_PREFS.
 - interviewScore random 51..100
 - Uses Roll number (uppercased) as document ID
 - Academic group extracts numeric part (G19 -> 19)
"""

import argparse, os, sys, re, random
import pandas as pd
import firebase_admin
from firebase_admin import credentials, firestore

# Exact headers in your Excel
NAME_COL = "Name"
ROLL_COL = "Roll number"
ACAD_COL = "Academic group"
SUB1_COL = "Subject Preference  [1st preference ]"
SUB2_COL = "Subject Preference  [2nd preference ]"
SUB3_COL = "Subject Preference  [3rd preference ]"

# Canonical allowed subjects
ALLOWED_SUBJECTS = {
    "Mathematics","Physics","Chemistry","Computer","Biology","English",
    "History","Geography","Social Studies","Economics","Accounting",
    "Business Studies","Hindi","Sanskrit","Political Science"
}
DEFAULT_PREFS = ["Mathematics", "Physics", "Chemistry"]

# Map common variants -> canonical
SUBJECT_MAP = {
    "maths": "Mathematics",
    "mathematics": "Mathematics",
    "physics": "Physics",
    "chem": "Chemistry",
    "chemistry": "Chemistry",
    "computer": "Computer",
    "comp": "Computer",
    "biology": "Biology",
    "bio": "Biology",
    "english": "English",
    "history": "History",
    "geography": "Geography",
    "social studies": "Social Studies",
    "social science": "Social Studies",
    "economics": "Economics",
    "accounting": "Accounting",
    "business studies": "Business Studies",
    "business": "Business Studies",
    "hindi": "Hindi",
    "sanskrit": "Sanskrit",
    "political science": "Political Science",
    # add more variants if needed
}

def clean_text(v):
    if pd.isna(v):
        return ""
    return str(v).strip()

def canonical_subject(raw):
    """Return canonical subject (from ALLOWED_SUBJECTS) or '' if unknown."""
    if not raw:
        return ""
    key = clean_text(raw).lower()
    # direct map
    if key in SUBJECT_MAP:
        return SUBJECT_MAP[key]
    # try fuzzy: remove dots/spaces/hyphens
    key_simple = re.sub(r"[\s\.\-]+", " ", key).strip()
    if key_simple in SUBJECT_MAP:
        return SUBJECT_MAP[key_simple]
    # title-case fallback if it matches allowed exactly
    candidate = key.title()
    if candidate in ALLOWED_SUBJECTS:
        return candidate
    return ""

def extract_group_number(value):
    if not value:
        return ""
    m = re.search(r"\d+", str(value))
    return m.group(0) if m else ""

def build_preferences(row):
    # Read raw values (if column missing, get("") handles)
    raw1 = clean_text(row.get(SUB1_COL, "")) if SUB1_COL in row else ""
    raw2 = clean_text(row.get(SUB2_COL, "")) if SUB2_COL in row else ""
    raw3 = clean_text(row.get(SUB3_COL, "")) if SUB3_COL in row else ""

    # If all three empty -> defaults
    if not (raw1 or raw2 or raw3):
        return DEFAULT_PREFS.copy()

    # Map each provided pref to canonical or '' if unknown
    c1 = canonical_subject(raw1) if raw1 else ""
    c2 = canonical_subject(raw2) if raw2 else ""
    c3 = canonical_subject(raw3) if raw3 else ""

    prefs = [c1, c2, c3]

    # For any empty/unknown at position i, fill from DEFAULT_PREFS by position
    for i in range(3):
        if not prefs[i]:
            prefs[i] = DEFAULT_PREFS[i]

    return prefs

def build_doc(row):
    name = clean_text(row.get(NAME_COL, ""))
    roll_raw = clean_text(row.get(ROLL_COL, ""))
    roll = roll_raw.upper() if roll_raw else ""
    acad_raw = clean_text(row.get(ACAD_COL, ""))
    acad_group = extract_group_number(acad_raw)
    prefs = build_preferences(row)
    interview_score = random.randint(51, 100)
    doc = {
        "name": name,
        "rollNumber": roll,
        "academicGroup": acad_group,
        "subjectPreference1": prefs[0],
        "subjectPreference2": prefs[1],
        "subjectPreference3": prefs[2],
        "interviewScore": interview_score,
    }
    return roll, doc

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--collection", default="ttwStudents")
    parser.add_argument("--project", default=None)
    parser.add_argument("--preview-only", action="store_true")
    args = parser.parse_args()

    if not os.path.isfile(args.excel):
        sys.exit(f"Excel file not found: {args.excel}")
    if not os.path.isfile(args.key):
        sys.exit(f"Service account key not found: {args.key}")

    df = pd.read_excel(args.excel, engine="openpyxl", dtype=str)
    df.columns = [c.strip() for c in df.columns]

    print(f"Loaded {len(df)} rows. Columns:")
    for c in df.columns:
        print(" -", c)

    # show preview of relevant cols (first 6 rows)
    cols_to_show = [c for c in [NAME_COL, ROLL_COL, ACAD_COL, SUB1_COL, SUB2_COL, SUB3_COL] if c in df.columns]
    print("\nPreview (first 6 rows):")
    print(df[cols_to_show].head(6).to_string(index=True))

    if args.preview_only:
        print("Preview-only mode; exiting.")
        return

    # init firebase
    cred = credentials.Certificate(args.key)
    if args.project:
        firebase_admin.initialize_app(cred, {"projectId": args.project})
    else:
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    col_ref = db.collection(args.collection)

    total = len(df)
    success = 0
    failed = 0
    for idx, row_ser in df.iterrows():
        try:
            row = row_ser.to_dict()
            roll, doc = build_doc(row)
            if not roll:
                print(f"Skipping row {idx}: missing roll")
                failed += 1
                continue
            col_ref.document(roll).set(doc)
            success += 1
            print(f"[{success+failed}/{total}] Uploaded {roll} - {doc['name']}")
        except Exception as e:
            failed += 1
            print(f"[{success+failed}/{total}] Error row {idx}: {e}")

    print(f"Done. Uploaded {success}/{total}, failed {failed}")

if __name__ == "__main__":
    main()
