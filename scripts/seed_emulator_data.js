/**
 * NSS IITP — Firestore Emulator Seed Data
 * ====================================================
 * Seeds realistic test data into the local Firestore Emulator
 * so you can safely test the reset_for_new_cycle.js script.
 *
 * USAGE:
 *   1. Start emulator:  firebase emulators:start --only firestore
 *   2. Run seed:        node seed_emulator_data.js
 *   3. Run dry-run:     node reset_for_new_cycle.js --emulator
 *   4. Confirm output is correct, then:
 *      node reset_for_new_cycle.js --emulator --live
 *   5. Verify reset worked correctly
 */

'use strict';

process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';

const admin = require('firebase-admin');
admin.initializeApp({ projectId: 'nssiitp-app' });

const { getFirestore, Timestamp, FieldValue } = require('firebase-admin/firestore');
const db = getFirestore();

// ─── SEED DATA ────────────────────────────────────────────────────────────────

const ADMINS = [
  { id: 'ADMIN001', name: 'Admin User', rollNumber: 'ADMIN001', userType: 'Admin', wings: [], email: 'admin@nssiitp.ac.in' },
];

const STUDENTS = [
  { id: '2501MM16', name: 'Abhishek Dwivedi', rollNumber: '2501MM16', userType: 'student', wings: ['Prerna Wing'], hours: 4.5, sem1Hours: 2.0, sem2Hours: 2.5, eventsAttended: 3, eventsList: ['event_1', 'event_2', 'event_3'] },
  { id: '2501AI21', name: 'Ashan Nayak',      rollNumber: '2501AI21', userType: 'student', wings: ['Environmental Wing'], hours: -2.0, sem1Hours: 0, sem2Hours: -2.0, eventsAttended: 1, eventsList: ['event_1'] },
  { id: '2501CS69', name: 'Amit Ahirrao',     rollNumber: '2501CS69', userType: 'student', wings: ['Rural Development Wing'], hours: 6.0, sem1Hours: 4.0, sem2Hours: 2.0, eventsAttended: 4, eventsList: ['event_1', 'event_2', 'event_3', 'event_4'] },
  { id: '2502PC09', name: 'Abhineet Kumar',   rollNumber: '2502PC09', userType: 'student', wings: ['Prerna Wing'], hours: 0, sem1Hours: 0, sem2Hours: 0, eventsAttended: 0, eventsList: [] },
  { id: '2501PH13', name: 'Aniruddha Karri',  rollNumber: '2501PH13', userType: 'student', wings: ['Teaching and Technical Wing'], hours: 2.0, sem1Hours: 2.0, sem2Hours: 0, eventsAttended: 1, eventsList: ['event_2'] },
];

