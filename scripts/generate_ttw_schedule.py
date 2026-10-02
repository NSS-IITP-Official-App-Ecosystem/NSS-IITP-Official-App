import firebase_admin
from firebase_admin import credentials, firestore
from collections import defaultdict

cred = credentials.Certificate('serviceAccountKey.json')
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred)
db = firestore.client()

# 1. Fetch TTW Students
students_docs = list(db.collection('ttwStudents').stream())
students = []
for doc in students_docs:
    d = doc.to_dict()
    roll = doc.id
    name = d.get('name') or roll
    group = str(d.get('group') or d.get('academicGroup') or '')
    if group.startswith('G') or group.startswith('g'):
        group = group[1:]
    classes_quota = int(d.get('classesPerWeek') or 1)
    prefs = [s.strip() for s in (d.get('subjectPreferences') or [])]
    modes = d.get('teachingModes') or []
    
    students.append({
        'roll': roll,
        'name': name,
        'group': group,
        'quota': classes_quota,
        'assigned_count': 0,
        'prefs': prefs,
        'modes': modes,
        'assigned_slots': [] # (day, time, school, subject)
    })

# 2. Fetch Presets & Availability
ts_presets = list(db.collection('teachingSlotPresets').stream())
va_presets = {doc.id: doc.to_dict() for doc in db.collection('volunteerAvailability').stream()}

print(f"Loaded {len(students)} TTW Volunteers.")
print(f"Loaded {len(ts_presets)} Teaching Slot Presets.\n")

# Build all required slots to be filled
slots_to_fill = []

days_order = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

for ts_doc in ts_presets:
    ts_data = ts_doc.to_dict()
    preset_name = ts_data.get('presetName') or ts_doc.id
    va_data = va_presets.get(preset_name) or ts_data.get('availability') or {}
    availability_matrix = ts_data.get('availability') or (va_data.get('availability') if isinstance(va_data, dict) else {}) or {}
    
    column_names = ts_data.get('columnNames') or []
    subjects_needed = ts_data.get('subjects') or []
    schedule_days = ts_data.get('schedule') or []
    
    # schedule_days has [{'day': 'Mon', 'slots': [False, True, ...]}, ...]
    for day_obj in schedule_days:
        day = day_obj.get('day')
        slots_bool = day_obj.get('slots') or []
        for slot_idx, is_active in enumerate(slots_bool):
            if is_active:
                col_info = column_names[slot_idx] if slot_idx < len(column_names) else {}
                class_time = col_info.get('classTime') if isinstance(col_info, dict) else f"Slot {slot_idx}"
                free_time = col_info.get('freeGroupTime') if isinstance(col_info, dict) else ""
                
                # Get available free groups for this day & slot
                day_avail = availability_matrix.get(day) or {}
                avail_str = day_avail.get(str(slot_idx)) or ""
                free_groups = set()
                if avail_str:
                    free_groups = {g.strip() for g in avail_str.split(',') if g.strip()}
                
                # Determine which subjects are required
                for subj in subjects_needed:
                    subj_name = subj.get('subjectName') or 'General'
                    class_count = int(subj.get('classCount') or 1)
                    priority = int(subj.get('priority') or 1)
                    
                    for req_i in range(class_count):
                        slots_to_fill.append({
                            'preset': preset_name,
                            'day': day,
                            'slot_idx': slot_idx,
                            'time': class_time,
                            'free_time': free_time,
                            'subject': subj_name,
                            'priority': priority,
                            'free_groups': free_groups,
                            'assigned_volunteer': None
                        })

print(f"Total teaching class requirements to schedule: {len(slots_to_fill)}\n")

# 3. Schedule Allocation Algorithm
# Sort slots by: Day index, Priority, and number of available groups (most constrained first)
def slot_sort_key(s):
    d_idx = days_order.index(s['day']) if s['day'] in days_order else 99
    avail_count = len(s['free_groups']) if s['free_groups'] else 999
    return (s['priority'], avail_count, d_idx, s['slot_idx'])

slots_to_fill.sort(key=slot_sort_key)

# Preference rounds (Round 1: 1st preference, Round 2: 2nd pref, Round 3: 3rd pref, Round 4+: any pref)
for round_pref in range(1, 10):
    for slot in slots_to_fill:
        if slot['assigned_volunteer'] is not None:
            continue
        
        target_subject = slot['subject'].lower()
        slot_day = slot['day']
        slot_time = slot['time']
        free_groups = slot['free_groups']
        
        best_cand = None
        best_score = -9999
        
        for cand in students:
            # Check quota
            if cand['assigned_count'] >= cand['quota']:
                continue
            
            # Check availability (Group must be in free_groups if free_groups specified)
            if free_groups and cand['group'] not in free_groups:
                continue
            
            # Check collision (Cannot teach two classes at the same time on the same day)
            has_time_collision = any(
                aslot['day'] == slot_day and aslot['time'] == slot_time 
                for aslot in cand['assigned_slots']
            )
            if has_time_collision:
                continue
            
            # Check subject preference ranking
            cand_prefs = [p.lower() for p in cand['prefs']]
            pref_rank = 99
            if target_subject in cand_prefs:
                pref_rank = cand_prefs.index(target_subject) + 1
            
            if pref_rank > round_pref:
                continue
            
            # Score: lower preference rank is better, fewer assigned classes so far is better
            score = (100 - pref_rank * 10) - (cand['assigned_count'] * 15)
            
            # Bonus if they don't already have another class on the same day (distribute across days)
            classes_on_same_day = sum(1 for aslot in cand['assigned_slots'] if aslot['day'] == slot_day)
            if classes_on_same_day == 0:
                score += 10
            elif classes_on_same_day >= 2:
                score -= 20
                
            if score > best_score:
                best_score = score
                best_cand = cand
                
        if best_cand is not None:
            slot['assigned_volunteer'] = best_cand
            best_cand['assigned_count'] += 1
            best_cand['assigned_slots'].append({
                'day': slot_day,
                'time': slot_time,
                'preset': slot['preset'],
                'subject': slot['subject']
            })

