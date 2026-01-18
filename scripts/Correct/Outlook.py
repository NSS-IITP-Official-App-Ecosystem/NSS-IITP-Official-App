import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from firebase_admin import auth
import argparse
import sys

def initialize_firebase(service_account_path):
    print(f"Using credentials file: {service_account_path}")
    try:
        cred = credentials.Certificate(service_account_path)
        firebase_admin.initialize_app(cred)
        print("Successfully connected to Firebase!")
        return firestore.client()
    except Exception as e:
        print(f"Error initializing Firebase: {e}")
        exit(1)

def update_student_email(roll_number, new_email, service_account_path):
    db = initialize_firebase(service_account_path)
    
    print(f"\n--- Updating Email for {roll_number} ---")
    
    # 1. FIND USER IN FIRESTORE
    user_ref = db.collection('users').document(roll_number)
    user_doc = user_ref.get()
    
    if not user_doc.exists:
        print(f"❌ Error: User with roll number '{roll_number}' not found in Firestore.")
        return

    user_data = user_doc.to_dict()
    auth_uid = user_data.get('uid')
    current_firestore_email = user_data.get('instituteOutlookId')
    
    if not auth_uid:
        print(f"❌ Error: No 'uid' found in Firestore for this user. Cannot update Auth.")
        return

    print(f"Found User:")
    print(f"  Name: {user_data.get('name')}")
    print(f"  Current Firestore Email: {current_firestore_email}")
    print(f"  Auth UID: {auth_uid}")

    # 2. UPDATE FIREBASE AUTHENTICATION
    try:
        print(f"\n> Updating Firebase Authentication to '{new_email}'...")
        auth.update_user(
            auth_uid,
            email=new_email,
            email_verified=True # Optional: Mark as verified since admin is changing it
        )
        print("  ✅ Auth email updated successfully.")
        
    except auth.EmailAlreadyExistsError:
        print(f"❌ Error: The email '{new_email}' is already being used by another account.")
        return
    except Exception as e:
        print(f"❌ Error updating Auth: {e}")
        return

    # 3. UPDATE FIRESTORE
    try:
        print(f"> Updating Firestore 'instituteOutlookId' to '{new_email}'...")
        user_ref.update({
            'instituteOutlookId': new_email,
            'email': new_email # Update legacy email field if it exists
        })
        print("  ✅ Firestore email updated successfully.")
        
    except Exception as e:
        print(f"❌ Error updating Firestore: {e}")
        print("WARNING: Auth was updated but Firestore failed! Manual check required.")
        return

    print("\n✨ SUCCESS! Email updated in both systems.")
    print(f"User {roll_number} can now login with:")
    print(f"  Email: {new_email} (Internal check)")
    print(f"  Pass:  [Their existing password]")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Update user email in Firebase Auth and Firestore.')
    parser.add_argument('--roll-number', required=True, help='Student Roll Number (Document ID)')
    parser.add_argument('--new-email', required=True, help='Correct Email Address')
    parser.add_argument('--service-account', required=True, help='Path to Firebase Service Account JSON key')

    args = parser.parse_args()
    
    update_student_email(args.roll_number, args.new_email, args.service_account)
