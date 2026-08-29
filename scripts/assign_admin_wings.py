import csv
import firebase_admin
from firebase_admin import credentials, firestore

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    return firestore.client()

db = init_firebase()

# Extracted from the provided image grid, modified to remove DEV Team per instructions
wings_by_name = {
    "Parnava Maitra": ["Design and Curation Wing"],
    "Shankhadeep Das": ["Design and Curation Wing"],
    "Anish Kumar": ["Design and Curation Wing", "Teaching and Technical Wing"],
    "Tanishk Raj": ["Teaching and Technical Wing"],
    "Anshika Garg": ["Teaching and Technical Wing"],
    "Rahul Durgachand": ["Rural Development Wing"],
    "Ayantika Halder": ["Rural Development Wing", "Nukkad"],
    "Kshitij Singh": ["Environmental Wing"],
    "Riju": ["Environmental Wing"],
    "Piyush Kumar": ["Prayatna + Chetna"],
    "Dikshit Verma": ["Prayatna + Chetna"],
    "Lakshya Tiwari": ["Design and Curation Wing"],
    "Saanvi Priyadarshini Rahash Muduli": ["Design and Curation Wing"],
    "Samriddhi": ["Design and Curation Wing"],
    "Anirudh Karthikeyan": ["Design and Curation Wing"],
    "Arit Raj": ["Design and Curation Wing"],
    "Taniya Kumari Gupta": ["Teaching and Technical Wing", "Magazine Team"],
    "Amoolya Sharan": ["Teaching and Technical Wing"],
    "Satish Kumar Yadav": ["Teaching and Technical Wing"],
    "Subhash Pandey": ["Teaching and Technical Wing"],
    "Shailendra Meena": ["Teaching and Technical Wing"],
    "Prakhar Kumar": ["Teaching and Technical Wing"],
    "Ishika Agarwal": ["Teaching and Technical Wing", "Magazine Team"],
    "Tejveer": ["Teaching and Technical Wing"],
    "Tanvi Mishra": ["Teaching and Technical Wing"],
    "Tanish Garg": ["Teaching and Technical Wing"],
    "Tarush Mohan": ["Teaching and Technical Wing", "Nukkad", "Magazine Team"],
    "Utkarsh Ranjan": ["Teaching and Technical Wing"],
    "Kajal Batra": ["Teaching and Technical Wing"],
    "Upendar Vadithya": ["Rural Development Wing"],
    "Rachapally Pradeep": ["Rural Development Wing"],
    "Pragati Sharma": ["Rural Development Wing"],
    "Pranav Kumar": ["Rural Development Wing", "Prerna Wing"],
    "Kartan Anish Kumar": ["Rural Development Wing"],
    "Pritam Raj": ["Rural Development Wing"],
    "Harshil Jain": ["Rural Development Wing", "Nukkad"],
    "Parmar Kashyap": ["Rural Development Wing"],
    "Satyam Kumar": ["Environmental Wing", "Nukkad"],
    "Manish Sharma": ["Environmental Wing"],
    "Soni Pal": ["Environmental Wing"],
    "Aman Saroj": ["Environmental Wing"],
    "Manak Ram": ["Environmental Wing"],
    "Mayank Biswas": ["Environmental Wing"],
    "Souranil Patra": ["Environmental Wing"],
    "Kavya Gupta": ["Environmental Wing"],
    "Divya Kumari": ["Prerna Wing"],
    "Sabavath Aishwarya Chouhan": ["Prerna Wing"],
    "J Samrutha": ["Prerna Wing"],
    "Kesanapalli Sanjana": ["Prerna Wing"],
    "Mahi Dinesh Borkar": ["Prerna Wing"],
    "Harshit Kumar": ["Prerna Wing"],
    "Sakala Sathwik": ["Prerna Wing"],
    "Sandeep Raj": ["Prerna Wing"],
    "Aniska Saha": ["Prerna Wing"],
    "Priyanshi Patel": ["Prerna Wing"],
    "Palak Banshiwal": ["Prerna Wing"],
    "Prity Kumari": ["Design and Curation Wing"],
    "Anushtup Kumar": ["Design and Curation Wing"]
}

print("Reading CSV and mapping wings based on the image grid...\n")
batch = db.batch()
count = 0

with open("scripts/admins_2026.csv", "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    next(reader)
    for row in reader:
        if len(row) >= 3 and row[2].strip():
            name = row[1].strip()
            roll = row[2].strip().upper()
            
            assigned_wings = []
            for n, w in wings_by_name.items():
                if name.lower() == n.lower() or name.lower() in n.lower() or n.lower() in name.lower():
                    assigned_wings = w
                    break
            
            if assigned_wings:
                print(f"[{roll}] {name} -> {assigned_wings}")
                doc_ref = db.collection("users").document(roll)
                batch.update(doc_ref, {"wings": assigned_wings})
                count += 1
            else:
                print(f"⚠️  No wings found in the image for: {name} ({roll})")

# Explicitly add Sourav Mondal and Ayush Anand since they were not in the CSV
explicit_admins = [
    {"roll": "2501EE04", "name": "Sourav Mondal"},
    {"roll": "2501CS20", "name": "Ayush Anand"}
]

for admin in explicit_admins:
    print(f"[{admin['roll']}] {admin['name']} -> ['Teaching and Technical Wing']")
    doc_ref = db.collection("users").document(admin['roll'])
    batch.set(doc_ref, {
        "userType": "admin",
        "wings": ["Teaching and Technical Wing"]
    }, merge=True)
    count += 1

batch.commit()
print(f"\n✅ Successfully updated {count} admins with their respective wings!")
