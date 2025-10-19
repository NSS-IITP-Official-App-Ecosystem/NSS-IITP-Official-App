#!/usr/bin/env python3
import json
from faqs_auth import get_db


def seed_dummy_faqs():
    """Seed the faqs collection with dummy data using single-collection structure"""
    db = get_db()
    
    print("Seeding dummy FAQ data...")
    
    # Root sections (parent_section = null)
    root_sections = [
        {
            "id": "general",
            "title": "General Questions",
            "interface_types": ["nss", "teaching_wing"]
        },
        {
            "id": "hierarchy", 
            "title": "NSS IIT Patna Hierarchy",
            "interface_types": ["nss", "teaching_wing"]
        },
        {
            "id": "wing_specific",
            "title": "Wing Specific Information", 
            "interface_types": ["teaching_wing"]
        }
    ]
    
    # Teaching & Technical Wing subsection (parent_section = "wing_specific")
    teaching_technical = {
        "id": "teaching_technical",
        "title": "Teaching & Technical Wing",
        "interface_types": []
    }
    
    # Nested subsections under Teaching & Technical Wing
    nested_subsections = [
        {
            "id": "general_info",
            "title": "General Information",
            "interface_types": []
        },
        {
            "id": "committees", 
            "title": "Committees",
            "interface_types": []
        },
        {
            "id": "schools",
            "title": "Teaching Locations", 
            "interface_types": []
        },
        {
            "id": "policies",
            "title": "Policies & Guidelines",
            "interface_types": []
        }
    ]
    
    # Dummy questions for each section
    questions_data = [
        # General section questions
        {
            "id": "general_q1",
            "question": "What is NSS?",
            "answer": "National Service Scheme is a public service program under the Ministry of Youth Affairs and Sports."
        },
        {
            "id": "general_q2", 
            "question": "What are the NSS requirements?",
            "answer": "Students need to complete 120 hours of community service in their first year."
        },
        
        # Hierarchy section questions
        {
            "id": "hierarchy_q1",
            "question": "Who is the PIC?",
            "answer": "Dr. Somanath Pradhan is the Program In-charge."
        },
        {
            "id": "hierarchy_q2",
            "question": "Who is the General Secretary?",
            "answer": "Mahipal is the General Secretary (3rd year)."
        },
        
        # Teaching & Technical Wing questions
        {
            "id": "teaching_q1",
            "question": "What are the sub-divisions in Teaching Wing?",
            "answer": "Core Teaching Committee, Events Committee, and Social Media & Photography Committee."
        },
        {
            "id": "teaching_q2",
            "question": "How can I get selected for a committee?",
            "answer": "Selection is based on interview score and skill set by sub-coordinators and coordinators."
        },
        
        # General Info subsection questions
        {
            "id": "general_info_q1",
            "question": "What committees exist in Teaching Wing?",
            "answer": "Core Teaching Committee, Events Committee, and Social Media & Photography Committee."
        },
        
        # Committees subsection questions
        {
            "id": "committees_q1",
            "question": "Tell me about Events Committee",
            "answer": "Sub-coordinators: Yash Mayur Modi, Devyansh Pandey, Harsh Verma. Responsibilities include event planning, logistics, and crowd control."
        },
        
        # Schools subsection questions
        {
            "id": "schools_q1",
            "question": "Which government schools are covered?",
            "answer": "Raghopur, Amhara School, TPS School"
        },
        
        # Policies subsection questions
        {
            "id": "policies_q1",
            "question": "How many hours are required?",
            "answer": "To be decided by the wing coordinators."
        }
    ]
    
    # Write root sections
    for section in root_sections:
        doc_data = {
            "type": "section",
            "title": section["title"],
            "parent_section": None,
            "interface_types": section["interface_types"]
        }
        db.collection("faqs").document(section["id"]).set(doc_data)
        print(f"✓ Created root section: {section['title']}")
    
    # Write Teaching & Technical Wing subsection
    teaching_doc_data = {
        "type": "section", 
        "title": teaching_technical["title"],
        "parent_section": "wing_specific",
        "interface_types": teaching_technical["interface_types"]
    }
    db.collection("faqs").document(teaching_technical["id"]).set(teaching_doc_data)
    print(f"✓ Created subsection: {teaching_technical['title']}")
    
    # Write nested subsections under Teaching & Technical Wing
    for subsection in nested_subsections:
        doc_data = {
            "type": "section",
            "title": subsection["title"], 
            "parent_section": "teaching_technical",
            "interface_types": subsection["interface_types"]
        }
        db.collection("faqs").document(subsection["id"]).set(doc_data)
        print(f"✓ Created nested subsection: {subsection['title']}")
    
    # Write questions
    question_parents = {
        "general_q1": "general",
        "general_q2": "general", 
        "hierarchy_q1": "hierarchy",
        "hierarchy_q2": "hierarchy",
        "teaching_q1": "teaching_technical",
        "teaching_q2": "teaching_technical",
        "general_info_q1": "general_info",
        "committees_q1": "committees",
        "schools_q1": "schools", 
        "policies_q1": "policies"
    }
    
    for question in questions_data:
        parent_section = question_parents[question["id"]]
        doc_data = {
            "type": "question",
            "parent_section": parent_section,
            "question": question["question"],
            "answer_type": "text",
            "answer": question["answer"]
        }
        db.collection("faqs").document(question["id"]).set(doc_data)
        print(f"✓ Created question: {question['question'][:50]}...")
    
    print(f"\n✅ Successfully seeded {len(root_sections)} root sections, {len(nested_subsections) + 1} subsections, and {len(questions_data)} questions")
    print("\nStructure created:")
    print("📁 general (root)")
    print("📁 hierarchy (root)")  
    print("📁 wing_specific (root)")
    print("  └── 📁 teaching_technical")
    print("      ├── 📁 general_info")
    print("      ├── 📁 committees")
    print("      ├── 📁 schools")
    print("      └── 📁 policies")


if __name__ == '__main__':
    print("Starting FAQ seeding...")
    try:
        seed_dummy_faqs()
        print("Seeding completed successfully!")
    except Exception as e:
        print(f"Error during seeding: {str(e)}")
        import traceback
        traceback.print_exc()