preset_grouped = defaultdict(lambda: defaultdict(list))
for s in slots_to_fill:
    preset_grouped[s['preset']][s['day']].append(s)

# 4. Save to Firestore under generatedSchedules collection
import uuid

schedule_id = str(uuid.uuid4())
now_ts = firestore.SERVER_TIMESTAMP

batch = db.batch()
batch_count = 0

for preset_name, days_map in preset_grouped.items():
    assigned_preset_slots = [s for s_list in days_map.values() for s in s_list if s['assigned_volunteer']]
    
    # Extract reference data
    unique_days = []
    for d in days_order:
        if d in days_map:
            unique_days.append(d)
            
    # Collect unique slot times
    slot_times_map = {}
    for s in assigned_preset_slots:
        slot_times_map[s['slot_idx']] = s['time']
        
    sorted_slot_indices = sorted(slot_times_map.keys())
    unique_slot_labels = [slot_times_map[i] for i in sorted_slot_indices]
    
    unique_subjects = list({s['subject'] for s in assigned_preset_slots})
    
    # Build assignments list
    assignments = []
    for s in assigned_preset_slots:
        v = s['assigned_volunteer']
        day_idx = unique_days.index(s['day']) if s['day'] in unique_days else 0
        
        assignments.append({
            'volunteerName': v['name'],
            'volunteerRollNo': v['roll'],
            'volunteerGroup': str(v['group']),
            'dayIndex': day_idx,
            'slotIndex': s['slot_idx'],
            'interviewScore': 0,
            'assignedSubject': s['subject']
        })
        
    doc_data = {
        'scheduleId': schedule_id,
        'timestamp': now_ts,
        'presetName': preset_name,
        'referenceData': {
            'days': unique_days,
            'slots': unique_slot_labels,
            'subjects': unique_subjects
        },
        'assignments': assignments
    }
    
    doc_ref = db.collection('generatedSchedules').document(preset_name)
    batch.set(doc_ref, doc_data)
    batch_count += 1

batch.commit()
print(f"Committed {batch_count} preset schedules directly to Firestore collection 'generatedSchedules'.")

# Print Results & Save Markdown Artifact
out_lines = []
out_lines.append("# Teaching and Technical Wing Generated Schedule\n")

assigned_count = sum(1 for s in slots_to_fill if s['assigned_volunteer'])
out_lines.append(f"**Total Requirements**: {len(slots_to_fill)} class slots across 24 presets")
out_lines.append(f"**Total Assigned**: {assigned_count} / {len(slots_to_fill)} ({assigned_count*100//len(slots_to_fill)}%)\n")

for preset_name, days_map in preset_grouped.items():
    out_lines.append(f"\n### Location / School Preset: {preset_name}")
    out_lines.append("| Day | Time | Subject | Assigned Volunteer | Roll No | Group |")
    out_lines.append("| :--- | :--- | :--- | :--- | :--- | :--- |")
    for day in days_order:
        if day in days_map:
            for s in sorted(days_map[day], key=lambda x: x['time']):
                v = s['assigned_volunteer']
                if v:
                    out_lines.append(f"| {s['day']} | {s['time']} | {s['subject']} | **{v['name']}** | `{v['roll']}` | G{v['group']} |")
                else:
                    out_lines.append(f"| {s['day']} | {s['time']} | {s['subject']} | *Unassigned* | - | - |")

out_lines.append("\n---\n### Volunteer Workload Distribution Summary\n")
assigned_vols = [s for s in students if s['assigned_count'] > 0]
out_lines.append(f"**Total Volunteers Allocated**: {len(assigned_vols)} / {len(students)}\n")
out_lines.append("| Roll Number | Volunteer Name | Group | Assigned / Quota | Assigned Slots |")
out_lines.append("| :--- | :--- | :--- | :--- | :--- |")

for s in sorted(assigned_vols, key=lambda x: (-x['assigned_count'], x['roll'])):
    slots_desc = [f"{sl['day']} {sl['time']} ({sl['subject']} @ {sl['preset']})" for sl in s['assigned_slots']]
    out_lines.append(f"| `{s['roll']}` | {s['name']} | G{s['group']} | **{s['assigned_count']} / {s['quota']}** | {', '.join(slots_desc)} |")

md_content = "\n".join(out_lines)

with open("scripts/generated_schedule_output.md", "w", encoding="utf-8") as f:
    f.write(md_content)

print(f"Schedule generated successfully! Saved to scripts/generated_schedule_output.md")
print(f"Total Volunteers Allocated: {len(assigned_vols)} / {len(students)}")
print(f"Total Classes Filled: {assigned_count} / {len(slots_to_fill)}")


