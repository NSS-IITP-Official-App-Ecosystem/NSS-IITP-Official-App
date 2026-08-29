import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

ORIGINAL_79_CLASSES = {
    '2601AI48': 2,
    '2601CB11': 2,
    '2601CB24': 2,
    '2601CB25': 2,
    '2601CB26': 1,
    '2601CB53': 2,
    '2601CE11': 1,
    '2601CE13': 2,
    '2601CE17': 2,
    '2601CE29': None,
    '2601CE30': 2,
    '2601CE35': 2,
    '2601CE43': 1,
    '2601CE54': None,
    '2601CE57': 2,
    '2601CE63': None,
    '2601CS12': 2,
    '2601CS15': None,
    '2601CS42': 1,
    '2601CS47': 2,
    '2601CS49': 1,
    '2601CS50': 2,
    '2601CS64': 2,
    '2601CS82': None,
    '2601CS87': 2,
    '2601CT19': 1,
    '2601CT21': 2,
    '2601CT23': None,
    '2601EC11': 1,
    '2601EC13': 2,
    '2601EC14': 2,
    '2601EC29': 3,
    '2601EC45': 1,
    '2601EE05': 1,
    '2601EE07': 2,
    '2601EE08': 1,
    '2601EE10': 1,
    '2601EE23': 1,
    '2601EE28': 1,
    '2601EE40': 2,
    '2601MC02': 2,
    '2601MC06': 1,
    '2601MC13': 2,
    '2601MC17': 2,
    '2601MC20': 2,
    '2601MC29': 2,
    '2601MC38': 2,
    '2601MC44': 1,
    '2601MC50': 2,
    '2601ME02': 2,
    '2601ME04': 2,
    '2601ME06': 2,
    '2601ME08': 3,
    '2601ME11': 2,
    '2601ME17': 2,
    '2601ME21': 2,
    '2601ME40': 2,
    '2601ME43': 2,
    '2601ME50': 3,
    '2601ME55': 2,
    '2601ME60': 2,
    '2601ME66': 2,
    '2601ME80': 1,
    '2601MM04': 1,
    '2601MM21': 2,
    '2601MM31': 1,
    '2601MM38': 1,
    '2601MM42': 1,
    '2601PH04': None,
    '2601PH14': 2,
    '2602CM04': 1,
    '2602CM05': 1,
    '2602CS08': 2,
    '2602MC03': 2,
    '2602VL06': 2,
    '2603CB03': 2,
    '2603CE04': 1,
    '2603CT03': 1,
    '2603ES03': 2,
    '2603ME04': 2,
}

TODAY_ADDED_ROLLS = [
    '2601MM25', '2601CT08', '2601CB02', '2601CS85', '2601MM32', '2601ME64', 
    '2601CB30', '2601EC03', '2601EC43', '2601MC16', '2601ME59', '2601CE40', 
    '2603ME13', '2601MM19', '2601MM05', '2601PH23', '2601PH13', '2601CB46', 
    '2601CE37', '2601CE22', '2601CT09', '2601ME82', '2601PH30', '2602CS06', 
    '2603CS01', '2601CE15', '2602CS02', '2601CB12', '2601EE33', '2601ME36', 
    '2501CE41'
]

# Check existing documents first
existing_docs = {d.id for d in db.collection('ttwStudents').stream()}

batch = db.batch()
restored_count = 0
today_count = 0

for roll, orig_val in ORIGINAL_79_CLASSES.items():
    if roll in existing_docs:
        ref = db.collection('ttwStudents').document(roll)
        if orig_val is not None:
            batch.set(ref, {"classesPerWeek": orig_val}, merge=True)
        else:
            batch.update(ref, {"classesPerWeek": firestore.DELETE_FIELD})
        restored_count += 1

for roll in TODAY_ADDED_ROLLS:
    if roll in existing_docs:
        ref = db.collection('ttwStudents').document(roll)
        batch.set(ref, {"classesPerWeek": 1}, merge=True)
        today_count += 1

batch.commit()
print(f"Restored previous classesPerWeek for {restored_count} original students.")
print(f"Set classesPerWeek = 1 for {today_count} newly added students.")
