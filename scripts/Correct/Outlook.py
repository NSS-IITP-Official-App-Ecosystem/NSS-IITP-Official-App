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
    except auth.UserNotFoundError:
        print(f"  ⚠️ User not found in Auth. Creating a new Auth record to preserve data...")
        try:
            # Preserve the existing UID so any other collections relying on it won't break
            auth.create_user(
                uid=auth_uid,
                email=new_email,
                email_verified=True,
                password=roll_number # Default password so they can log in
            )
            print(f"  ✅ Created new Auth record with existing UID: {auth_uid}.")
        except auth.EmailAlreadyExistsError:
            try:
                conflicting_user = auth.get_user_by_email(new_email)
                print(f"❌ Error: Cannot recreate user. The email '{new_email}' is registered to a DIFFERENT UID.")
                print(f"  > This Firestore Document's UID: {auth_uid}")
                print(f"  > The Existing Auth Record's UID: {conflicting_user.uid}")
                print(f"\n💡 How to resolve this:")
                print(f"   Option A: If {conflicting_user.uid} is the correct new UID, update the Firestore 'uid' field to {conflicting_user.uid}.")
                print(f"   Option B: If {auth_uid} is the correct UID (and you want to keep old data), delete the user {conflicting_user.uid} from Firebase Auth manually and run this script again.")
            except Exception as fetch_error:
                print(f"❌ Error: The email already exists, but could not fetch details: {fetch_error}")
            return
        except Exception as create_error:
            print(f"❌ Error creating new Auth record: {create_error}")
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
    print(f"  Pass:  [Their existing password, or '{roll_number}' if newly created]")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Update user email in Firebase Auth and Firestore.')
    parser.add_argument('--roll-number', required=True, help='Student Roll Number (Document ID)')
    parser.add_argument('--new-email', required=True, help='Correct Email Address')
    parser.add_argument('--service-account', required=True, help='Path to Firebase Service Account JSON key')

    args = parser.parse_args()
    
    update_student_email(args.roll_number, args.new_email, args.service_account)
