# FCM Serverless Delivery Service 🚀

This folder (`fcm-backend`) contains a tiny Node.js microservice designed exclusively to run on Vercel's free tier. It securely accepts push notification requests from your Android App and sends them to Google FCM using Firebase Admin credentials hidden in environment variables.

## How to deploy to Vercel (100% Free, NO CLI required)

Follow these simple steps:

### Context
Your Android app will soon send POST requests to the Vercel URL you create. You need the **Firebase Service Account JSON** files for both your test (`chatapp-24fae`) and prod (`nssiitp-app`) projects. 

*If you don't have them: Go to Firebase Console > Project Settings > Service Accounts > "Generate new private key".*

### Step 1: Push to GitHub
1. Upload this specific folder (`fcm-backend`) to a new Git repository on your GitHub account. 

### Step 2: Deploy on Vercel
1. Go to [Vercel.com](https://vercel.com/) and create a free account with your GitHub.
2. Click **"Add New Project"** and select the GitHub repository you just uploaded.
3. Don't click Deploy yet! Scroll down to the **Environment Variables** section.

### Step 3: Add your Secure Keys
Add two environment variables here (paste the ENTIRE contents of the service account JSON files as the values):

* **Key:** `FCM_TEST_KEY` 
* **Value:** *(Paste the entire test service account JSON here. Make sure there are no extra line breaks at the very beginning or end)*

* **Key:** `FCM_PROD_KEY`
* **Value:** *(Paste the entire prod service account JSON here)*

4. Click **Deploy**. In about 15 seconds, Vercel will give you a live URL (e.g., `https://nss-fcm-backend.vercel.app`).

### Step 4: Hook it up to Android
Save the URL Vercel gave you! We will plug this URL into our `FcmSender.kt` in the Android codebase so that when an admin creates an event, the app fires a request straight to Vercel.
