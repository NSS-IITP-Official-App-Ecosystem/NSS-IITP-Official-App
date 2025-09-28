import json
import firebase_admin
from firebase_admin import credentials, firestore
import os

def init_firebase():
    # Check if already initialized
    if not firebase_admin._apps:
        # Try different possible locations for the service account file
        possible_paths = [
            'serviceAccountKey.json',  # Direct in root
            'app/src/main/assets/serviceAccountKey.json',
            'app/src/main/assets/service-account.json',
            'chatapp-24fae-4375b955e5f7.json',
            'service-account.json'
        ]
        
        cred = None
        for path in possible_paths:
            if os.path.exists(path):
                print(f"Using service account from: {path}")
                try:
                    # Initialize with database URL
                    cred = credentials.Certificate(path)
                    firebase_admin.initialize_app(cred, {
                        'projectId': 'chatapp-24fae',
                        'databaseURL': 'https://chatapp-24fae.firebaseio.com'
                    })
                    print("Successfully initialized Firebase Admin SDK")
                    break
                except Exception as e:
                    print(f"Failed to initialize with {path}: {str(e)}")
                    continue
                
        if cred is None:
            raise FileNotFoundError("Could not find valid service account file. Please ensure it exists in one of these locations: " + ", ".join(possible_paths))
            
    return firestore.client()

def process_questions_section(section_data, section_id):
    """Process sections that have questions"""
    questions = []
    if 'questions' in section_data:
        for q_id, q_data in section_data['questions'].items():
            question = {
                'id': q_id,
                'section_id': section_id,
                'sub_section_id': None,  # General section has no sub-sections
                'question': q_data['question'],
                'answer_type': 'bullet_points' if isinstance(q_data['answer'].get('points', []), list) else 'text'
            }
            
            # Handle different answer formats
            if 'points' in q_data['answer']:
                question['answer'] = q_data['answer']['points']
            elif 'wings' in q_data['answer']:
                question['answer_type'] = 'wings'
                question['answer'] = q_data['answer']['wings']
            else:
                question['answer'] = q_data['answer']
                
            questions.append(question)
    return questions

