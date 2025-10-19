#!/usr/bin/env python3
import json
import os
from datetime import datetime
from faqs_auth import get_db


OUTPUT_DIR = os.path.join(os.getcwd(), 'faqs_backup')


def ensure_out_dir():
    os.makedirs(OUTPUT_DIR, exist_ok=True)


def safe_get(doc_ref):
    snap = doc_ref.get()
    return snap.to_dict() if snap.exists else None


def export_faqs():
    db = get_db()
    ensure_out_dir()

    print('Reading sections...')
    sections = safe_get(db.collection('faqs').document('sections')) or {}

    print('Reading all sub_sections...')
    sub_sections = {}
    sub_root = db.collection('faqs').document('sub_sections')
    for col in sub_root.collections():
        sub_sections[col.id] = {}
        for doc in col.stream():
            sub_sections[col.id][doc.id] = doc.to_dict()

    print('Reading all questions...')
    questions = {}
    q_root = db.collection('faqs').document('questions')
    for col in q_root.collections():
        questions[col.id] = {}
        for doc in col.stream():
            questions[col.id][doc.id] = doc.to_dict()

    ts = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
    payload = {
        'project_id': os.getenv('GOOGLE_CLOUD_PROJECT'),
        'exported_at_utc': ts,
        'sections': sections,
        'sub_sections': sub_sections,
        'questions': questions,
    }

    json_path = os.path.join(OUTPUT_DIR, f'faqs_backup_{ts}.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)

    txt_path = os.path.join(OUTPUT_DIR, f'faqs_backup_{ts}.txt')
    with open(txt_path, 'w', encoding='utf-8') as f:
        f.write('SECTIONS\n')
        for sid, sdata in sections.items():
            f.write(f'- {sid}: {sdata.get("title", "")}\n')
        f.write('\nSUB_SECTIONS\n')
        for sid, subs in sub_sections.items():
            f.write(f'[{sid}]\n')
            for sub_id, sub_data in subs.items():
                f.write(f'  - {sub_id}: {sub_data.get("title", "")} (parent={sub_data.get("parent_sub_section_id")})\n')
        f.write('\nQUESTIONS\n')
        for sid, qmap in questions.items():
            f.write(f'[{sid}]\n')
            for qid, qdata in qmap.items():
                q = (qdata.get('question') or '')
                q = q.replace('\n', ' ')[:150]
                f.write(f'  - {qid}: {q} (sub_section_id={qdata.get("sub_section_id")})\n')

    print('Backup written:')
    print(' -', json_path)
    print(' -', txt_path)


if __name__ == '__main__':
    export_faqs()


