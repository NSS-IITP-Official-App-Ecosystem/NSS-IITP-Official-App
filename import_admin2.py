import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import csv
import time
import json
import os

# Save the service account credentials to a file
SERVICE_ACCOUNT_JSON = {
  "type": "service_account",
  "project_id": "chatapp-24fae",
  "private_key_id": "ff505f9f48a021ec09b58b591105c7629c3345ae",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCgjAL2ToV14uXr\nbAAqj56t3y5hzLpocbq4ZCWW6TA5j6aKetQCrvjXV2DDBcbtUie0ZxoGgqQq3Apn\nQEDW1g2RDEe6Vt+clyR18q8GceBl/Mjbn40Dwbf2udWJOMWIb6AGKCADorGlBIkp\nv6uBya/1V4UGoM7NzaTNYdK1lBaBvS8NmZg60x+VSEByumBMo/xIe+VWgvWZnwyT\nK4W7piZvYzsDj1fwvWVkWLuChrHIokFJgXLg5VTpzz5PqlGXEPo1+uK8eY3vUBBB\nWJC0C0ViFMqBKIDmns33cJk4ldouXMB5tB86SW3y6eXLk1tpyaPKVF9xJphjLMoU\nwLUwwX4HAgMBAAECggEABSGEZLfMtyBJxdTdSn7tjNQBqPmAiZ783zDALMsGEgjf\nwzQ6u105MbSmsITqPyJth2v2HnglZANFR9h7WgJcS6LvVeq52cjQzv4zDssr50aF\natN2D8UsqnN0+h443evnWtsEbulPsRzsf0uTYSH/gCFMbJJOGbC1UCUMtWXmtD25\nE/jkuCtMdraHXoX86dEqku+5MIi8rFQ+xjHUGgRwPnmxyzLT9OfPBxn+TOOgCdfp\nVld4w1Nov2byVT9ex2loVfsHFiHI1liFqCWVvo0RZo1PExLIzLZhPFYaQYBQ9XFK\nFGqWyT9JQlCRuGZZccPRZXtFXlBaE7sSGLihKPonqQKBgQDeikWIDpehDlMJM1qi\nPNQnFpdCA8a1PkIVCWYn4diqhdZtm2T2Ic879muZHgawh7aFPBySVvsHodXCj13b\n7XUbcKzqgiAVNDq7VWH04TPTdDhULeoQoVYse6Zg4g+EUMYG6T9FzwxAnBnjwheV\nYgaI4I7tf1ev2GYoSj+AtaHnPwKBgQC4r5PHr4+Rl8PEC1La/eVrNp+ga19lca3e\nACIZXNKlilgm09Su9vzWRC8WLzgVm54pKw6S1BWux08iYYyvgGVzKlcRNBIYlcLE\nvKH7NBgrRLzKXoF+kkNxTO+7WbGbn9W6o6AH8gtO/+5/sCDrHJHEhlIk7rnG2+pI\nGnknFZO/OQKBgAopxG0nRXN8hPxJlR0NrB6w4G2KYJvMYRY23Kx7mvqGrDlLPHwl\nJSK26UkP3vxW5lYFXcStJj+9+1YsV/wARzXemncHRFHTlc8SYivd0REaIKxvgF4M\nEDRt3TZVfKHtUm3kXxhgEU4uEtyc85jF5kiAAOcFxspoFppGccn+AYvfAoGBAKxP\n1Jeoq1Dxwks1LXb5EIX5jvGDfJwPdmL8yNPVEQw2iYygc31TIA9hynTuVWuDqAtN\nCunfSuOpPaIcVcChEQZkaJu6c2/QusoIHTFdJ3enSGWfyz4mhRo+6CVqyBUlCap7\nY6JGKFrq2sDzuaLjIvWzRQ0EVvy8zle7q3HV5eWxAoGAHMYPHAa12juuQo2FULfa\nGfjNaUpv40KvNZwl8fsbMAdy54YWEV7Ulz3/q8CL64LUVHv3/RGbyLdm5hcmlcq3\nj6oLaNj2rEIOQwz3CTEI+Dx3UyuIZMQfV3ZONeVBihfgby5A1oC949dmnw1uMpd6\nYNhfGa8kpOBTsw3qcIHR/wM=\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-fbsvc@chatapp-24fae.iam.gserviceaccount.com",
  "client_id": "110038306197171695004",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40chatapp-24fae.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
}

