import csv
import json
import os

# Path to the CSV file
CSV_FILE_PATH = "C:\\Users\\HP\\AndroidStudioProjects\\ChatApp\\Student.csv"

# Output JSON file
OUTPUT_JSON_PATH = "student_collection_for_import.json"

def convert_csv_to_firestore_json():
    """Convert student CSV data to Firebase-compatible JSON format"""
    print(f"Reading CSV file: {CSV_FILE_PATH}")
    
    try:
        # Read the CSV file
        with open(CSV_FILE_PATH, 'r', encoding='utf-8') as csv_file:
            csv_reader = csv.DictReader(csv_file)
            rows = list(csv_reader)
            print(f"Found {len(rows)} students in CSV file")
            
            # Create a dictionary to hold the data in Firestore format
            firestore_data = {}
            
            # Process each row
            for row in rows:
                try:
                    # Get the roll number to use as document ID
                    roll_no = row['Roll No.'].strip()
                    if not roll_no:
                        print("Skipping row with empty roll number")
                        continue
                    
                    # Create a clean document object
                    student_data = {}
                    
                    # Add all fields to the document
                    for key, value in row.items():
                        # Clean keys by removing whitespace and dots
                        clean_key = key.strip().replace(".", "_").replace(" ", "_")
                        # Store the value
                        student_data[clean_key] = value.strip() if isinstance(value, str) else value
                    
                    # Add to the Firestore data dictionary
                    firestore_data[roll_no] = student_data
                    
                except Exception as e:
                    print(f"Error processing row: {e}")
            
            # Write the formatted JSON for Firestore
            with open(OUTPUT_JSON_PATH, 'w', encoding='utf-8') as json_file:
                json.dump(firestore_data, json_file, indent=2)
            
            print(f"Successfully converted {len(firestore_data)} students to JSON format")
            print(f"Output saved to: {os.path.abspath(OUTPUT_JSON_PATH)}")
            print("\nYou can now import this JSON file into Firestore using these steps:")
            print("1. Go to the Firebase Console > Your Project > Firestore Database")
            print("2. Click the three-dot menu button > Import/Export > Import")
            print("3. Select the JSON file just created")
            print("4. Set collection path to 'Student'")
            print("5. Click 'Import'")
    
    except Exception as e:
        print(f"Error reading CSV file: {e}")

if __name__ == "__main__":
    convert_csv_to_firestore_json() 