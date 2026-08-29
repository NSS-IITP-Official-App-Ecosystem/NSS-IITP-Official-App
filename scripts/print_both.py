import json
with open('backups/users_pre_wings_migration_20260813_013003.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

print('--- The 6 Students in BOTH Chetna and Prayatna ---')
count = 0
for u in users:
    original = u.get('wings', [])
    if not isinstance(original, list): continue
    
    # Is it in both?
    has_chetna = any('chetna' in w.lower() for w in original)
    has_prayatna = any('prayatna' in w.lower() for w in original)
    
    if has_chetna and has_prayatna:
        count += 1
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
        print(f"\n  Student: {u.get('name')} ({u.get('rollNumber')})")
        print(f"      Before: {original}")
        print(f"      After:  {final}")

print(f'\nTotal printed: {count}')
