#!/usr/bin/env python3
from faqs_auth import get_db


CONFIRM_PHRASE = 'I_UNDERSTAND_THIS_DELETES_FAQS'


def clean(confirm: str):
    if confirm != CONFIRM_PHRASE:
        raise SystemExit(f'ABORT: Pass exactly {CONFIRM_PHRASE} to proceed')

    db = get_db()

    # Delete questions/* subcollections
    q_root = db.collection('faqs').document('questions')
    for col in q_root.collections():
        batch = db.batch()
        count = 0
        for doc in col.stream():
            batch.delete(doc.reference)
            count += 1
            if count % 400 == 0:
                batch.commit()
                batch = db.batch()
        batch.commit()
        print(f'Deleted {count} question docs under {col.id}')

    # Delete sub_sections/* subcollections
    s_root = db.collection('faqs').document('sub_sections')
    for col in s_root.collections():
        batch = db.batch()
        count = 0
        for doc in col.stream():
            batch.delete(doc.reference)
            count += 1
            if count % 400 == 0:
                batch.commit()
                batch = db.batch()
        batch.commit()
        print(f'Deleted {count} sub_section docs under {col.id}')

    # Reset sections doc to empty
    db.collection('faqs').document('sections').set({})
    print('Cleared sections document')


if __name__ == '__main__':
    import sys
    clean(sys.argv[1] if len(sys.argv) > 1 else '')


