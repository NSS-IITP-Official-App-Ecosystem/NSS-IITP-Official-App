import random
import firebase_admin
from firebase_admin import credentials, firestore

# Initialize Firebase Admin SDK
cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

# Get all documents in the 'Student' collection
students_ref = db.collection("Student")
docs = students_ref.stream()

for doc in docs:
    random_bool = random.choice([True, False])
    students_ref.document(doc.id).update({"Teaching_wing": random_bool})
    print(f"Updated {doc.id} with Teaching_wing={random_bool}")

print("All student documents updated with random Teaching_wing field.")