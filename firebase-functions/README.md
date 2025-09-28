# Firebase Cloud Functions for ChatApp Notifications

This folder contains Firebase Cloud Functions for sending push notifications to users in the ChatApp application.

## Setup Instructions

Follow these steps to set up and deploy the Firebase Cloud Functions:

### Prerequisites

1. You need the Firebase CLI installed:
   ```
   npm install -g firebase-tools
   ```

2. You must have a Firebase project with:
   - Firebase Authentication
   - Firestore Database
   - Firebase Cloud Messaging

### Setup Steps

1. **Login to Firebase CLI**:
   ```
   firebase login
   ```

2. **Initialize Firebase Functions in this directory**:
   ```
   firebase init functions
   ```
   - Select your project when prompted
   - Choose JavaScript
   - Say yes to ESLint
   - Install dependencies when prompted

3. **Replace the index.js file**:
   Replace the generated index.js with the one provided in this folder.

4. **Deploy your functions**:
   ```
   firebase deploy --only functions
   ```

## How It Works

The notification system works as follows:

1. When a user sends a message in a group chat, the app creates a document in the `notifications` collection with:
   - Message details
   - Sender information
   - Group details
   - List of participants to notify
   - Information about mentions (@everyone or @user)

2. The Firebase Cloud Function `sendNotification` is triggered when a new document is created in the `notifications` collection.

3. The function:
   - Retrieves FCM tokens for all participants
   - Sends different notification types based on the message context:
     - Special notifications for @everyone mentions
     - Personalized notifications for users who were @mentioned
     - Regular notifications for other participants
   - Marks the notification as processed

## Setting up FCM in the Android App

1. **Add FCM Token to User Documents**:
   - When a user logs in, their FCM token is stored in their user document
   - This is done by the `ChatMessagingService` class

2. **Message Display**:
   - Messages with @mentions are highlighted in the chat
   - Both @everyone and @user mentions are visually distinct

## Troubleshooting

- **Notifications not working?**
  - Verify FCM tokens are being saved to user documents
  - Check Cloud Function logs in the Firebase Console
  - Ensure the app has notification permissions

- **Deployment issues?**
  - Make sure you're logged in to the correct Firebase account
  - Verify your project has billing enabled (required for Cloud Functions)

## Security Note

The Firebase Admin SDK (service account) is used only on the server side (Cloud Functions). The client app uses Firebase Cloud Messaging client libraries, which use a different authentication method that is safe for client-side use. 