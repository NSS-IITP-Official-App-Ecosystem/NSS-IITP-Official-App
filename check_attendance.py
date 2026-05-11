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
                # Fallback to Application Default Credentials
                firebase_admin.initialize_app()
        except Exception as e:
            print(f"Failed to initialize Firebase: {e}")
            print("Please provide a path to a service account JSON file using --cred_path")
            sys.exit(1)

def check_attendance(target_date_str, cred_path=None):
    from datetime import datetime
    initialize_firebase(cred_path)
    try:
        db = firestore.client()
    except Exception as e:
        print("\n❌ Error: Google Application Default Credentials not found.")
        print("To run this script, you must provide a Firebase Service Account key.")
        print("Usage: python check_attendance.py \"11 Apr 2026\" --cred_path \"./service-account.json\"")
        print("\nYou can generate this JSON file from:")
        print("Firebase Console -> Project Settings -> Service Accounts -> Generate new private key")
        return
    
    # Try parsing target date
    try:
        if '-' in target_date_str:
            target_date = datetime.strptime(target_date_str, '%Y-%m-%d').date()
        else:
            target_date = datetime.strptime(target_date_str, '%d %b %Y').date()
    except ValueError:
        print("Invalid date format. Please use 'DD MMM YYYY' (e.g. '10 Apr 2026') or 'YYYY-MM-DD'")
        return

    print(f"Scanning all events to find attendance marked exactly on: {target_date.strftime('%d %b %Y')}")
    
    try:
        events = db.collection('NSS_Events_Attendence').stream()
        total_found = 0
        report_metadata = []
        
        for doc in events:
            data = doc.to_dict()
            attendees = data.get('attendees', [])
            event_name = data.get('description', 'Unknown Event')
            
            marked_today = []
            
            for att in attendees:
                stamp = att.get('scanTimestamp')
                if stamp:
                    from datetime import timezone, timedelta
                    ist = timezone(timedelta(hours=5, minutes=30)) # Indian Standard Time
                    
                    if hasattr(stamp, 'astimezone'):
                        # Convert UTC timestamp to IST
                        att_date = stamp.astimezone(ist).date()
                    else:
                        from dateutil import parser
                        att_date = parser.parse(str(stamp)).astimezone(ist).date()
                        
                    if att_date == target_date:
                        marked_today.append(att)
            
            if marked_today:
                import textwrap
                all_rolls = [a.get('rollNumber', 'Unknown') for a in marked_today]
                roll_string = ', '.join(all_rolls)
                
                print(f"\n==================================================")
                print(f"-> Event: {doc.id}")
                print(f"-> Attendance Count: {len(marked_today)}")
                print(f"--------------------------------------------------")
                
                wrapped_text = textwrap.fill(roll_string, width=80, initial_indent="   ", subsequent_indent="   ")
                print(wrapped_text)
                
                total_found += len(marked_today)
                report_metadata.append(f"Event: {doc.id}\nCount: {len(marked_today)}\nRoll Numbers:\n{wrapped_text}\n\n")
                
        print(f"\n==================================================")
        if total_found == 0:
            print(f"[X] No attendance marks were recorded exactly on {target_date.strftime('%d %b %Y')}.")
        else:
            print(f"[*] Total attendance marks recorded on {target_date.strftime('%d %b %Y')}: {total_found}")
            
            # Save nicely to a file
            filename = f"attendance_{target_date.strftime('%d_%b_%Y')}.txt"
            with open(filename, "w", encoding="utf-8") as f:
                f.write(f"ATTENDANCE REPORT - {target_date.strftime('%d %b %Y')}\n")
                f.write(f"Total Combined Attendance: {total_found}\n")
                f.write("="*80 + "\n\n")
                f.writelines(report_metadata)
            print(f"[file] Full human-readable report saved to: {filename}")
            
    except Exception as e:
        print(f"Error querying Firestore: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Check if attendance was marked on a specific day based on scanTimestamp.")
    parser.add_argument("date", type=str, help="The date to check (e.g., '10 Apr 2026' or '2026-04-10')")
    parser.add_argument("--cred_path", type=str, default=None, help="Path to your Firebase service account JSON file. If not provided, defaults to checking for Application Default Credentials.")
    
    args = parser.parse_args()
    check_attendance(args.date, args.cred_path)
