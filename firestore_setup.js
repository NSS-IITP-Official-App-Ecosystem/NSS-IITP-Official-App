const admin = require('firebase-admin');
const serviceAccount = require('./service-account-key.json');

// Initialize Firebase Admin with your service account
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// Sample data for testing
const now = new Date();
const tomorrow = new Date();
tomorrow.setDate(now.getDate() + 1);

// Function to add a sample event
async function addSampleEvent(collection, eventData) {
  try {
    const result = await db.collection(collection).doc(eventData.id).set(eventData);
    console.log(`Added sample event to ${collection}: ${eventData.id}`);
  } catch (error) {
    console.error(`Error adding sample event to ${collection}:`, error);
  }
}

// Function to create collections and add sample data
async function setupFirestore() {
  console.log('Setting up Firestore collections for calendar feature...');

  // 1. teaching_events collection
  const teachingEvent = {
    id: 'sample-teaching-1',
    date: admin.firestore.Timestamp.fromDate(now),
    title: 'Data Structures Lecture',
    description: 'Core teaching session on algorithms and data structures',
    eventType: 'TEACHING',
    timeSlot: '10:00 AM - 12:00 PM',
    status: 'SCHEDULED',
    createdBy: 'admin',
    bookedBy: '',
    bookedByName: '',
    timestamp: admin.firestore.Timestamp.now().toMillis()
  };
  
  await addSampleEvent('teaching_events', teachingEvent);

  // 2. general_events collection
  const generalEvent = {
    id: 'sample-event-1',
    date: admin.firestore.Timestamp.fromDate(tomorrow),
    title: 'Department Meeting',
    description: 'Monthly department meeting',
    eventType: 'GENERAL_EVENT',
    timeSlot: '2:00 PM - 3:00 PM',
    status: 'SCHEDULED',
    createdBy: 'admin',
    bookedBy: '',
    bookedByName: '',
    timestamp: admin.firestore.Timestamp.now().toMillis()
  };
  
  await addSampleEvent('general_events', generalEvent);

  // 3. leave_applications collection with a sample leave application
  const leaveApplication = {
    id: 'sample-leave-1',
    userId: 'student1',
    userName: 'John Student',
    date: admin.firestore.Timestamp.fromDate(now),
    slot: '10:00 AM - 12:00 PM',
    subject: 'Data Structures',
    school: 'School of Engineering',
    status: 'PENDING',
    timestamp: admin.firestore.Timestamp.now().toMillis()
  };
  
  try {
    await db.collection('leave_applications').doc(leaveApplication.id).set(leaveApplication);
    console.log('Added sample leave application');
  } catch (error) {
    console.error('Error adding sample leave application:', error);
  }

  // 4. accepted_leaves collection (this would normally be populated when an admin approves a leave)
  const acceptedLeave = {
    ...leaveApplication,
    id: 'sample-accepted-1',
    status: 'APPROVED'
  };
  
  try {
    await db.collection('accepted_leaves').doc(acceptedLeave.id).set(acceptedLeave);
    console.log('Added sample accepted leave');
  } catch (error) {
    console.error('Error adding sample accepted leave:', error);
  }

  console.log('Firestore setup complete!');
  process.exit(0);
}

// Run the setup
setupFirestore().catch(error => {
  console.error('Setup failed:', error);
  process.exit(1);
}); 