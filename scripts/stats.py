import json
with open('backups/users_pre_wings_migration_20260813_013003.json', 'r', encoding='utf-8') as f:
    users = json.load(f)

pure_chetna = []
pure_prayatna = []
both = []
comma_mashed = []
untouched = []

for u in users:
    wings = u.get('wings', [])
    if not isinstance(wings, list): continue
    
    # Check for comma-mashed
    is_comma_mashed = any(',' in w for w in wings)
    if is_comma_mashed:
        comma_mashed.append(u)
        continue
        
    has_chetna = any('chetna' in w.lower() for w in wings)
    has_prayatna = any('prayatna' in w.lower() for w in wings)
    
    if has_chetna and has_prayatna:
        both.append(u)
    elif has_chetna:
        pure_chetna.append(u)
    elif has_prayatna:
        pure_prayatna.append(u)
    else:
        untouched.append(u)

print(f'Total Users: {len(users)}')
print(f'Comma-mashed edge cases: {len(comma_mashed)}')
print(f'In Both Chetna & Prayatna: {len(both)}')
print(f'Purely Chetna Wing: {len(pure_chetna)}')
print(f'Purely Prayatna Wing: {len(pure_prayatna)}')
print(f'Total touched: {len(comma_mashed) + len(both) + len(pure_chetna) + len(pure_prayatna)}')
print(f'Total UNTOUCHED: {len(untouched)}')
print('\nSample of 5 Untouched Users:')
for u in untouched[:5]:
    print(f"  - {u.get('name')} | Wings: {u.get('wings')}")
