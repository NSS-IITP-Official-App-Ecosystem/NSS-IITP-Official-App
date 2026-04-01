const express = require('express');
const admin = require('firebase-admin');

const app = express();
app.use(express.json());

// Initialize Firebase Admin dynamically based on projectId
function getFirebaseAdmin(projectId) {
    if (admin.apps.length > 0) {
        const existingApp = admin.apps.find(app => app.name === projectId);
        if (existingApp) return existingApp;
    }

    // Try to load credentials from Vercel Environment Variables
    let serviceAccountJson;
    
    if (projectId === 'chatapp-24fae') {
        if (!process.env.FCM_TEST_KEY) throw new Error("Missing FCM_TEST_KEY in environment variables");
        serviceAccountJson = JSON.parse(process.env.FCM_TEST_KEY);
    } else if (projectId === 'nssiitp-app') {
        if (!process.env.FCM_PROD_KEY) throw new Error("Missing FCM_PROD_KEY in environment variables");
        serviceAccountJson = JSON.parse(process.env.FCM_PROD_KEY);
    } else {
        throw new Error(`Invalid projectId: ${projectId}`);
    }

    return admin.initializeApp({
        credential: admin.credential.cert(serviceAccountJson),
        projectId: projectId
    }, projectId); // Give the app a name equal to the projectId so we can reuse it
}

app.post('/api/send', async (req, res) => {
    try {
        const { projectId, topic, token, title, body, data } = req.body;

        if (!projectId) {
            return res.status(400).json({ error: "Missing 'projectId' property in request body" });
        }
        if (!title) {
            return res.status(400).json({ error: "Missing 'title' property in request body" });
        }

        // Initialize or get existing Firebase App instance for the requested project
        const firebaseApp = getFirebaseAdmin(projectId);

        // Build the message payload
        const message = {
            notification: {
                title: title,
                body: body || ''
            },
            data: data || {}
        };

        // Target a Topic OR a specific Device Token
        if (topic) {
            message.topic = topic;
        } else if (token) {
            message.token = token;
        } else {
            return res.status(400).json({ error: "Must provide either 'topic' or 'token' in request body" });
        }

        // Send the notification using HTTP v1 (firebase-admin SDK handles this natively)
        const response = await firebaseApp.messaging().send(message);

        return res.status(200).json({ 
            success: true, 
            messageId: response,
            environment: projectId === 'nssiitp-app' ? 'Production' : 'Test' 
        });

    } catch (error) {
        console.error("Error sending notification:", error);
        return res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

module.exports = app;
