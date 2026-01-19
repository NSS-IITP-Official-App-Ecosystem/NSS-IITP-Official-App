import os
import sys

# Try importing firebase_admin
try:
    import firebase_admin
    from firebase_admin import credentials
    from firebase_admin import firestore
except ImportError:
    print("Error: 'firebase-admin' package is missing.")
    print("Please run: pip install firebase-admin")
    sys.exit(1)

def get_service_account_path(prompt_message):
    """Helper to get service account key path from user."""
    while True:
        key_path = input(f"{prompt_message}: ").strip()
        key_path = key_path.strip("'\"")
        
        if not key_path:
            print("Path cannot be empty.")
            continue

        if os.path.exists(key_path) and key_path.endswith('.json'):
            return os.path.abspath(key_path)
        else:
            print(f"Error: Valid JSON key file not found at {key_path}.")


def init_firebase_app(name, key_path):
    """Initialize a Firebase app with a given name and credentials."""
    try:
        cred = credentials.Certificate(key_path)
        app = firebase_admin.initialize_app(cred, name=name)
        return firestore.client(app)
    except Exception as e:
        print(f"Failed to initialize Firebase app '{name}': {e}")
        sys.exit(1)

def list_collections(db):
    """List all top-level collections in a database."""
    try:
        collections = db.collections()
        collection_names = [col.id for col in collections]
        return collection_names
    except Exception as e:
        print(f"Error listing collections: {e}")
        return []

def copy_collection(source_db, dest_db, collection_name, mode='merge'):
    """
    Copy a collection from source to destination.
    mode: 'merge' or 'replace'
    """
    try:
        source_col = source_db.collection(collection_name)
        dest_col = dest_db.collection(collection_name)
        
        # If replace mode, delete existing documents first
        if mode == 'replace':
            print(f"Deleting existing documents in destination '{collection_name}'...")
            docs = dest_col.stream()
            batch = dest_db.batch()
            count = 0
            for doc in docs:
                batch.delete(doc.reference)
                count += 1
                if count % 400 == 0:  # Batch limit is 500
                    batch.commit()
                    batch = dest_db.batch()
            if count % 400 != 0:
                batch.commit()
            print(f"Deleted {count} documents.")
        
        # Copy documents
        print(f"Copying documents from source to destination...")
        source_docs = source_col.stream()
        
        batch = dest_db.batch()
        batch_count = 0
        total_copied = 0
        
        for doc in source_docs:
            doc_data = doc.to_dict()
            dest_doc_ref = dest_col.document(doc.id)
            
            if mode == 'merge':
                batch.set(dest_doc_ref, doc_data, merge=True)
            else:  # replace
                batch.set(dest_doc_ref, doc_data)
            
            batch_count += 1
            total_copied += 1
            
            if batch_count >= 400:
                batch.commit()
                print(f"  Committed batch of {batch_count} documents...")
                batch = dest_db.batch()
                batch_count = 0
        
        if batch_count > 0:
            batch.commit()
        
        print(f"\nSUCCESS: Copied {total_copied} documents to '{collection_name}' collection.")
        
    except Exception as e:
        print(f"Error copying collection: {e}")
        import traceback
        traceback.print_exc()

def main():
    print("=" * 60)
    print("Firebase Collection Copy Tool")
    print("=" * 60)
    
    # 1. Get source database credentials
    print("\n--- SOURCE DATABASE ---")
    source_key = get_service_account_path("Enter path to SOURCE database service account key")
    
    # Initialize source database
    print("Initializing source database...")
    source_db = init_firebase_app('source', source_key)
    print("Connected to source database.")
    
    # 2. List collections in source
    print("\nFetching collections from source database...")
    collections = list_collections(source_db)
    
    if not collections:
        print("No collections found in source database.")
        return
    
    print("\nAvailable Collections:")
    for idx, col_name in enumerate(collections):
        # Try to get document count
        try:
            doc_count = len(list(source_db.collection(col_name).limit(1000).stream()))
            if doc_count == 1000:
                doc_count_str = "1000+"
            else:
                doc_count_str = str(doc_count)
        except:
            doc_count_str = "?"
        
        print(f"{idx + 1}. {col_name} ({doc_count_str} documents)")
    
    # 3. Select collection
    while True:
        try:
            selection = input("\nSelect collection number to copy: ").strip()
            if not selection:
                continue
            
            sel_idx = int(selection) - 1
            if 0 <= sel_idx < len(collections):
                selected_collection = collections[sel_idx]
                break
            else:
                print("Invalid number.")
        except ValueError:
            print("Please enter a number.")
    
    print(f"\nSelected: {selected_collection}")
    
    # 4. Get destination database credentials
    print("\n--- DESTINATION DATABASE ---")
    dest_key = get_service_account_path("Enter path to DESTINATION database service account key")
    
    # Check if it's the same as source
    if os.path.abspath(source_key) == os.path.abspath(dest_key):
        print("\nWARNING: Source and destination are the SAME database!")
        confirm = input("This will overwrite data in the same database. Continue? (yes/no): ").lower()
        if confirm != 'yes':
            print("Aborted.")
            return
    
    # Initialize destination database
    print("Initializing destination database...")
    dest_db = init_firebase_app('destination', dest_key)
    print("Connected to destination database.")
    
    # 5. Check if collection exists in destination
    dest_collections = list_collections(dest_db)
    
    copy_mode = 'merge'
    if selected_collection in dest_collections:
        print(f"\nWARNING: Collection '{selected_collection}' already exists in destination!")
        print("Options:")
        print("1. MERGE - Keep existing documents, add/update from source")
        print("2. REPLACE - Delete all existing documents, then copy from source")
        print("3. CANCEL - Abort the operation")
        
        while True:
            choice = input("Select option (1/2/3): ").strip()
            if choice == '1':
                copy_mode = 'merge'
                break
            elif choice == '2':
                copy_mode = 'replace'
                confirm = input("This will DELETE all existing documents. Are you sure? (yes/no): ").lower()
                if confirm == 'yes':
                    break
                else:
                    print("Cancelled REPLACE mode. Choose another option.")
            elif choice == '3':
                print("Operation cancelled.")
                return
            else:
                print("Invalid choice.")
    
    # 6. Perform copy
    print(f"\nStarting copy operation (Mode: {copy_mode.upper()})...")
    copy_collection(source_db, dest_db, selected_collection, mode=copy_mode)
    
    print("\n" + "=" * 60)
    print("Copy operation complete!")
    print("=" * 60)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nOperation cancelled by user.")
    except Exception as e:
        print(f"\nUnexpected error: {e}")
        import traceback
        traceback.print_exc()
