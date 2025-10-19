#!/usr/bin/env python3
import json
from typing import Any, Dict, List
from faqs_auth import get_db


def upsert_section(sections_doc: Dict[str, Any], section_id: str, title: str, interface_types: List[str]):
    sections_doc[section_id] = {
        'id': section_id,
        'title': title,
        'interface_types': interface_types
    }


def write_subsection(db, section_id: str, sub: Dict[str, Any], parent_sub_section_id: str = None):
    sub_payload = {
        'id': sub['id'],
        'section_id': section_id,
        'title': sub['title'],
        'description': sub.get('description') or '',
        'parent_sub_section_id': parent_sub_section_id or ''
    }
    db.collection('faqs').document('sub_sections').collection(section_id).document(sub['id']).set(sub_payload)


def write_question(db, section_id: str, q: Dict[str, Any], sub_section_id: str = None):
    payload = {
        'id': q['id'],
        'section_id': section_id,
        'sub_section_id': sub_section_id or '',
        'question': q['question'],
        'answer_type': 'text',
        'answer': q.get('answer', '')
    }
    db.collection('faqs').document('questions').collection(section_id).document(q['id']).set(payload)


def build_from_seed(seed: Dict[str, Any]):
    db = get_db()

    # 1) Build sections doc
    sections_doc: Dict[str, Any] = {}

    def process_root_section(sid: str, sdata: Dict[str, Any]):
        upsert_section(sections_doc, sid, sdata['title'], sdata.get('interface_types') or [])

        # Only root holds three top-level; nested goes to sub_sections under its parent section
        if sid == 'wing_specific':
            for sub in sdata.get('sections', []):
                # teaching_technical subtree under wing_specific
                write_subsection(db, 'wing_specific', sub, None)  # first-level under wing_specific

                # Process nested sections under teaching_technical
                for nested in sub.get('sections', []):
                    write_subsection(db, 'wing_specific', nested, sub['id'])

                # Questions directly under teaching_technical
                for q in sub.get('questions', []):
                    write_question(db, 'wing_specific', q, sub['id'])

                # Questions under nested subsections
                for nested in sub.get('sections', []):
                    for q in nested.get('questions', []):
                        write_question(db, 'wing_specific', q, nested['id'])

        # Questions directly under root sections
        for q in sdata.get('questions', []):
            write_question(db, sid, q, None)

    for sid, sdata in seed['sections'].items():
        process_root_section(sid, sdata)

    # Commit sections doc at the end
    db.collection('faqs').document('sections').set(sections_doc)


if __name__ == '__main__':
    import sys
    if len(sys.argv) < 2:
        print('Usage: python scripts/rebuild_faqs.py scripts/new_faqs_seed.json')
        raise SystemExit(1)
    seed_path = sys.argv[1]
    with open(seed_path, 'r', encoding='utf-8') as f:
        seed = json.load(f)
    build_from_seed(seed)
    print('Rebuild completed successfully.')


