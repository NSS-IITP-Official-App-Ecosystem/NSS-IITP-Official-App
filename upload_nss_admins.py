import firebase_admin
from firebase_admin import credentials, firestore
import csv
import io

# Service account key as a dict
service_account_key = {
  "type": "service_account",
  "project_id": "chatapp-24fae",
  "private_key_id": "dcaf0935a2a5ed555fd65f326bb833fb3ea9d315",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDUlsU6C2wDahUx\nY1uKUsEcm293kVUTQiH148c9xcw9BiIRpXpF0rtqayLuAT9AIFWMflIZzUyXZbuc\nVTbdFi0JharXkaAkZ6GP6JbDubo1ci6tzAq97npOKz1y3xs/P/mF1RYIs+JUMiod\nEpd2kbkOOfbPQw+7q1tiPurS6thPUPrmJBrSHtEuO7ZNepWy4kD3VO//EPCbzcYX\nNuj6sKWeuYryvS8Zo2p7YctweIw8v48WYwpYIGGRMBTpVhOVwFrTQKRY21+gwLZ0\nNE3njkktgNbLwfslVeAzuQ1DEiySLUzNnogWkBt25F+Wd/wHcf1T10bNT5WgCjmK\n/YG2TJh3AgMBAAECggEAHMVY5N2l3SJuwtPdEC/Z9cqtf8OZ/XS+Tt5oWRpbZUje\ntH+uOCPofGMKg6aEjmOx4EU6TEamHnlEtkgsGCjHqJnAANOzFio2SdkjClMo+dG9\nsoTCUWkkgU7FzrWWJO2+EdhAVqYts9JkM7IP8JqRLnU2y18akn4C7J1yP2BMLROb\nzFUQkhkHttV+734VGk5Cs82h7P1UX6dEknlUojluv//IlqjMURDQnF8H0tvswWC9\nKlJMNhIBSPhqKYaz0W7FVL1VflFy914yzYI1r9yfGLwucwTUmbchtENzfDQwifj0\nY5t/3As4D2r4vfgFIWYMVMm9kwDiG4ADcayEt8a2RQKBgQD6N2+ksYg1lRFAThUO\n3IW7UTXd7KnlfGfxmPcISTgwuXsapUdtnxQ3VZnX+liltIMsQRqmIvA/RMfOFaRp\nm7Hr9uvSO5pe0Q38F7Q+BRsTiVnO9TjxBjSAxQs4l7FbhbeCASHK2xfl4CUdNYHM\nc5DbMiy2svhMupVj2YAgwaWuswKBgQDZgK/YnhEuBiV1uUm26W3paXEXrqUPOL/y\nuFp8er6UdYdjJIkb7LzIo01Og0dpHSeLtzzNUOeXUvQVwvjJa0h/acxg+0T6YVJK\nulVW15MA1zBGn6TyUqEuJuDAk4v5YUYsG8M5Nj48LhqBQihGT0Cpv7QMYTcVvXaY\n1JLif6ERLQKBgQCQ/X6cGKOtKOwOLzDUo8R6ftyP9IuehzBUNycujf8ZfOFw10VE\nqGG2jsvO8ypbGvbHthITIzvD7b86FCBpcebvrO2JrTAOsWVBJxsytRKzh2ubGU4d\nnFLgK04NRRlox1tG4hEK01pNwky/WDoWlwDhg3mVhK+NBrF6YpZ0ZMH1MQKBgQDJ\nxYzb2j87v+3uWRZePgx9tRh6DMUI6u9+frFKux+jX6haO+GJ60NyqbOkuzp689Xk\n0fbwzTYa9pSCv53GVOUrdf1olpD16WTK+DME3fV8mSQq0BzXw/nMc7qaTAZVMX1E\nm/iuUXjIoUhrUvFD0G+/SZ0Awwqz+1BTFRiyUA4efQKBgQDPJdF/RJw2SGQDqOWg\nFcxIt3aHp11tOGm+oUNKUPmPFmrUSa8J9sLRUQx4pLiE4tfMRjyiTTP2Blp4TIE/\nPl+A8GLzmkxFHEUHPUuyYYV6dr01h3Y9fnc3g4vFxdPO0Sjt03Up+djRYJfZppAR\nXNgknaMCC68RC4fq/p/h54z+nA==\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-fbsvc@chatapp-24fae.iam.gserviceaccount.com",
  "client_id": "110038306197171695004",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40chatapp-24fae.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
}

