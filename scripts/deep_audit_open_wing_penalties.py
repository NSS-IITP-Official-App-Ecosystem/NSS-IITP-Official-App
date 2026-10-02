import firebase_admin
from firebase_admin import credentials, firestore

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

ALL_WINGS = [
    "Teaching and Technical Wing",
    "Prerna Wing",
    "Rural Development Wing",
    "Environmental Wing",
    "Design and Curation Wing"
]

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

print(f"Loaded {len(users)} users and {len(events)} events.\n")

students_audit = {}

for u in users:
    d = u.to_dict()
    roll = u.id.upper()
    user_type = (d.get('userType') or '').strip().lower()
    if user_type == 'admin':
        continue
        
    user_wings = d.get('wings') or []
    
    students_audit[roll] = {
        'name': d.get('name', ''),
        'roll': roll,
        'wings': user_wings,
        'stored_sem1': float(d.get('sem1Hours') or 0.0),
        'stored_sem2': float(d.get('sem2Hours') or 0.0),
        'stored_total': float(d.get('hours') or 0.0),
        'stored_events_count': int(d.get('eventsAttended') or 0),
        
        # Calculated breakdowns
        'calc_sem1_open': 0.0,
        'calc_sem1_wing': 0.0,
        'calc_sem1_penalty': 0.0,
        
        'calc_sem2_open': 0.0,
        'calc_sem2_wing': 0.0,
        'calc_sem2_penalty': 0.0,
        
        'attended_open_events': [],
        'attended_wing_events': [],
        'exempted_events': [],
        'penalized_events': []
    }

for e in events:
    ed = e.to_dict()
    eid = e.id
    sem = get_semester(ed.get('eventDate') or '', eid)
    h = float(ed.get('hours') or 0.0)
    neg_h = float(ed.get('negativeHours') or 0.0)
    is_mand = bool(ed.get('mandatory'))
    
    event_wings = ed.get('wings') or []
    is_open = len(event_wings) == 0 or all(w in event_wings for w in ALL_WINGS) or ed.get('displayWings') == 'Open Event'
    
    attendees = {a.get('rollNumber', '').upper() for a in (ed.get('attendees') or []) if a.get('rollNumber')}
    penalized = {r.upper() for r in (ed.get('penalizedRollNumbers') or [])}
    neg_penalty = {r.upper() for r in (ed.get('negativePenaltyRollNumbers') or [])}
    zero_penalty = {r.upper() for r in (ed.get('zeroPenaltyRollNumbers') or [])}
    exempt = {r.upper() for r in (ed.get('exemptedRollNumbers') or [])}
    pos_penalty = {r.upper() for r in (ed.get('positivePenaltyRollNumbers') or [])}

    for roll, data in students_audit.items():
        is_user_wing = any(w in event_wings for w in data['wings'])
        
        # Case 1: Attended or Positive Override
        if roll in attendees or roll in pos_penalty:
            if is_open:
                if sem == 1: data['calc_sem1_open'] += h
                if sem == 2: data['calc_sem2_open'] += h
                data['attended_open_events'].append((eid, h, sem))
            else:
                if sem == 1: data['calc_sem1_wing'] += h
                if sem == 2: data['calc_sem2_wing'] += h
                data['attended_wing_events'].append((eid, h, sem))
                
        # Case 2: Explicitly Exempted / Zero Hours (Penalty Removed)
        elif roll in zero_penalty or roll in exempt:
            data['exempted_events'].append((eid, sem))
            
        # Case 3: Explicitly Penalized
        elif roll in penalized or roll in neg_penalty:
            pen = neg_h if neg_h > 0 else h
            if sem == 1: data['calc_sem1_penalty'] += pen
            if sem == 2: data['calc_sem2_penalty'] += pen
            data['penalized_events'].append((eid, pen, sem))

# Analyze all students
unrefunded_penalty_issues = []
discrepancies = []
negative_hour_students = []

