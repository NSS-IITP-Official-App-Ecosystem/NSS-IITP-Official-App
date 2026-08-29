import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

def get_semester(date_str, eid=''):
    try:
        parts = date_str.strip().split()
        if len(parts) >= 2:
            day = int(parts[0])
            month_str = parts[1].lower()
            months = {'jan':1,'feb':2,'mar':3,'apr':4,'may':5,'jun':6,'jul':7,'aug':8,'sep':9,'oct':10,'nov':11,'dec':12}
            m = months.get(month_str[:3], 0)
            if m:
                if 7 <= m <= 11: return 1
                if m == 12 and day <= 10: return 1
                if m == 12 and day >= 11: return 2
                if 1 <= m <= 6: return 2
    except:
        pass
    return 1

events = list(db.collection('NSS_Events_Attendence').stream())
users = list(db.collection('users').stream())

print(f"Total users: {len(users)}, Total events: {len(events)}")

user_computed = {}

for u in users:
    roll = u.id.upper()
    udata = u.to_dict()
    user_computed[roll] = {
        'doc_id': u.id,
        'stored_sem1': float(udata.get('sem1Hours') or 0),
        'stored_sem2': float(udata.get('sem2Hours') or 0),
        'stored_hours': float(udata.get('hours') or 0),
        'stored_events': int(udata.get('eventsAttended') or 0),
        'userType': udata.get('userType') or '',
        'name': udata.get('name') or '',
        'calc_sem1': 0.0,
        'calc_sem2': 0.0,
        'calc_hours': 0.0,
        'calc_events': 0,
        'events_breakdown': []
    }

for e in events:
    ed = e.to_dict()
    eid = e.id
    sem = get_semester(ed.get('eventDate') or '', eid)
    h = float(ed.get('hours') or 0)
    neg_h = float(ed.get('negativeHours') or 0)
    is_mand = bool(ed.get('mandatory'))
    
    attendees = {a.get('rollNumber', '').upper() for a in (ed.get('attendees') or []) if a.get('rollNumber')}
    penalized = {r.upper() for r in (ed.get('penalizedRollNumbers') or [])}
    neg_penalty = {r.upper() for r in (ed.get('negativePenaltyRollNumbers') or [])}

    for roll, data in user_computed.items():
        if data['userType'] == 'Admin':
            continue
            
        if roll in attendees:
            data['calc_events'] += 1
            data['calc_hours'] += h
            if sem == 1: data['calc_sem1'] += h
            if sem == 2: data['calc_sem2'] += h
            data['events_breakdown'].append(f"+{h} ({eid})")
        elif roll in penalized or roll in neg_penalty:
            pen = neg_h if neg_h > 0 else h
            data['calc_hours'] -= pen
            if sem == 1: data['calc_sem1'] -= pen
            if sem == 2: data['calc_sem2'] -= pen
            data['events_breakdown'].append(f"-{pen} ({eid})")

discrepancies = []
for roll, data in user_computed.items():
    if data['userType'] == 'Admin': continue
    diff_sem1 = round(data['stored_sem1'] - data['calc_sem1'], 2)
    diff_hours = round(data['stored_hours'] - data['calc_hours'], 2)
    diff_events = data['stored_events'] - data['calc_events']
    
    if diff_sem1 != 0 or diff_hours != 0 or diff_events != 0:
        discrepancies.append({
            'doc_id': data['doc_id'],
            'roll': roll,
            'name': data['name'],
            'stored_sem1': data['stored_sem1'],
            'calc_sem1': data['calc_sem1'],
            'diff_sem1': diff_sem1,
            'stored_hours': data['stored_hours'],
            'calc_hours': data['calc_hours'],
            'stored_events': data['stored_events'],
            'calc_events': data['calc_events'],
            'events_breakdown': data['events_breakdown']
        })

print(f"\n=======================================================")
print(f"Total students with discrepancies: {len(discrepancies)}")
print(f"=======================================================\n")

for d in discrepancies[:30]:
    print(f"Roll: {d['roll']:<10} Name: {d['name']:<25} | Sem1: Stored={d['stored_sem1']:<5} Calc={d['calc_sem1']:<5} (Diff={d['diff_sem1']:<5}) | Events: Stored={d['stored_events']} Calc={d['calc_events']}")
    print(f"   Breakdown: {', '.join(d['events_breakdown'])}")

