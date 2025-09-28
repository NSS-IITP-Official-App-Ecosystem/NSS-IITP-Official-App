# TW-APP Chat Application

Changes

A comprehensive Android chat application with advanced features for educational institutions. This application facilitates communication between students and teachers through private messaging, group chats, calendar management, and automated scheduling.

## 📱 Features hello


### Communication
- Real-time private messaging
- Group chat functionalityS
- Media sharing (images, documents)
- Push notifications
- Read receipts and typing indicators

### User Management
- Role-based authentication (Student/Admin)
- User profiles with academic information
- Permission management
- Firebase authentication integration

### Calendar & Scheduling
- Event calendar with filtering options
- Teaching slots scheduling
- Leave application management
- Automated scheduling tools
- Availability management

### Organization
- Modular architecture
- Multi-module project structure
- Clean code with MVVM pattern

## 🛠️ Technologies

- **Language:** Kotlin
- **UI Framework:** XML layouts, Jetpack Compose
- **Architecture:** MVVM (Model-View-ViewModel)
- **Backend:**
  - Firebase Authentication
  - Cloud Firestore
  - Firebase Cloud Messaging
  - Firebase Cloud Functions
- **Dependencies:**
  - AndroidX libraries
  - Material Design components
  - Lifecycle components
  - Navigation components
  - Coroutines for asynchronous operations

## 🏗️ Project Structure

The project follows a modular architecture:

```
TW-APP/
├── app/                           # Main application module
│   ├── src/main/
│   │   ├── java/com/phad/chatapp/ # Application source code
│   │   │   ├── activities/        # Activity classes
│   │   │   ├── adapters/          # RecyclerView adapters
│   │   │   ├── fragments/         # UI fragments
│   │   │   ├── models/            # Data models
│   │   │   ├── repositories/      # Data repositories
│   │   │   └── utils/             # Utility classes
│   │   └── res/                   # Android resources
├── calendar/                      # Calendar feature module
│   └── src/main/
│       ├── java/.../calendar/     # Calendar functionality
│       └── res/                   # Calendar resources
└── scheduling/                    # Scheduling feature module
    └── src/main/
        ├── java/.../scheduling/   # Scheduling functionality
        └── res/                   # Scheduling resources
```

## 🚀 Installation

1. Clone the repository:
   ```
   git clone https://github.com/code-epic-adi/TW-APP.git
   ```

2. Open the project in Android Studio.

3. Set up Firebase:
   - Create a new Firebase project in the [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app to your Firebase project
   - Download the `google-services.json` file and place it in the app directory
   - Enable Authentication, Firestore, and Cloud Messaging in the Firebase Console

4. Build and run the application on an emulator or physical device.

## 📝 Usage

### Authentication
- Users can log in with their institutional credentials
- System automatically assigns roles (Student/Admin)

### Messaging
- Start private conversations from the contacts list
- Create and manage group chats
- Share media and documents

### Calendar
- View and filter academic events
- Schedule teaching sessions
- Apply for leave/absence

### Scheduling
- Teachers can create teaching slot presets
- Students can set their availability
- System generates optimal schedules

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Contact

Project Link: [https://github.com/code-epic-adi/TW-APP](https://github.com/code-epic-adi/TW-APP)

---

Developed with ❤️ for improving educational communication 
