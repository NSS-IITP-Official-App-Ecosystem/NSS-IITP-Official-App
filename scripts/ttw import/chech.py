import firebase_admin
from firebase_admin import credentials, firestore

# 1. Initialize Firebase
cred = credentials.Certificate(r"C:\Users\itses\Desktop\NSS_App\Import\key.json")  # path to your key file
firebase_admin.initialize_app(cred)
db = firestore.client()

# 2. Reference the collection
collection_ref = db.collection("ttwStudents")

# 3. Fetch all documents
docs = collection_ref.stream()

# 4. Scan and filter
count = 0
print("Students with interviewScore = 0 or > 50:\n")

for doc in docs:
    data = doc.to_dict()
    interview_score = data.get("interviewScore", None)
    name = data.get("name", "Unknown")
    roll_number = data.get("rollNumber", doc.id)

    # Check condition
    if interview_score == 0 or (isinstance(interview_score, (int, float)) and interview_score > 50):
        print(f"Name: {name}, Roll: {roll_number}, Interview Score: {interview_score}")
        count += 1

print("\nTotal students found:", count)
