import json
with open('backups/users_pre_wings_migration_20260813_013003.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

for u in users:
    wings = u.get('wings', [])
    if not isinstance(wings, list): continue
    
    for w in wings:
        if w != w.strip() and not any(x in w.lower() for x in ['chetna', 'prayatna']):
            print(f"{u.get('name')} has trailing space in: '{w}'")