# Initialize Firebase Admin SDK using the dict
try:
    cred = credentials.Certificate(service_account_key)
    if not firebase_admin._apps:
        firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Successfully connected to Firebase.")
except Exception as e:
    print(f"Error connecting to Firebase: {e}")
    print("Please ensure your service account key is correct.")
    exit()

# Updated CSV data
csv_data = """Roll_Number,Name,Contact_Number,College_Email,Personal_Email,Year,image_url,Academic_Group,NSS_Group,Gender,Teaching_wing
2401ME27,VIRAJ MANDAVKAR,8104347052,mandavkar_2401me27@iitp.ac.in,mandavkarviraj03@gmail.com,2,https://drive.google.com/file/d/1j0mdtZNG8dxYf6-7MyYuwv1SeLJjEiEM/view?usp=drivesdk,21,15,Male,Yes
2401ME71,Harshit Sayal,7011400287,harshit_2401me71@iitp.ac.in,harshujm276@gmail.com,2,https://drive.google.com/file/d/1ogVc3Ow5FJZz2wLXxq7N0lEKxhyjANDK/view?usp=drivesdk,21,2,Male,Yes
2402MT05,Dikshit Verma ,8199031106,dikshit_2402mt05@iitp.ac.in,dikshitv031106@gmail.com,2,https://drive.google.com/file/d/1ML-1996kzlt9z9Yma7tsP15OhJdpE0HB/view?usp=drivesdk,4,32,Male,No
2401CS45,DARLA SRAVAN KUMAR,9494559819,darla_2401cs45@iitp.ac.in,dsravankumar2007@gmail.com,2,https://drive.google.com/drive/u/0/folders/1NuU_z-RN6LOekdJ-FplA0stPygCcEpAC,14,26,Male,No
2401MC20,RANVEER GUPTA,9308512722,ranveer_2401mc20@iitp.ac.in,ranveerguptavk@gmail.com,2,https://drive.google.com/file/d/1PKhFbT1ys8wgW3Au4V07YM6B0aaavQ59/view?usp=drivesdk,20,30,Male,Yes
2401CE10,Shivam kumar,9939267750,shivam_2401ce10@iitp.ac.in,shivamkumarbaletha@gmail.com,2,https://drive.google.com/file/d/1-JflCgmy4UQSkqBBCX67b5mk89Tcl_dc/view?usp=drivesdk,11,31,Male,Yes
2403EE05,B.JAWAHAR REDDY,8309122630,bhureddy_2403ee05@iitp.ac.in,itsmejawahars54321@gmail.com,2,https://drive.google.com/file/d/1XdGvec51tA670OX_Dbt2O9KO9SHJEcKN/view?usp=drivesdk,5,24,Male,No
2401ME61,Aditya Raj,8002950538,aditya_2401me61@iitp.ac.in,adityaraj51255@gmail.com,2,https://drive.google.com/file/d/1Ngze0X9e-IxtwjOfUe3zy_ujKj6AS_vh/view?usp=drivesdk,22,25,Male,No
Pranay Dev,Pranay Dev,+91 8789478742,pranay_2401cb45@iitp.ac.in,devpranay878947@gmail.com,2,https://drive.google.com/file/d/12E_9YFomDAy86z14NHUIpF2DasfZ5Q9e/view,8,10,Male,No
2403ME04,Patil Arya Nitin ,7709152843,patil_2403me04@iitp.ac.in,patilarya15aug@gmail.com,2,https://drive.google.com/file/d/1BiA4X6RyiTqTC1IIp3mPcFTWUmqR1U4b/view?usp=drivesdk,5,31,Female,No
2401ME49,Riju Mondal,7908986725,riju_2401me49@iitp.ac.in,riju6264@gmail.com,2,https://drive.google.com/file/d/195_r2aT30uyU4z6ju2D4WKLSIgj1jX_i/view?usp=drivesdk,23,11,Male,No
2402MT08,Piyush Kumar ,7905723471,Piyush_2402mt08@iitp.ac.in,Piyush1324kumar@gmail.com,2,https://drive.google.com/drive/folders/1zueFyCZ4onrpEX_VsIpqQhbewKRq7w4t,4,8,Male,No
2401ME59,Yash Mayur Modi,6352020828,yash_2401me59@iitp.ac.in,modisuvas27@gmail.com,2,https://drive.google.com/file/d/1EePeiiTKC1KbQ_JA-yAjMgGJ5tgQ58-6/view?usp=sharing,21,16,Male,Yes
2401ME60,Amit Kumar Meena ,9352835543,amit_2401me60@iitp.ac.in,amitkm9680@gmail.com,2,https://drive.google.com/drive/folders/1BIsXuPqeJjqUUoEnh0RNSnvUzNEw9AIK,23,32,Male,Yes
2401EC17,Sai Gayathri ,7569818259,nimmagadda_2401ec17@iitp.ac.in,4gayathri.chinni@gmail.com,2,https://drive.google.com/file/d/1R2wMqIQHHZwXsW7zurSNp9yBP80GALDy/view?usp=drivesdk,17,30,Female,No
2401CE27,Kumari Jagrati,6395326149,kumari_2401ce27@iitp.ac.in,jagratikumari657@gmail.com,2,https://drive.google.com/file/d/18dfdv1_4eykAhzhW05-kGCBern6Pfp5O/view?usp=sharing,10,24,Female,Yes
2401ME13,Harsh Verma,9045120527,harsh_2401me13@iitp.ac.in,harshdps27@gmail.com,2,https://drive.google.com/file/d/1Uo0Fm3JrShuxizcX1e4K5nRnlCC1Hz91/view?usp=sharing,21,28,Male,Yes
2403CT04,Priyanshu Purohit ,7340710615,priyanshu_2403ct04@iitp.ac.in ,priyanshupurohit2409@gmail.com ,2,https://drive.google.com/file/d/17GI0qL5k1Y8J2XrUaVi8K3aTHzwT3a0V/view?usp=drivesdk,4,24,Male,No
2402PC01,Varada Anirudh ,8247533648,varada_2402pc01@iitp.ac.in,varadaanirudh21@gmail.com,2,https://drive.google.com/drive/folders/1rwfXuFnxbDB5KIX_yglXdHCZQIcQz3Oj,3,7,Male,No
2401ME42,VIJAY MANOJ PATHELLA ,9121671830,vijaymanoj_2401me42@iitp.ac.in,vm4390187@gmail.com,2,https://drive.google.com/file/d/1EVPJSl3FinKVHfIbhZRHA3QtEShlKONC/view?usp=drivesdk,21,5,Male,No
2402ST06,Anshika Garg,7696216163,anshika_2402st06@iitp.ac.in,anshika5079garg@gmail.com,2,https://drive.google.com/file/d/1RsmXCUCLjIcVfwLX_zqxHBFGtuz5C7dm/view?usp=sharing,2,17,Female,Yes
2401MC26,Anish Kumar ,7367072522,anish_2401mc26@iitp.ac.in,anish582006@gmail.com,2,https://drive.google.com/file/d/1ttl9lBw7GP8AFx3W8I3KSJNxPdg8-FrM/view?usp=drivesdk,20,14,Male,Yes
2401CE23,HIMANSHI,8278922113,himanshi_2401ce23@gmail.com,himanshi2282@gmail.com,2,https://drive.google.com/file/d/1LDtt79i8-2WU6_WhOR-Pm7W3jHxM_XFc/view?usp=drivesdk,11,13,Female,No
2401CT02,Mehal Srivastava ,9535896013,mehal_2401ct02@iitp.ac.in,mehalsrivastava@gmail.com,2,https://drive.google.com/drive/folders/1zvRBOy0hGjzmS4yMm7Tqi8vX7tiSnA4D,9,9,Female,Yes
2401CT09,Samiksha katariya ,9828861697,samiksha_2401ct09@iitp.ac in,samikshakatariya8@gmail.com,2,https://drive.google.com/file/d/17qk5R5DevCyiOWD29kVlFccNChvmu9No/view?usp=drivesdk,10,11,Female,No
2403MM03,Tharun Thirupathi ,7780390445,Thirupathi_2403mm03@iitp.ac.in,Nanikanna836@gmail.com,2,https://drive.google.com/file/d/19cxLgP82Yp-oiFYXkCCLmlVj8eAXz6BV/view?usp=drive_link,6,5,Male,No
2401MM32,Parnava Maitra,9593396993,parnava_2401mm32@iitp.ac.in,Parnava.tan@gmail.com,2,https://drive.google.com/file/d/1cmGz9dADmlrigl8LZeuk5goS-6at5WLc/view?usp=drivesdk,24,22,Male,Yes
2401EC11,Tanishk Raj ,7033143473,tanishk_2401ec11@iitp.ac.in,1512tanishkraj@gmail.com,2,https://drive.google.com/file/d/1LdqSaMtImgMr8KWbQFnlg7tYW0RfZ8Ua/view?usp=drivesdk,17,7,Male,Yes
2401CT30,Kshitij Singh ,7088657157,kshitij_2401ct30@iitp.ac.in,kshitij0589@gmail.com,2,https://drive.google.com/file/d/1kEWVPN2DYlsiFjOEHPEhMi5JE3mtr1_u/view?usp=drivesdk,9,18,Male,No
2401MM10,SHANKHADEEP DAS,8159826878,shankhadeep_2401mm10@iitp.ac.in,shankhadeepd4@gmail.com,2,https://drive.google.com/file/d/1Y02CuplN-AUJ7dW-m6uu2vFQk_LaBygX/view?usp=drivesdk,24,6,Male,No
2403CT02,Charu Garg,8595793227,charu_2403ct02@iitp.ac.in,charugarg.0006@gmail.com,2,https://drive.google.com/drive/folders/1XzSbtqZiRiP3n5H2cY6jrNdxbLwN6IVe?usp=sharing,4,11,Female,No
2402PC02,Neha Sree Kuppam,9573692502,neha_2402pc02@iitp.ac.in,sree11320neha@gmail.com,2,https://drive.google.com/drive/folders/194ETHnFLfmQ9ww09OkGBi-wJuOnqXGwW,3,29,Female,No
2401EC19,Ayantika Halder ,8584014805,ayantika_2401ec19@iitp.ac.in,ayantikaiitp@gmail.com,2,https://photos.app.goo.gl/UYsVnE2aaY5hLf6i8,18,23,Female,No
2401CT04,AYUSH SEN,7000257907,ayush_2401ct04@iitp.ac.in,ayushgraphix@gmail.com,2,https://drive.google.com/file/d/1V7zbrEpEJpInhPAYxLZFkP0hYkWgSTTb/view?usp=drivesdk,9,8,Male,No
2401CE14,Shreya Yadav ,8467935303,shreya_2401ce14@iitp.ac.in,shreyayadav172008@gmail.com,2,https://drive.google.com/file/d/1Ry_jMHIJsOnXWIGPTiBZDekKUvtpbHOf/view?usp=drivesdk,11,11,Female,No
2402GT05,Shaurya Singh ,9234763129,shaurya_2402gt05@iitp.ac.in,shauryas8092@gmail.com,2,https://drive.google.com/file/d/1wGpcJxb9cK1cFpw6tKqy4tDae-8mSjdt/view?usp=drivesdk,2,20,Female,No
2401me14,B.praneeth,9441592387,bantumalli_2401me14@iitp.ac.in,bantumallipraneeth1035@gmail.com,2,https://photos.app.goo.gl/tyG9omrar7MzJTvF8,23,28,Male,No
2401CB06 ,Abhishek kumar ,79090 88743 ,Abhishek_2401cb06@iitp.ac.in,ak16199120@gmail.com ,2,,8,31,Male,No
2401MC34,Kingshuk Haldar,8100518784,kingshuk_2401mc34@iitp.ac.in,khaldarofficial106@gmail.com,2,https://drive.google.com/file/d/18H3oPwI9gvE5pz-SjNABw2xWr11cBX2I/view?usp=sharing,20,9,Male,Yes
2401CB23,DEVYANSH PANDEY,9198333486,devyansh_2401cb23@iitp.ac.in,devyanshp0602@gmail.com,2,https://drive.google.com/file/d/1aC2VfC0uWH9BZNZHeaXgvF3qGAMa6HE4/view?usp=drivesdk,7,32,Male,Yes
2302MT05,PT BHOODEV BHUSHAN MISHRA ,9129359974,bhoodev_2302mt05@iitp.ac.in,bhoodevmishra2005@gmail.com,3,,1,1,Male,No
2401ME52,Ayushkar Nath,6033055994,ayushkar_2401me52@iitp.ac.in,nathayushkar07@gmail.com,2,https://drive.google.com/file/d/1BOb_SPow2CgmJTGnHzJ28KIg57y3BpbN/view?usp=drivesdk,22,20,Male,No
2401CE51,MITALI AWASTHI,9555535910,mitali_2401ce51@iitp.ac.in,mitaliawasthi06@gmail.com,2,https://drive.google.com/file/d/12g5v3dtPgDoeHsI7l0I46RR5320hp0zk/view?usp=sharing,10,25,Female,Yes
2301CS44,Sai Vardhan,6300232527,vardhan_2301cs44@iitp.ac.in,saivardhansunny2103@gmail.com,2,https://drive.google.com/file/d/174BvpxGi-cDIEu-dt51IomuCo6zvujnE/view?usp=drivesdk,1,32,Male,No
2401EE10,Romir Zadoo,7303770116,romir_2401ee10@iitp.ac.in,romirzadoo1@gmail.com,2,https://drive.google.com/file/d/1rCN3q-4eSCMdlwuFegoey3Zg1RlSIy9O/view?usp=drive_link,15,18,Male,No
2301MC51,Aditya Gupta,9410408989,aditya_2301mc51@iitp.ac.in,aditya308989@gmail.com,3,https://drive.google.com/file/d/1ZnPNJcHl-3aIxaKhsK47E6jSFYAND0Pp/view?usp=drive_link,0,0,Male,Yes
"""

