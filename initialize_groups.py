import firebase_admin
from firebase_admin import credentials, firestore
import argparse
import sys
import os
import re

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

def get_doc_id(preset_name):
    """Generate a deterministic document ID from preset name, matching Kotlin logic."""
    cleaned = preset_name.strip()
    cleaned = re.sub(r'\s+', '_', cleaned)
    cleaned = re.sub(r'[^a-zA-Z0-9_]', '', cleaned)
    return cleaned

def initialize_groups(cred_path=None, admin_id="SystemAdmin"):
    initialize_firebase(cred_path)
    try:
        db = firestore.client()
    except Exception as e:
        print("\n❌ Error: Google Application Default Credentials not found.")
        print("To run this script, you must provide a Firebase Service Account key.")
        print("Usage: python initialize_groups.py --cred_path \"./service-account.json\"")
        return

    print("Fetching schedules from 'generatedSchedules' collection...")
    try:
        schedules = db.collection('generatedSchedules').stream()
        
        batch = db.batch()
        batch_count = 0
        total_synced = 0
        
        for doc in schedules:
            data = doc.to_dict()
            preset_name = data.get('name', doc.id)
            
            if preset_name == 'Unknown':
                continue
                
            # Extract volunteers from optimized assignments
            assignments = data.get('optimizedAssignments', [])
            volunteers = set()
            for assign in assignments:
                roll_no = assign.get('volunteerRollNo')
                if roll_no:
                    volunteers.add(roll_no)
                    
            if not volunteers:
                print(f"Skipping {preset_name} (No volunteers assigned)")
                continue
                
            print(f"Processing group: {preset_name} with {len(volunteers)} volunteers")
            
            doc_id = get_doc_id(preset_name)
            group_ref = db.collection('groups').document(doc_id)
            
            group_doc = group_ref.get()
            
            if group_doc.exists:
                # Update existing group
                group_data = group_doc.to_dict()
                existing_admins = group_data.get('admins', [])
                existing_creator = group_data.get('createdBy', '')
                
                final_participants = set(volunteers)
                final_participants.update(existing_admins)
                if existing_creator:
                    final_participants.add(existing_creator)
                final_participants.add(admin_id)
                
                final_admins = set(existing_admins)
                final_admins.add(admin_id)
                
                batch.update(group_ref, {
                    'participants': list(final_participants),
                    'admins': list(final_admins)
                })
                print(f" -> Prepared UPDATE for '{preset_name}'")
            else:
                # Create new group
                final_participants = list(volunteers)
                final_participants.append(admin_id)
                
                new_group = {
                    'id': doc_id,
                    'name': preset_name,
                    'description': f"Auto-generated group for {preset_name} classes",
                    'participants': final_participants,
                    'admins': [admin_id],
                    'createdBy': admin_id,
                    'createdAt': firestore.SERVER_TIMESTAMP,
                    'isPublic': False,
                    'joinRequests': [],
                    'messagingPermissions': {p: True for p in final_participants}
                }
                
                batch.set(group_ref, new_group)
                print(f" -> Prepared CREATE for '{preset_name}'")
                
            batch_count += 1
            total_synced += 1
            
            # Firestore batches are limited to 500 operations
            if batch_count >= 450:
                batch.commit()
                batch = db.batch()
                batch_count = 0
                
        # Commit any remaining operations
        if batch_count > 0:
            batch.commit()
            
        print(f"\n✅ Successfully synced {total_synced} class groups to the database!")
        
    except Exception as e:
        print(f"❌ Error querying Firestore: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Initialize class chat groups from generated schedules.")
    parser.add_argument("--cred_path", type=str, default=None, help="Path to your Firebase service account JSON file.")
    parser.add_argument("--admin_id", type=str, default="SystemAdmin", help="The Admin Roll Number to add to all groups.")
    
    args = parser.parse_args()
    initialize_groups(args.cred_path, args.admin_id)