const EVENTS = [
  {
    id: 'event_1',
    eventName: 'Tree Plantation Drive',
    eventDate: '15 Jul 2025',
    hours: 2.0,
    negativeHours: 1.5,
    mandatory: true,
    isMandatory: true,
    wings: ['Environmental Wing'],
    is_live: false,
    absentPenaltyApplied: true,
    penaltyEverApplied: true,
    penalizedRollNumbers: ['2501AI21'],
    attendees: [
      { rollNumber: '2501MM16', name: 'Abhishek Dwivedi', attendanceMethod: 'QR' },
      { rollNumber: '2501CS69', name: 'Amit Ahirrao',     attendanceMethod: 'QR' },
    ],
    total_marked: 2,
    allowedAttendanceMode: 'QR',
    visibleOnlyToPresent: false,
    createdAt: Timestamp.fromDate(new Date('2025-07-15')),
  },
  {
    id: 'event_2',
    eventName: 'Health Awareness Camp',
    eventDate: '22 Aug 2025',
    hours: 2.0,
    negativeHours: 0,
    mandatory: false,
    isMandatory: false,
    wings: [],
    is_live: false,
    absentPenaltyApplied: false,
    penaltyEverApplied: false,
    penalizedRollNumbers: [],
    attendees: [
      { rollNumber: '2501MM16', name: 'Abhishek Dwivedi', attendanceMethod: 'QR' },
      { rollNumber: '2501CS69', name: 'Amit Ahirrao',     attendanceMethod: 'Photo' },
      { rollNumber: '2501PH13', name: 'Aniruddha Karri',  attendanceMethod: 'Manual' },
    ],
    total_marked: 3,
    allowedAttendanceMode: 'BOTH',
    visibleOnlyToPresent: false,
    createdAt: Timestamp.fromDate(new Date('2025-08-22')),
  },
  {
    id: 'event_3',
    eventName: 'Swachh Bharat Abhiyan',
    eventDate: '05 Sep 2025',
    hours: 1.5,
    negativeHours: 1.0,
    mandatory: true,
    isMandatory: true,
    wings: ['Prerna Wing', 'Rural Development Wing'],
    is_live: false,
    absentPenaltyApplied: false,
    penaltyEverApplied: false,
    penalizedRollNumbers: [],
    attendees: [
      { rollNumber: '2501MM16', name: 'Abhishek Dwivedi', attendanceMethod: 'QR' },
      { rollNumber: '2501CS69', name: 'Amit Ahirrao',     attendanceMethod: 'QR' },
    ],
    total_marked: 2,
    allowedAttendanceMode: 'QR',
    visibleOnlyToPresent: false,
    createdAt: Timestamp.fromDate(new Date('2025-09-05')),
  },
  {
    id: 'event_4',
    eventName: 'Blood Donation Camp',
    eventDate: '10 Oct 2025',
    hours: 2.5,
    negativeHours: 0,
    mandatory: false,
    isMandatory: false,
    wings: [],
    is_live: false,
    absentPenaltyApplied: false,
    penaltyEverApplied: false,
    penalizedRollNumbers: [],
    attendees: [
      { rollNumber: '2501CS69', name: 'Amit Ahirrao', attendanceMethod: 'QR' },
    ],
    total_marked: 1,
    allowedAttendanceMode: 'GEO',
    visibleOnlyToPresent: false,
    createdAt: Timestamp.fromDate(new Date('2025-10-10')),
  },
];

// Attendance subcollection records (one per attendee per event)
const ATTENDANCE_RECORDS = {
  'event_1': [
    { rollNumber: '2501MM16', name: 'Abhishek Dwivedi', attendanceMethod: 'QR', scanTimestamp: Timestamp.fromDate(new Date('2025-07-15T10:30:00')) },
    { rollNumber: '2501CS69', name: 'Amit Ahirrao',     attendanceMethod: 'QR', scanTimestamp: Timestamp.fromDate(new Date('2025-07-15T10:32:00')) },
  ],
  'event_2': [
    { rollNumber: '2501MM16', name: 'Abhishek Dwivedi', attendanceMethod: 'QR',     scanTimestamp: Timestamp.fromDate(new Date('2025-08-22T11:00:00')) },
    { rollNumber: '2501CS69', name: 'Amit Ahirrao',     attendanceMethod: 'Photo',  scanTimestamp: Timestamp.fromDate(new Date('2025-08-22T11:05:00')) },
    { rollNumber: '2501PH13', name: 'Aniruddha Karri',  attendanceMethod: 'Manual', scanTimestamp: Timestamp.fromDate(new Date('2025-08-22T11:10:00')) },
  ],
  'event_3': [
    { rollNumber: '2501MM16', name: 'Abhishek Dwivedi', attendanceMethod: 'QR', scanTimestamp: Timestamp.fromDate(new Date('2025-09-05T09:00:00')) },
    { rollNumber: '2501CS69', name: 'Amit Ahirrao',     attendanceMethod: 'QR', scanTimestamp: Timestamp.fromDate(new Date('2025-09-05T09:02:00')) },
  ],
  'event_4': [
    { rollNumber: '2501CS69', name: 'Amit Ahirrao', attendanceMethod: 'GEO', scanTimestamp: Timestamp.fromDate(new Date('2025-10-10T14:00:00')) },
  ],
};

