import json
with open('backups/users_pre_wings_migration_20260813_013003.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

# Python 184
touched_python = []
for u in users:
    wings = u.get('wings', [])
    if not isinstance(wings, list): continue
    if any(',' in w for w in wings) or any('chetna' in w.lower() for w in wings) or any('prayatna' in w.lower() for w in wings):
        touched_python.append(u.get('name'))

# JS 196
touched_js = []
with open('backups/dry_run_output.txt', 'r', encoding='utf-16') as f:
    for line in f:
        if line.strip().startswith('👤'):
            name = line.strip().split('👤 ')[1].split(' (')[0]
            touched_js.append(name)

python_set = set(touched_python)
js_set = set(touched_js)

print(f"In JS but not Python: {js_set - python_set}")
print(f"In Python but not JS: {python_set - js_set}")
