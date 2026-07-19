const admin = require('firebase-admin');
const serviceAccount = require('../serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});
const db = admin.firestore();

async function createTestUpdate() {
  await db.collection('nss_updates').doc('welcome_post').set({
    title: "Welcome to the new App!",
    content: "The feed is now clean and working perfectly.",
    authorName: "System Admin",
    postType: "text",
    timestamp: Date.now(),
    updateType: 1
  });
  console.log("Created test update!");
}

createTestUpdate().catch(console.error);
