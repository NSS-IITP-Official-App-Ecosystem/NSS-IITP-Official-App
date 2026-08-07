const fft = require('firebase-functions-test')({
    projectId: 'demo-test' // Using a demo project completely isolates this test data from your main emulator data!
});
const admin = require('firebase-admin');

// Tell admin to use the local emulator
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';

// Initialize the app for the demo-test project so admin.firestore() works
if (!admin.apps.length) {
    admin.initializeApp({ projectId: 'demo-test' });
}

const db = admin.firestore();

module.exports = { fft, admin, db };
