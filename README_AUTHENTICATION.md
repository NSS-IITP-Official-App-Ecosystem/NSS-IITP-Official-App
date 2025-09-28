# Firebase Authentication Creator Script

This script automatically creates Firebase Authentication accounts for users in your Firestore database.

## How it Works

1. The script connects to your Firebase project using admin credentials
2. It reads all documents from the "users" collection in Firestore
3. For each user, it creates a Firebase Authentication account with:
   - Email: The user's email from Firestore
   - Password: First 4 letters of the user's name + @ + First 4 digits of mobile number
   - Example: For "Aditya Gupta" with mobile "9876543210", password is "Adit@9876"
4. It updates the Firestore document with the Authentication UID and temporary password

## Prerequisites

1. Python 3.6 or higher
2. Firebase Admin SDK (`pip install firebase-admin`)
3. Firebase service account credentials JSON file

## Setup Instructions

1. **Create Firebase Service Account Key**:
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Select your project
   - Go to Project Settings > Service Accounts
   - Click "Generate New Private Key"
   - Save the downloaded JSON file as `firebase-credentials.json` in the same directory as the script

2. **Install Required Python Packages**:
   ```
   pip install firebase-admin
   ```

3. **Check User Data Structure**:
   Make sure your Firestore "users" collection documents have these fields:
   - `email`: User's email address
   - `fullName`: User's full name
   - `mobile`: User's mobile number

## Running the Script

1. Place the `create_firebase_accounts.py` script and `firebase-credentials.json` in the same directory
2. Run the script:
   ```
   python create_firebase_accounts.py
   ```
3. The script will show progress and report summary statistics

## Output

The script outputs:
- Created accounts with their generated passwords
- Users that already exist
- Any errors encountered
- Summary statistics

## Important Notes

1. **Security**: The script stores temporary passwords in Firestore. Consider removing them after all users have logged in.
2. **Notify Users**: Make sure to inform users about their temporary passwords through a secure channel.
3. **Password Reset**: Encourage users to change their passwords after first login.
4. **Error Handling**: The script will attempt to create accounts for all users, even if some fail.

## Troubleshooting

- **Authentication Issues**: Make sure your Firebase service account has the correct permissions (Firestore and Authentication Admin)
- **Missing Fields**: If users are being skipped, check that they have the required fields (email, fullName, mobile)
- **Password Policy**: Firebase requires passwords to be at least 6 characters. The script handles this, but verify if errors occur.

## Customization

- Modify the `generate_password()` function if you need a different password pattern
- Add additional fields to be stored in Authentication accounts by modifying the `create_user()` call 