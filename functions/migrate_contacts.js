const admin = require('firebase-admin');
const serviceAccount = require('../serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});
const db = admin.firestore();

function parseFirestoreValue(value) {
  if (value.stringValue !== undefined) return value.stringValue;
  if (value.integerValue !== undefined) return parseInt(value.integerValue, 10);
  if (value.booleanValue !== undefined) return value.booleanValue;
  if (value.arrayValue !== undefined) {
    return (value.arrayValue.values || []).map(parseFirestoreValue);
  }
  if (value.mapValue !== undefined) {
    const obj = {};
    for (const [k, v] of Object.entries(value.mapValue.fields || {})) {
      obj[k] = parseFirestoreValue(v);
    }
    return obj;
  }
  return null;
}

async function run() {
  console.log("Fetching data from chatapp-24fae for nss_updates...");
  const res = await fetch('https://firestore.googleapis.com/v1/projects/chatapp-24fae/databases/(default)/documents/nss_updates');
  const data = await res.json();
  
  if (!data.documents) {
    console.log("No documents found or error in fetch:", data);
    return;
  }
  
  for (const doc of data.documents) {
    const docId = doc.name.split('/').pop();
    const parsedData = {};
    for (const [k, v] of Object.entries(doc.fields)) {
      parsedData[k] = parseFirestoreValue(v);
    }
    await db.collection('nss_updates').doc(docId).set(parsedData);
    console.log(`Migrated doc: ${docId}`);
  }
  console.log("Migration complete!");
}

run().catch(console.error);
