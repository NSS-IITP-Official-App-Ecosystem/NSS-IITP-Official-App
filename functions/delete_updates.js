const admin = require('firebase-admin');
const serviceAccount = require('../serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});
const db = admin.firestore();

async function clearUpdates() {
  console.log("Fetching documents in 'nss_updates' collection...");
  const snapshot = await db.collection('nss_updates').get();
  
  if (snapshot.empty) {
    console.log("No updates found. Collection is already empty.");
    return;
  }
  
  console.log(`Found ${snapshot.size} updates. Deleting...`);
  
  const batch = db.batch();
  snapshot.docs.forEach((doc) => {
    batch.delete(doc.ref);
  });
  
  await batch.commit();
  console.log("Successfully cleared all updates from the home screen!");
}

clearUpdates().catch(console.error);
