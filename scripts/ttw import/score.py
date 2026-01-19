#!/usr/bin/env python3
"""
update_interview_scores.py

Reads an Excel file and updates Firestore documents in collection `ttwStudents`.
- Document ID is taken from Excel column C (3rd column).
- New interview score is taken from Excel column M (13th column).

Usage:
    python update_interview_scores.py --excel /path/to/Sc.xlsx --key /path/to/serviceAccountKey.json

Options:
    --excel         Path to Excel file (default: /mnt/data/Sc.xlsx)
    --key           Path to Firebase service account JSON key (required)
    --collection    Firestore collection name (default: ttwStudents)
    --field         Firestore field name to update (default: interviewScore)
    --strip-nondigits  If set, remove non-digit characters from roll IDs before checking (useful if Excel has "G19" but doc IDs are "19")
    --dry-run       If set, will only print what would be updated without touching Firestore.
"""

import argparse
import sys
import pandas as pd
import firebase_admin
from firebase_admin import credentials, firestore
import re

def parse_args():
    p = argparse.ArgumentParser(description="Update Firestore interview scores from Excel")
    p.add_argument('--excel', required=False, default='/mnt/data/Sc.xlsx', help='Path to Excel file')
    p.add_argument('--key', required=True, help='Path to Firebase service account JSON key')
    p.add_argument('--collection', required=False, default='ttwStudents', help='Firestore collection name')
    p.add_argument('--field', required=False, default='interviewScore', help='Firestore field name to update')
    p.add_argument('--strip-nondigits', action='store_true', help='Strip non-digit characters from roll IDs')
    p.add_argument('--dry-run', action='store_true', help='Do not perform updates; only show what would be changed')
    return p.parse_args()

def normalize_roll(value, strip_nondigits=False):
    if pd.isna(value):
        return None
    s = str(value).strip()
    if strip_nondigits:
        s = re.sub(r'\D+', '', s)  # remove non-digits
    return s

def read_excel_rolls_scores(path):
    # Read with pandas. We'll take column C (index 2) for roll and column M (index 12) for score.
    df = pd.read_excel(path, header=0, dtype=object)
    if df.shape[1] < 13:
        print(f"Warning: the Excel file has only {df.shape[1]} columns. Expected at least 13 (column M).", file=sys.stderr)
    rolls = df.iloc[:, 2] if df.shape[1] >= 3 else pd.Series([], dtype=object)
    scores = df.iloc[:, 12] if df.shape[1] >= 13 else pd.Series([], dtype=object)
    return list(zip(rolls.tolist(), scores.tolist()))

def main():
    args = parse_args()

    # Read excel
    try:
        pairs = read_excel_rolls_scores(args.excel)
    except Exception as e:
        print("Failed to read Excel file:", e, file=sys.stderr)
        sys.exit(1)

    # Initialize Firebase
    try:
        cred = credentials.Certificate(args.key)
        firebase_admin.initialize_app(cred)
        db = firestore.client()
    except Exception as e:
        print("Failed to initialize Firebase Admin SDK. Check --key path and permissions.", e, file=sys.stderr)
        sys.exit(1)

    changed_ids = []
    total = 0
    for raw_roll, raw_score in pairs:
        roll = normalize_roll(raw_roll, strip_nondigits=args.strip_nondigits)
        if not roll or str(roll).strip() == '' or pd.isna(roll):
            continue
        # Normalize score
        if pd.isna(raw_score):
            continue
        try:
            # Try to convert to number (int if possible)
            if isinstance(raw_score, str):
                score_val = raw_score.strip()
                if score_val == '':
                    continue
                score = float(score_val) if '.' in score_val else int(score_val)
            elif isinstance(raw_score, (int, float)):
                score = int(raw_score) if float(raw_score).is_integer() else float(raw_score)
            else:
                score = raw_score
        except Exception:
            print(f"Skipping roll {roll} because score '{raw_score}' is not parseable.", file=sys.stderr)
            continue

        doc_ref = db.collection(args.collection).document(str(roll))
        try:
            doc = doc_ref.get()
        except Exception as e:
            print(f"Error fetching document {roll}: {e}", file=sys.stderr)
            continue

        if not doc.exists:
            continue

        update_data = { args.field: score }
        total += 1
        if args.dry_run:
            print(f"[DRY RUN] Would update document {roll} -> {args.field} = {score}")
            changed_ids.append(str(roll))
        else:
            try:
                doc_ref.update(update_data)
                print(f"Updated document {roll} -> {args.field} = {score}")
                changed_ids.append(str(roll))
            except Exception as e:
                print(f"Failed to update document {roll}: {e}", file=sys.stderr)

    # Summary
    print("\nDocuments updated:")
    for rid in changed_ids:
        print(rid)
    print(f"Total documents updated: {len(changed_ids)} (processed {total} candidate rows)")

if __name__ == '__main__':
    main()
