import sys
import firebase_admin
from firebase_admin import credentials, firestore

sys.stdout.reconfigure(encoding='utf-8')

cred = credentials.Certificate('../serviceAccountKey.json')
firebase_admin.initialize_app(cred)
db = firestore.client()

eid = '26_Aug_Blood__Platelet_Donation_Awareness_Session'
edoc_ref = db.collection('NSS_Events_Attendence').document(eid)
edata = edoc_ref.get().to_dict()

penalized_list = [str(x) for x in edata.get('penalizedRollNumbers', [])]
penalized_upper = [x.upper() for x in penalized_list]
exempted_upper = set([str(x).upper() for x in edata.get('exemptedRollNumbers', [])])

overlap = [roll for roll in penalized_upper if roll in exempted_upper]
negative_hours = float(edata.get('negativeHours', 2.0))

print(f"Refunding {negative_hours} hours to {len(overlap)} students and removing them from penalizedRollNumbers...")

batch = db.batch()

for roll in overlap:
    user_ref = db.collection('users').document(roll)
    user_doc = user_ref.get()
    if user_doc.exists:
        udata = user_doc.to_dict()
        old_hours = udata.get('hours', 0.0)
        old_sem1 = udata.get('sem1Hours', 0.0)
        new_hours = old_hours + negative_hours
        new_sem1 = old_sem1 + negative_hours
        print(f"  {roll} ({udata.get('name')}): hours {old_hours} -> {new_hours}, sem1: {old_sem1} -> {new_sem1}")
        batch.update(user_ref, {
            'hours': firestore.Increment(negative_hours),
            'sem1Hours': firestore.Increment(negative_hours)
        })

# Clean penalizedRollNumbers on the event document
cleaned_penalized = [r for r in penalized_list if r.upper() not in exempted_upper]
batch.update(edoc_ref, {
    'penalizedRollNumbers': cleaned_penalized
})

batch.commit()
print(f"\nSuccessfully refunded {negative_hours} hours to all {len(overlap)} students and updated event {eid}!")