def process_teaching_technical_section(section_data, section_id):
    """Process teaching_technical section with sub-sections and proper questions"""
    
    # Define sub-sections
    sub_sections = [
        {
            'id': 'general_info',
            'title': 'General Information',
            'description': 'Basic information about the Teaching & Technical Wing'
        },
        {
            'id': 'committees',
            'title': 'Committees',
            'description': 'Information about different committees and their functions'
        },
        {
            'id': 'schools',
            'title': 'Teaching Locations',
            'description': 'Details about different schools and teaching locations'
        },
        {
            'id': 'policies',
            'title': 'Policies & Guidelines',
            'description': 'Important policies, rules, and guidelines for volunteers'
        }
    ]
    
    # Define questions for each sub-section
    questions = []
    
    # General Information sub-section
    questions.extend([
        {
            'id': 'sub_divisions',
            'section_id': section_id,
            'sub_section_id': 'general_info',
            'question': 'What are the various sub-divisions existing in the Teaching Wing and what are their functions?',
            'answer_type': 'text',
            'answer': 'There are a total of three sub-divisions existing in the Teaching Wing:\n\n1. Core teaching Committee: Responsible for all activities related to teaching. Teaching activities can range from teaching in government schools, conducting events in various schools, technical teaching in schools and tutorial blocks to even online teaching.\n\n2. Events Committee: All the events that happen in the institution related to social activity are managed by the Events Committee. It includes Event management, Logistics, seating arrangement, event decision and execution.\n\n3. Social Media & Photography Committee: Graphic designing for posts and video editing for reels for the official social media The Teaching and Technical Wing. Also responsible for handling the photography and videography at the events.'
        },
        {
            'id': 'committee_selection',
            'section_id': section_id,
            'sub_section_id': 'general_info',
            'question': 'How can I get selected for a particular committee in the Teaching and Technical Wing?',
            'answer_type': 'text',
            'answer': 'Volunteers for a particular committee will be selected based on their interview score and skill set by the sub-coordinators and co-ordinators of the wing. However, volunteers can inform about their particular preference if any during the time of the interview.'
        },
        {
            'id': 'teaching_opportunity',
            'section_id': section_id,
            'sub_section_id': 'general_info',
            'question': 'If I am part of the Events or Social Media & Photography Committee, will I still have the opportunity to teach students?',
            'answer_type': 'text',
            'answer': 'Teaching students is the most touching and fulfilling part of being in the Teaching and Technical Wing. We believe every volunteer deserves to experience it, so no matter which committee you\'re in, you\'ll have the opportunity to take part in teaching activities.'
        }
    ])
    
    # Committees sub-section
    questions.extend([
        {
            'id': 'committee_change',
            'section_id': section_id,
            'sub_section_id': 'committees',
            'question': 'If I am initially allotted to a particular committee after the interview, will I have an opportunity to change it later?',
            'answer_type': 'text',
            'answer': 'The initial committee allotted might change upon vacancies and as per your interest but there is no guarantee to it, moreover the initial allotment will be done on the basis of interests.'
        },
        {
            'id': 'events_committee',
            'section_id': section_id,
            'sub_section_id': 'committees',
            'question': 'Tell me about the Events Committee',
            'answer_type': 'bullet_points',
            'answer': [
                'Sub-coordinators: Yash Mayur Modi, Devyansh Pandey, Harsh Verma',
                'Responsibilities:',
                '• Event planning and execution',
                '• Logistics management',
                '• Seating arrangements',
                '• Event documentation',
                '• Crowd control',
                'Workload:',
                '• 2-4 events per month',
                '• Reduced teaching load',
                '• Fair distribution of work'
            ]
        },
        {
            'id': 'social_media_committee',
            'section_id': section_id,
            'sub_section_id': 'committees',
            'question': 'Tell me about the Social Media Committee',
            'answer_type': 'bullet_points',
            'answer': [
                'Sub-coordinators: Jagrati Kumari, Anish Kumar, Mitali Awasthi, Amit Kumar Meena',
                'Activities:',
                '• Graphic designing (Canva/Figma)',
                '• Video editing',
                '• Photography at events',
                '• Videography',
                '• Content creation',
                '• Platform management',
                'Workload:',
                '• 1 task per week (max 2 in extreme cases)',
                '• Reduced teaching load'
            ]
        }
    ])
    
    # Schools sub-section
    questions.extend([
        {
            'id': 'government_schools',
            'section_id': section_id,
            'sub_section_id': 'schools',
            'question': 'Which government schools are covered?',
            'answer_type': 'bullet_points',
            'answer': ['Raghopur', 'Amhara School', 'TPS School']
        },
        {
            'id': 'foundation_academy',
            'section_id': section_id,
            'sub_section_id': 'schools',
            'question': 'Tell me about Foundation Academy',
            'answer_type': 'bullet_points',
            'answer': [
                'Private CBSE school inside IIT Patna campus',
                'Activity-based teaching',
                'Science and Mathematics focus',
                'Python programming (Classes 8-10)',
                'PPT and simulation-based teaching',
                'Session timing: 2-3 days/week including Saturday',
                '7-8 minutes by cycle from CV Raman hostel'
            ]
        },
        {
            'id': 'tutorial_block',
            'section_id': section_id,
            'sub_section_id': 'schools',
            'question': 'Tell me about Tutorial Block',
            'answer_type': 'bullet_points',
            'answer': [
                'Technical Education (Classes 6-10):',
                '• Online form filling',
                '• Online examinations',
                '• Cyber security',
                '• Menstrual hygiene awareness',
                '• Digital safety',
                '• Job opportunities',
                'Academic Support (Classes 11-12):',
                '• Board exam preparation',
                '• Practice questions',
                'Timings:',
                '• Saturday: 1:45 PM – 5:15 PM',
                '• Sunday: 9:45 AM – 1:15 PM',
                'Students: Children of campus support staff',
                'Volunteer Responsibilities:',
                '• Student pickup/drop at Gate 1',
                '• Maintain discipline',
                '• Monitor campus premises',
                '• Support teaching team'
            ]
        }
    ])
    
    # Policies sub-section
    questions.extend([
        {
            'id': 'hours_requirement',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'How many hours will I obtain from the Teaching Wing? How many hours am I required to complete in the first and second semesters?',
            'answer_type': 'text',
            'answer': 'To be decided'
        },
        {
            'id': 'incomplete_hours',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'What will happen if I am unable to complete the required number of hours? Can I complete my remaining hours from other NSS activities?',
            'answer_type': 'text',
            'answer': 'If you are unable to complete the required number of hours in the Teaching and Technical Wing, you will not be considered to have passed NSS. You must complete 80 hours (or the final decided number) over the course of two semesters.'
        },
        {
            'id': 'leave_semester',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'Can I choose to leave the Teaching and Technical Wing at the end of the first semester?',
            'answer_type': 'text',
            'answer': 'Leaving the Teaching and Technical Wing at the end of the first semester is allowed, but only if you have completed 40 hours. If your total is less than 40 hours and you still wish to leave, you must complete the remaining hours in the second semester through teaching activities, even if you are no longer part of the Teaching Wing.'
        },
        {
            'id': 'hours_monitoring',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'Will there be a system in place for volunteers to monitor their hours throughout the semester?',
            'answer_type': 'text',
            'answer': 'Volunteers will be able to view the points they have earned through the Teaching and Technical Wing once or twice every month. Final hours will be displayed only after the semester ends.'
        },
        {
            'id': 'absence_information',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'If I am unable to attend a class or event due to illness or a personal reason, whom should I inform? Will I have an opportunity to make up for my absence?',
            'answer_type': 'text',
            'answer': 'If a volunteer is unable to attend a class or event due to illness or a personal reason, they must inform the Teaching and Technical Wing in advance by sending an email. All volunteers will have sufficient opportunities throughout the semester to make up for any absences. It is the responsibility of the wing to ensure that such opportunities are provided.'
        },
        {
            'id': 'tshirt_policy',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'Is it compulsory to buy the Teaching and Technical Wing T-shirt? Am I required to wear it at all times?',
            'answer_type': 'text',
            'answer': 'Yes, it is mandatory for every volunteer to purchase the T-shirt, as it is compulsory for volunteers from all NSS wings to buy their respective T-shirts. Wearing the T-shirt will be required while taking classes or attending events.'
        },
        {
            'id': 'transportation',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'Will there be a transportation facility available for volunteers? Will NSS provide compensation for transportation costs?',
            'answer_type': 'text',
            'answer': 'Transportation facilities will not be available for volunteers. Since the schools are located nearby, volunteers are encouraged to use their own bicycles. Please note that NSS will not reimburse any transportation expenses.'
        },
        {
            'id': 'emergency_contact',
            'section_id': section_id,
            'sub_section_id': 'policies',
            'question': 'In case of an emergency or any complaints/questions, whom should I contact?',
            'answer_type': 'text',
            'answer': 'In case of an emergency or if you have any questions, you should first contact the concerned POC. If you do not receive a response, you may reach out to any sub-coordinator of your committee. If the issue remains unresolved, you can then contact the head sub-coordinator. Finally, if you still have any grievances or complaints, feel free to contact the coordinators. Remember, we are always here to support and help you!'
        }
    ])
    
    return sub_sections, questions