# Save credentials to a temp file
TEMP_CRED_FILE = "temp_service_account.json"
with open(TEMP_CRED_FILE, 'w') as f:
    json.dump(SERVICE_ACCOUNT_JSON, f)

# Path to the CSV file for Admin2
CSV_FILE_PATH = "C:\\Users\\HP\\AndroidStudioProjects\\ChatApp\\Database.TWAPP - Admin_ 2 (Third_Year).csv"

def import_admin2_to_firestore():
    """Import Admin2 data from CSV to Firestore"""
    print("Starting import of Admin2 data to Firestore...")
    
    # Initialize Firebase Admin SDK
    try:
        cred = credentials.Certificate(TEMP_CRED_FILE)
        firebase_admin.initialize_app(cred, {
            'projectId': 'chatapp-24fae',
        })
        db = firestore.client()
        print("Connected to Firestore database")
    except Exception as e:
        print(f"Error connecting to Firestore: {e}")
        return
    
    # Read the CSV file
    try:
        with open(CSV_FILE_PATH, 'r', encoding='utf-8') as csv_file:
            csv_reader = csv.DictReader(csv_file)
            rows = list(csv_reader)
            print(f"Found {len(rows)} Admin2 records in CSV file")
            
            # Process in batches
            batch_size = 10  # Smaller batch size as there are fewer records
            total_batches = (len(rows) + batch_size - 1) // batch_size
            success_count = 0
            
            for batch_index in range(total_batches):
                start_idx = batch_index * batch_size
                end_idx = min(start_idx + batch_size, len(rows))
                batch_rows = rows[start_idx:end_idx]
                
                # Create a batch
                batch = db.batch()
                
                print(f"Processing batch {batch_index + 1}/{total_batches} ({len(batch_rows)} Admin2 records)")
                
                for row in batch_rows:
                    try:
                        # Clean up data
                        admin_data = {}
                        roll_no = row.get('Roll No', '').strip()
                        
                        # Skip empty roll numbers
                        if not roll_no:
                            print("Skipping row with empty roll number")
                            continue
                        
                        # Add all fields to the document
                        for key, value in row.items():
                            # Clean keys by removing whitespace and dots
                            clean_key = key.strip().replace(".", "_").replace(" ", "_")
                            # Store the value
                            admin_data[clean_key] = value.strip() if isinstance(value, str) else value
                        
                        # Add user type field (Admin2)
                        admin_data["userType"] = "Admin2"
                        
                        # Create a reference to the Admin2 document
                        admin_ref = db.collection('Admin2').document(roll_no)
                        
                        # Add to batch
                        batch.set(admin_ref, admin_data)
                    except Exception as e:
                        print(f"Error processing Admin2 record {roll_no}: {e}")
                
                # Commit the batch
                try:
                    batch.commit()
                    success_count += len(batch_rows)
                    print(f"Successfully committed batch {batch_index + 1}")
                    # Small delay to avoid hitting Firestore limits
                    time.sleep(1)
                except Exception as e:
                    print(f"Error committing batch {batch_index + 1}: {e}")
            
            print(f"Import completed. Successfully imported {success_count} out of {len(rows)} Admin2 records.")
    
    except Exception as e:
        print(f"Error reading CSV file: {e}")
    
    # Clean up temp credential file
    if os.path.exists(TEMP_CRED_FILE):
        os.remove(TEMP_CRED_FILE)
        print(f"Removed temporary credential file")

if __name__ == "__main__":
    import_admin2_to_firestore() 