# Use io.StringIO to treat the string data as a file
csv_file = io.StringIO(csv_data)

# Parse the CSV data
reader = csv.DictReader(csv_file)

collection_name = 'NSS_ADMINS'

# Loop through each row in the CSV
for row in reader:
    try:
        roll_number = row.get('Roll_Number', '').strip()
        if not roll_number:
            print(f"Skipping row due to empty Roll_Number: {row}")
            continue

        # Data to be added to Firestore with correct types
        data_to_upload = {}
        for key, value in row.items():
            stripped_key = key.strip()
            stripped_value = value.strip() if value else ""

            if stripped_key in ['Year', 'Academic_Group', 'NSS_Group']:
                try:
                    # Convert to number, default to 0 if empty/invalid
                    data_to_upload[stripped_key] = int(stripped_value) if stripped_value else 0
                except (ValueError, TypeError):
                    data_to_upload[stripped_key] = 0
            elif stripped_key == 'Teaching_wing':
                # Convert to boolean
                data_to_upload[stripped_key] = stripped_value.lower() == 'yes'
            else:
                data_to_upload[stripped_key] = stripped_value

        db.collection(collection_name).document(roll_number).set(data_to_upload)
        print(f"Successfully uploaded data for Roll Number: {roll_number}")

    except Exception as e:
        print(f"Error uploading data for row: {row}")
        print(f"Error: {e}")

print(f"\nData upload to '{collection_name}' collection complete.") 