for roll, data in students_audit.items():
    calc_sem1_total = round(data['calc_sem1_open'] + data['calc_sem1_wing'] - data['calc_sem1_penalty'], 2)
    calc_sem2_total = round(data['calc_sem2_open'] + data['calc_sem2_wing'] - data['calc_sem2_penalty'], 2)
    calc_total_hours = round(calc_sem1_total + calc_sem2_total, 2)
    calc_events_count = len(data['attended_open_events']) + len(data['attended_wing_events'])
    
    diff_sem1 = round(data['stored_sem1'] - calc_sem1_total, 2)
    diff_sem2 = round(data['stored_sem2'] - calc_sem2_total, 2)
    diff_total = round(data['stored_total'] - calc_total_hours, 2)
    diff_events = data['stored_events_count'] - calc_events_count
    
    # Check if student has negative stored hours
    if data['stored_sem1'] < 0 or data['stored_sem2'] < 0 or data['stored_total'] < 0:
        negative_hour_students.append({
            'roll': roll,
            'name': data['name'],
            'stored_sem1': data['stored_sem1'],
            'stored_sem2': data['stored_sem2'],
            'stored_total': data['stored_total'],
            'penalties': data['penalized_events'],
            'exemptions': data['exempted_events']
        })
        
    # Check if student was removed from penalty (exempted) but still has negative deduction in stored hours
    if data['exempted_events'] and diff_sem1 < 0:
        unrefunded_penalty_issues.append({
            'roll': roll,
            'name': data['name'],
            'diff': diff_sem1,
            'exemptions': data['exempted_events']
        })
        
    if diff_sem1 != 0 or diff_sem2 != 0 or diff_total != 0 or diff_events != 0:
        discrepancies.append({
            'roll': roll,
            'name': data['name'],
            'stored_sem1': data['stored_sem1'],
            'calc_sem1': calc_sem1_total,
            'open_sem1': data['calc_sem1_open'],
            'wing_sem1': data['calc_sem1_wing'],
            'pen_sem1': data['calc_sem1_penalty'],
            'diff_sem1': diff_sem1,
            'stored_total': data['stored_total'],
            'calc_total': calc_total_hours,
            'diff_total': diff_total,
            'stored_events': data['stored_events_count'],
            'calc_events': calc_events_count,
            'exemptions': data['exempted_events'],
            'penalties': data['penalized_events']
        })

print("=" * 80)
print(f"DEEP AUDIT REPORT FOR ALL {len(students_audit)} STUDENTS")
print("=" * 80)
print(f"1. Total Students with Calculation Discrepancies: {len(discrepancies)}")
print(f"2. Students Removed from Penalty but Not Refunded: {len(unrefunded_penalty_issues)}")
print(f"3. Students with Negative Balance: {len(negative_hour_students)}")

if negative_hour_students:
    print("\n--- Students with Negative Balance (Active Penalties) ---")
    for s in negative_hour_students:
        print(f"• {s['roll']} ({s['name']}): Sem1={s['stored_sem1']}h, Sem2={s['stored_sem2']}h, Total={s['stored_total']}h")
        print(f"  Penalties: {s['penalties']}")
        print(f"  Exemptions: {s['exemptions']}")

if discrepancies:
    print("\n--- Discrepancies Details ---")
    for d in discrepancies:
        print(f"• {d['roll']} ({d['name']}):")
        print(f"  Stored Sem1: {d['stored_sem1']}h | Calc: {d['calc_sem1']}h (Open: {d['open_sem1']}h + Wing: {d['wing_sem1']}h - Penalty: {d['pen_sem1']}h)")
        print(f"  Exemptions: {d['exemptions']}")
        print(f"  Penalties: {d['penalties']}")
else:
    print("\n✅ All 374 students have 100% accurate hours calculations!")
    print("   Every student's stored total exactly equals (Open Hours + Wing Hours - Active Penalties).")
    print("   No unrefunded penalty deductions exist anywhere in the database.")
