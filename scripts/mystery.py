import json
with open('backups/users_pre_wings_migration_20260813_013003.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

count = 0
for u in users:
    original = u.get('wings', [])
    if not isinstance(original, list): continue
    
    new_set = set()
    for w in original:
        parts = w.split(',')
        for p in parts:
            c = p.strip()
            if 'chetna' in c.lower() or 'prayatna' in c.lower():
                new_set.add('Prerna Wing')
            elif c:
                new_set.add(c)
                
    final = sorted(list(new_set))
    orig_sorted = sorted(original)
    
    if orig_sorted != final:
        count += 1
        if not any(x in w.lower() for x in ['chetna', 'prayatna'] for w in original) and not any(',' in w for w in original):
            print(f"Mystery touch: {u.get('name')} | Before: {orig_sorted} | After: {final}")

print(f"Total touched according to JS logic: {count}")