def process_hierarchy_section(section_data, section_id):
    """Process hierarchy section with sub-sections"""
    
    # Define sub-sections
    sub_sections = [
        {
            'id': 'leadership',
            'title': 'Leadership',
            'description': 'PIC and General Secretary information'
        },
        {
            'id': 'positions',
            'title': 'Positions & Teams',
            'description': 'Advisory team, wing secretaries, and wing mentors'
        }
    ]
    
    questions = []
    
    # Leadership sub-section
    if 'leadership' in section_data:
        leadership = section_data['leadership']
        leadership_answers = []
        
        if 'pic' in leadership:
            leadership_answers.append(f"PIC: {leadership['pic']}")
        
        if 'general_secretary' in leadership:
            gs = leadership['general_secretary']
            leadership_answers.append(f"General Secretary: {gs['name']} ({gs['year']})")
        
        questions.append({
            'id': 'leadership_info',
            'section_id': section_id,
            'sub_section_id': 'leadership',
            'question': 'Who are the leaders?',
            'answer_type': 'bullet_points',
            'answer': leadership_answers
        })
    
    # Positions sub-section
    if 'positions' in section_data:
        positions = section_data['positions']
        
        # Advisory Team
        if 'advisory_team' in positions:
            advisory = positions['advisory_team']
            questions.append({
                'id': 'advisory_team',
                'section_id': section_id,
                'sub_section_id': 'positions',
                'question': f'Who is in the Advisory Team ({advisory["year"]})?',
                'answer_type': 'bullet_points',
                'answer': advisory['members']
            })
        
        # Wing Secretaries
        if 'wing_secretaries' in positions:
            wing_secs = positions['wing_secretaries']
            wing_sec_answers = []
            
            for wing, secretary in wing_secs['members'].items():
                if isinstance(secretary, list):
                    wing_sec_answers.append(f"{wing.replace('_', ' ').title()}: {', '.join(secretary)}")
                else:
                    wing_sec_answers.append(f"{wing.replace('_', ' ').title()}: {secretary}")
            
            questions.append({
                'id': 'wing_secretaries_hierarchy',
                'section_id': section_id,
                'sub_section_id': 'positions',
                'question': f'Who are the Wing Secretaries ({wing_secs["year"]})?',
                'answer_type': 'bullet_points',
                'answer': wing_sec_answers
            })
        
        # Wing Mentors
        if 'wing_mentors' in positions:
            wing_mentors = positions['wing_mentors']
            mentor_answers = []
            
            for wing, mentors in wing_mentors['wings'].items():
                mentor_answers.append(f"{wing.replace('_', ' ').title()}:")
                mentor_answers.extend([f"• {mentor}" for mentor in mentors])
                mentor_answers.append("")  # Empty line for separation
            
            questions.append({
                'id': 'wing_mentors_hierarchy',
                'section_id': section_id,
                'sub_section_id': 'positions',
                'question': f'Who are the Wing Mentors ({wing_mentors["year"]})?',
                'answer_type': 'bullet_points',
                'answer': mentor_answers
            })
    
    return sub_sections, questions