const PHOTO_LOGS = [
  { eventId: 'event_2', rollNumber: '2501CS69', name: 'Amit Ahirrao', status: 'approved', submittedAt: Timestamp.fromDate(new Date('2025-08-22T11:04:00')) },
];

const SCHEDULED_NOTIFICATIONS = [
  { title: 'Tree Plantation Reminder', body: 'Event starts in 1 hour', topics: ['all'], scheduledAt: Timestamp.fromDate(new Date('2025-07-15T09:00:00')), status: 'sent' },
  { title: 'Blood Donation Camp', body: 'Join us today!', topics: ['all'], scheduledAt: Timestamp.fromDate(new Date('2025-10-10T08:00:00')), status: 'pending' },
];

// ─── SEED ─────────────────────────────────────────────────────────────────────

async function seed() {
  console.log('\n' + '═'.repeat(60));
  console.log('  NSS IITP — Seeding Firestore Emulator');
  console.log('═'.repeat(60) + '\n');

  // Admins
  console.log('👤 Seeding admins...');
  for (const admin of ADMINS) {
    await db.collection('users').doc(admin.id).set(admin);
    console.log(`  ✅ ${admin.name} (${admin.id})`);
  }

  // Students
  console.log('\n👥 Seeding students...');
  for (const student of STUDENTS) {
    await db.collection('users').doc(student.id).set(student);
    console.log(`  ✅ ${student.name} (${student.rollNumber}) — hours: ${student.hours}, sem1: ${student.sem1Hours}, sem2: ${student.sem2Hours}`);
  }

  // Events + attendance subcollections
  console.log('\n📅 Seeding events + attendance records...');
  for (const event of EVENTS) {
    const { id, ...eventData } = event;
    await db.collection('NSS_Events_Attendence').doc(id).set(eventData);

    const attRecords = ATTENDANCE_RECORDS[id] || [];
    for (const att of attRecords) {
      await db.collection('NSS_Events_Attendence').doc(id).collection('attendance').doc(att.rollNumber).set(att);
    }
    console.log(`  ✅ ${event.eventName} — ${attRecords.length} attendance record(s)`);
  }

  // PhotoAttendanceLog
  console.log('\n📷 Seeding PhotoAttendanceLog...');
  for (const log of PHOTO_LOGS) {
    const ref = db.collection('PhotoAttendanceLog').doc();
    await ref.set(log);
    console.log(`  ✅ Photo log for ${log.name} (${log.eventId})`);
  }

  // scheduledNotifications
  console.log('\n🔔 Seeding scheduledNotifications...');
  for (const notif of SCHEDULED_NOTIFICATIONS) {
    const ref = db.collection('scheduledNotifications').doc();
    await ref.set(notif);
    console.log(`  ✅ ${notif.title} (${notif.status})`);
  }

  // Summary
  console.log('\n' + '─'.repeat(60));
  console.log('\n📊 SEED COMPLETE — Emulator now contains:\n');
  console.log(`  users:                    ${ADMINS.length + STUDENTS.length} (${ADMINS.length} admin, ${STUDENTS.length} students)`);
  console.log(`  NSS_Events_Attendence:    ${EVENTS.length} events`);
  const totalAtt = Object.values(ATTENDANCE_RECORDS).flat().length;
  console.log(`  attendance subcollections: ${totalAtt} records across ${EVENTS.length} events`);
  console.log(`  PhotoAttendanceLog:       ${PHOTO_LOGS.length} records`);
  console.log(`  scheduledNotifications:   ${SCHEDULED_NOTIFICATIONS.length} records`);
  console.log('\n  Next steps:');
  console.log('  1. node reset_for_new_cycle.js --emulator           (dry-run — see the plan)');
  console.log('  2. node reset_for_new_cycle.js --emulator --live    (execute on emulator)');
  console.log('  3. Verify the output matches expectations');
  console.log('  4. If correct → run on production with serviceAccountKey.json\n');
}

seed().catch(err => {
  console.error('❌ Seed error:', err.message);
  process.exit(1);
});
