import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
from firebase_admin import auth
import json
import re

# Path to your Firebase service account credentials file
# Using the specific path provided by the user
CREDENTIALS_PATH = r"C:\Users\HP\AndroidStudioProjects\ChatApp\chatapp-24fae-firebase-adminsdk-fbsvc-ff505f9f48.json"

# Initialize Firebase Admin SDK
try:
    cred = credentials.Certificate(CREDENTIALS_PATH)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Successfully connected to Firebase!")
except Exception as e:
    print(f"Error initializing Firebase: {e}")
    exit(1)

def clean_name(name):
    """Extract only alphabetic characters from name"""
    if not name:
        return "User"
    return re.sub(r'[^a-zA-Z]', '', name)

def generate_password(name, contact_number):
    """Generate password based on pattern: first 4 letters of name + @ + first 4 digits of contact number"""
    # Clean and prepare the name part
    clean_name_str = clean_name(name)
    name_part = clean_name_str[:4].capitalize()
    if len(name_part) < 4:
        name_part = name_part.ljust(4, 'x')  # Pad with 'x' if name is too short
    
    # Clean and prepare the contact number part
    contact_str = str(contact_number) if contact_number else "0000"
    contact_str = re.sub(r'[^0-9]', '', contact_str)  # Keep only digits
    contact_part = contact_str[:4]
    if len(contact_part) < 4:
        contact_part = contact_part.ljust(4, '0')  # Pad with '0' if contact number is too short
    
    return f"{name_part}@{contact_part}"

def process_users():
    """Fetch all users from Firestore and create Auth accounts"""
    # Get all users from the 'users' collection
    users_ref = db.collection('users')
    users = users_ref.get()
    
    total_users = len(users)
    success_count = 0
    error_count = 0
    existing_count = 0
    
    print(f"Found {total_users} users in Firestore.")
    print("Starting account creation process...")
    print("-" * 50)
    
    # Process each user
    for user in users:
        user_data = user.to_dict()
        user_id = user.id  # Document ID
        
        print(f"Processing user {user_id}: {user_data}")
        
        try:
            # Extract user information using the correct field names from Firestore
            email = user_data.get('email')
            name = user_data.get('name')  # Changed from fullName to name
            contact_number = user_data.get('contactNumber')  # Changed from mobile to contactNumber
            
            print(f"  Email: {email}")
            print(f"  Name: {name}")
            print(f"  Contact: {contact_number}")
            
            # Skip if no email
            if not email:
                print(f"Skipping user {user_id}: No email found")
                error_count += 1
                continue
                
            # Generate password
            password = generate_password(name, contact_number)
            print(f"  Generated password: {password}")
            
            # Check if user already exists in Auth
            try:
                auth.get_user_by_email(email)
                print(f"User already exists: {email}")
                existing_count += 1
                continue
            except auth.UserNotFoundError:
                # User doesn't exist, so create new account
                try:
                    auth_user = auth.create_user(
                        email=email,
                        password=password,
                        display_name=name  # Changed from full_name to name
                    )
                    
                    # Update Firestore document with UID
                    user_ref = db.collection('users').document(user_id)
                    user_ref.update({
                        'uid': auth_user.uid,
                        'tempPassword': password  # Store the temp password for reference
                    })
                    
                    print(f"Created account for {email} (Password: {password})")
                    success_count += 1
                    
                except auth.EmailAlreadyExistsError:
                    print(f"Email already exists: {email}")
                    existing_count += 1
                    
                except Exception as create_error:
                    print(f"Error creating user {email}: {create_error}")
                    error_count += 1
        
        except Exception as e:
            print(f"Error processing user {user.id}: {e}")
            error_count += 1
    
    print("-" * 50)
    print(f"Summary:")
    print(f"Total users processed: {total_users}")
    print(f"Accounts created: {success_count}")
    print(f"Already existing: {existing_count}")
    print(f"Errors/skipped: {error_count}")

if __name__ == "__main__":
    print("Firebase User Authentication Creator")
    print("=" * 50)
    
    try:
        process_users()
        print("\nProcess completed!")
    except Exception as e:
        print(f"\nAn error occurred during processing: {e}")
    
    print("\nDon't forget to inform users about their temporary passwords.")
    print("Recommend users to change their passwords after first login.") 