import os
import sys
import collections
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

def init_firebase():
    """Locates service account key and initializes Firebase."""
    
    # Common locations to check
    possible_paths = [
        "C:/Users/itses/CodeVault/Projects/Keys/IITP App/service-account.json",
        "serviceAccountKey.json",
        "scripts/serviceAccountKey.json",
        "../serviceAccountKey.json",
        "../../serviceAccountKey.json"
    ]
    
    key_path = None
    for path in possible_paths:
        if os.path.exists(path):
            key_path = os.path.abspath(path)
            print(f"Found service account key at: {key_path}")
            break
            
    if not key_path:
        print("Service account key not found.")
        sys.exit(1)

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(key_path)
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

def generate_preference_matrix(db):
    print("Scanning 'ttwStudents' collection...")
    
    collection_ref = db.collection('ttwStudents')
    docs = collection_ref.stream()
    
    # Structure: matrix[subject][position] = count
    matrix = collections.defaultdict(lambda: collections.defaultdict(int))
    
    # Track max position to determine columns
    max_position = 0
    total_students = 0
    
    for doc in docs:
        data = doc.to_dict()
        total_students += 1
        
        prefs = data.get('subjectPreferences')
        
        if not prefs:
            continue
            
        # Handle if it's a list (expected) or dict
        iterator = None
        if isinstance(prefs, list):
            iterator = enumerate(prefs)
        elif isinstance(prefs, dict):
            # Sort by key if it's a dict to ensure order '0', '1', etc.
            try:
                sorted_keys = sorted(prefs.keys(), key=lambda x: int(x))
                iterator = [(int(k), prefs[k]) for k in sorted_keys]
            except:
                iterator = prefs.items()
        
        if iterator:
            for index, subject in iterator:
                position = index + 1 # 1-based preference
                matrix[subject][position] += 1
                if position > max_position:
                    max_position = position

    print(f"\nScanned {total_students} students.")
    print("\nSubject Preference Matrix (Count of students who chose Subject X at Position Y):")
    
    # Prepare Table
    # Columns: Subject, Pref 1, Pref 2, ...
    
    headers = ["Subject"] + [f"Pref {i}" for i in range(1, max_position + 1)]
    
    # Calculate column widths
    col_widths = {i: len(h) for i, h in enumerate(headers)}
    
    # Get all subjects, sorted by 1st preference count (descending)
    all_subjects = sorted(matrix.keys(), key=lambda s: matrix[s].get(1, 0), reverse=True)
    
    rows = []
    for subject in all_subjects:
        row = [subject]
        # Update first column width
        col_widths[0] = max(col_widths[0], len(subject))
        
        for i in range(1, max_position + 1):
            count = matrix[subject].get(i, 0)
            val_str = str(count)
            row.append(val_str)
            
            # Update width
            col_widths[i] = max(col_widths[i], len(val_str))
        rows.append(row)
        
    # Print Table
    # Header format
    header_row = " | ".join(h.ljust(col_widths[i]) for i, h in enumerate(headers))
    separator = "-+-".join("-" * col_widths[i] for i in range(len(headers)))
    
    print("-" * len(header_row))
    print(header_row)
    print(separator)
    
    for row in rows:
        print(" | ".join(val.ljust(col_widths[i]) for i, val in enumerate(row)))
    print("-" * len(header_row))

if __name__ == "__main__":
    db = init_firebase()
    generate_preference_matrix(db)