def process_section(section_data, interface_types):
    """Process a section and its questions"""
    section = {
        'id': section_data['id'],
        'title': section_data['title'],
        'interface_types': interface_types
    }
    
    sub_sections = []
    questions = []
    
    # Process based on section type
    if section_data['id'] == 'teaching_technical':
        sub_sections, questions = process_teaching_technical_section(section_data, section_data['id'])
    elif section_data['id'] == 'hierarchy':
        sub_sections, questions = process_hierarchy_section(section_data, section_data['id'])
    else:
        # General section has no sub-sections
        questions = process_questions_section(section_data, section_data['id'])
    
    return section, sub_sections, questions

def upload_faqs_to_firestore():
    try:
        db = init_firebase()
        
        # Read the JSON file
        with open('faqs_raw.json', 'r') as f:
            data = json.load(f)
        
        # Interface type mapping - updated to include hierarchy
        section_interfaces = {
            'general': ['nss', 'teaching_wing'],
            'teaching_technical': ['teaching_wing'],
            'hierarchy': ['nss', 'teaching_wing']
        }
        
        print("\nStarting FAQ upload process...")
        
        # First, try to read the sections document to verify permissions
        sections_ref = db.collection('faqs').document('sections')
        try:
            sections_ref.get()
            print("Successfully verified Firestore read access")
        except Exception as e:
            print(f"Warning: Could not read sections document: {str(e)}")
        
        # Process and upload sections and questions
        batch = db.batch()
        sections_data = {}
        total_questions = 0
        total_sub_sections = 0
        
        # Process each section
        for section_id, section_data in data['faqs']['sections'].items():
            print(f"\nProcessing section: {section_id}")
            section, sub_sections, questions = process_section(
                section_data, 
                section_interfaces.get(section_id, ['nss', 'teaching_wing'])
            )
            
            # Add section to sections document
            sections_data[section_id] = section
            print(f"Added section {section_id} to batch with {len(sub_sections)} sub-sections and {len(questions)} questions")
            total_sub_sections += len(sub_sections)
            total_questions += len(questions)
            
            # Upload sub-sections
            for sub_section in sub_sections:
                print(f"Adding sub-section: {sub_section['id']}")
                # Ensure section_id is included in the sub-section data
                sub_section_data = sub_section.copy()
                sub_section_data['section_id'] = section_id
                sub_section_ref = db.collection('faqs').document('sub_sections').collection(section_id).document(sub_section['id'])
                batch.set(sub_section_ref, sub_section_data)
            
            # Upload questions as separate documents in the questions subcollection
            for question in questions:
                print(f"Adding question: {question['id']}")
                question_ref = db.collection('faqs').document('questions').collection(section_id).document(question['id'])
                batch.set(question_ref, question)
        
        # Upload all sections
        print("\nUploading sections data...")
        batch.set(sections_ref, sections_data)
        
        # Commit the batch
        print("Committing batch...")
        batch.commit()
        
        print("\nFAQ data uploaded successfully!")
        print(f"Uploaded {len(sections_data)} sections with {total_sub_sections} sub-sections and {total_questions} total questions")
        
    except Exception as e:
        print(f"\nError uploading FAQs: {str(e)}")
        if hasattr(e, 'details'):
            print(f"Error details: {e.details()}")
        raise

if __name__ == '__main__':
    upload_faqs_to_firestore() 