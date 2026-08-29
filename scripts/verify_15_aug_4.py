import firebase_admin
from firebase_admin import credentials, firestore

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

print("Scanning all records for event '15_Aug_4'...\n")
att_docs = db.collection("NSS_Events_Attendence").document("15_Aug_4").collection("attendance").get()

total = 0
anomalies = 0
anomaly_samples = []

for doc in att_docs:
    total += 1
    data = doc.to_dict()
    device_id = data.get("deviceId", "")
    
    if device_id != "Manual_Penalty_Exemption":
        anomalies += 1
        if anomalies <= 5:
            anomaly_samples.append({doc.id: data})

print(f"Total records checked: {total}")
print(f"Records WITH deviceId != 'Manual_Penalty_Exemption': {anomalies}")

if anomalies > 0:
    print("\nSample of anomalous records found:")
    for sample in anomaly_samples:
        print(sample)
