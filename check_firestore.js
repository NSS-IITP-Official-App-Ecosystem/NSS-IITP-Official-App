// Run this with: node check_firestore.js
// First: npm install firebase-admin (one time)
// Then paste your service account JSON content as the credential

const admin = require('firebase-admin');

// Paste your test project service account JSON here temporarily
const serviceAccount = require('./google-services-test.json'); // adjust path

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function check() {
  const snap = await db.collection('app_notifications').orderBy('timestamp', 'desc').limit(10).get();
  console.log(`Total docs: ${snap.size}`);
  snap.forEach(doc => {
    const d = doc.data();
    console.log({
      id: doc.id,
      title: d.title,
      targetType: d.targetType,
      targetTopics: d.targetTopics,
      timestamp: d.timestamp?.toDate()
    });
  });
  process.exit(0);
}

check().catch(e => { console.error(e); process.exit(1); });
