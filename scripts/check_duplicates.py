import json
with open('backups/users_pre_wings_migration_20260813_013003.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

for u in users:
    wings = u.get('wings', [])
    if not isinstance(wings, list): continue
    
    unique_wings = set(wings)
    if len(unique_wings) != len(wings) and not any(x in w.lower() for x in ['chetna', 'prayatna'] for w in wings) and not any(',' in w for w in wings):
        print(f"{u.get('name')} has duplicates: {wings}")
