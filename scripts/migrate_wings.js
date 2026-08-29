const admin = require('firebase-admin');
const readline = require('readline');
const path = require('path');

// --- Configuration & Flags ---
const USE_EMULATOR  = process.argv.includes('--emulator');
const DRY_RUN       = !process.argv.includes('--live');
const SKIP_CONFIRM  = process.argv.includes('--yes'); // Allow bypassing prompt

const SERVICE_ACCOUNT_PATH = path.join(__dirname, '..', 'serviceAccountKey.json');

// --- Helper for Interactive Prompt ---
function confirm(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise(resolve => {
    rl.question(question, answer => {
      rl.close();
      resolve(answer.trim().toLowerCase());
    });
  });
}

// --- Initialize Firebase ---
function initFirebase() {
  if (USE_EMULATOR) {
    process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';
    admin.initializeApp({ projectId: 'nssiitp-app' });
    return;
  }
  
  let serviceAccount;
  try {
    serviceAccount = require(SERVICE_ACCOUNT_PATH);
  } catch (err) {
    console.error(`❌ Could not load service account key from: ${SERVICE_ACCOUNT_PATH}`);
    process.exit(1);
  }
  const { cert } = require('firebase-admin/app');
  admin.initializeApp({ credential: cert(serviceAccount) });
}

const { getFirestore } = require('firebase-admin/firestore');

// --- Main Migration Logic ---
async function migrateWings() {
  initFirebase();
  const db = getFirestore();

  console.log('\n════════════════════════════════════════════════════════════');
  console.log('  NSS IITP — Wing Migration (Chetna/Prayatna -> Prerna)');
  console.log('════════════════════════════════════════════════════════════');
  console.log(`  Mode:    ${DRY_RUN ? '🟢 DRY RUN (Read Only)' : '🔴 LIVE (will modify data!)'}`);
  console.log(`  Target:  ${USE_EMULATOR ? '🧪 Firestore Emulator' : '☁️  Production Firestore'}`);
  console.log('────────────────────────────────────────────────────────────\n');

  console.log('📂 Scanning all users...');
  
  const usersSnap = await db.collection('users').get();
  
  let totalUsers = 0;
  let usersToUpdate = [];
  
  usersSnap.forEach(doc => {
    totalUsers++;
    const data = doc.data();
    const originalWings = data.wings;
    
    // Safety check: skip if wings is missing or not an array
    if (!Array.isArray(originalWings)) return;

    let newWingsSet = new Set();
    let hasCommaOrLegacy = false;

    originalWings.forEach(wingString => {
      if (typeof wingString !== 'string') return;
      
      // Check if it's mashed or legacy
      if (wingString.includes(',')) hasCommaOrLegacy = true;
      if (wingString.toLowerCase().includes('chetna') || wingString.toLowerCase().includes('prayatna')) {
        hasCommaOrLegacy = true;
      }

      // Split logic
      let parts = wingString.split(',');
      parts.forEach(part => {
        let cleanWing = part.trim();
        let lowerClean = cleanWing.toLowerCase();
        
        if (lowerClean.includes('chetna') || lowerClean.includes('prayatna')) {
          newWingsSet.add('Prerna Wing');
        } else if (cleanWing.length > 0) {
          newWingsSet.add(cleanWing);
        }
      });
    });

    const finalWingsArray = Array.from(newWingsSet).sort();
    
    // Sort original as well for stable equality check
    const sortedOriginal = [...originalWings].sort();
    const isDifferent = JSON.stringify(sortedOriginal) !== JSON.stringify(finalWingsArray);

    if (isDifferent) {
      usersToUpdate.push({
        id: doc.id,
        name: data.name,
        before: originalWings,
        after: finalWingsArray
      });
    }
  });

  console.log(`  Scanned ${totalUsers} total users.`);
  console.log(`  Found ${usersToUpdate.length} user(s) requiring wing updates.\n`);

  if (usersToUpdate.length > 0) {
    console.log('📝 USERS TO BE MODIFIED:');
    usersToUpdate.forEach(u => {
      console.log(`  👤 ${u.name} (${u.id})`);
      console.log(`      Before: ${JSON.stringify(u.before)}`);
      console.log(`      After:  ${JSON.stringify(u.after)}\n`);
    });
  }

  if (DRY_RUN) {
    console.log('════════════════════════════════════════════════════════════');
    console.log('  🟢 DRY RUN COMPLETE. No data was modified.');
    console.log('════════════════════════════════════════════════════════════\n');
    process.exit(0);
  }

  console.log('════════════════════════════════════════════════════════════');
  console.log('  🔴 LIVE MODE — This will permanently modify data.');
  console.log('════════════════════════════════════════════════════════════');

  if (SKIP_CONFIRM) {
    console.log('  [--yes] Skipping confirmation prompt.');
  } else {
    const ans = await confirm(`\n  Type "UPDATE" to confirm modifying ${usersToUpdate.length} users: `);
    if (ans !== 'update') {
      console.log('\n  ❌ Aborted.');
      process.exit(0);
    }
  }

  console.log('\n  ✅ Confirmed. Starting batch update...\n');

  // Firestore batch writes support max 500 operations per batch
  let batch = db.batch();
  let count = 0;
  
  for (const u of usersToUpdate) {
    const ref = db.collection('users').doc(u.id);
    batch.update(ref, { wings: u.after });
    count++;
    
    if (count % 400 === 0) {
      await batch.commit();
      batch = db.batch();
      console.log(`  🔄 Committed ${count}/${usersToUpdate.length} updates...`);
    }
  }
  
  if (count % 400 !== 0) {
    await batch.commit();
  }

  console.log('\n════════════════════════════════════════════════════════════');
  console.log('  ✅ MIGRATION COMPLETE');
  console.log(`  Successfully updated wings for ${usersToUpdate.length} student(s).`);
  console.log('════════════════════════════════════════════════════════════\n');
}

migrateWings().catch(console.error);
