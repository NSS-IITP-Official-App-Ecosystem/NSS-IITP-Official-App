# Biometric Fingerprint Authentication for Attendance

## Problem Statement

**Proxy Scenario to Prevent:**
- Person A comes to event with TWO phones: their own phone AND Person B's phone
- Person B is absent from the event
- Person B has pre-added Person A's fingerprint to Person B's phone
- Person A tries to mark attendance for Person B using Person B's phone with their own fingerprint

**Goal:** Detect and block this proxy attempt using hardware-backed biometric security.

---

## Solution Overview

### Core Mechanism: Hardware-Backed Key with Auto-Invalidation

Uses Android's **KeyStore** system to create cryptographic keys that:
1. Are stored in device's secure hardware (cannot be extracted)
2. Require biometric authentication to use
3. **Automatically self-destruct if fingerprint enrollments change** (add/remove fingerprints)

### Security Layers

| Layer | Component | Purpose |
|-------|-----------|---------|
| 1 | Enrollment Date (Firebase) | Distinguish first-time enrollment vs tampering |
| 2 | Device ID (Existing) | Ensure correct device is used (already implemented) |
| 3 | Hardware Key (Device) | Detect fingerprint modifications after enrollment |

---

## Technical How It Works

### Android KeyStore API

**Key Creation:**
```kotlin
val keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES,
    "AndroidKeyStore"
)

val keyParams = KeyGenParameterSpec.Builder(
    "nss_attendance_key",  // Fixed key name (same for all students)
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
    .setUserAuthenticationRequired(true)  // Requires biometric to use
    .setInvalidatedByBiometricEnrollment(true)  // ⭐ AUTO-INVALIDATE ON CHANGES
    .build()

keyGenerator.init(keyParams)
keyGenerator.generateKey()  // Key created and stored in secure hardware
```

**What `setInvalidatedByBiometricEnrollment(true)` Does:**
- Android OS monitors biometric enrollment state
- If ANY fingerprint is added or removed → Android automatically DELETES this key
- Happens in background, even when app is not running
- No code needed to detect the change

**Key Name:**
- Fixed constant in code: `"nss_attendance_key"`
- Same name for all students (keys are isolated per device, no conflict)
- Never changes, never generated dynamically

---

## Database Structure

### Firebase: Existing `users` Collection

**Add ONE new field to existing user documents:**

```javascript
users/
  └── {rollNumber}/  // e.g., "2301CS16"
      {
        // ... all existing fields (name, uid, rollNumber, etc.) ...
        
        // NEW FIELD - Add this:
        "biometricEnrolledAt": 1705745400000  // Unix timestamp (Number)
        
        // Note: Reuse existing device ID field for device validation
      }
```

**Field Details:**
- **Type:** Number (Unix timestamp in milliseconds)
- **Null/Empty:** Indicates student has NOT enrolled biometric
- **Set:** When student completes first biometric enrollment
- **Reset:** When admin allows re-enrollment (set to null or delete field)

---

## Implementation Flow

### First Attendance (Enrollment)

```
1. Student arrives at event
2. Completes all existing checks (QR, location, device ID, etc.) ✓
3. App checks Firebase: Is "biometricEnrolledAt" empty?
   → YES (null or field doesn't exist) → This is FIRST enrollment

4. Show instruction: "Ensure you have only YOUR fingerprint on this device"
5. Trigger BiometricPrompt
6. Student authenticates with their fingerprint
7. On success:
   a. Create hardware key with name "nss_attendance_key"
   b. Save to Firebase: biometricEnrolledAt = current timestamp
8. Mark attendance ✓
```

**Code Logic:**
```kotlin
val enrolledAt = firestore.collection("users")
    .document(rollNumber)
    .get()
    .await()
    .getLong("biometricEnrolledAt")

if (enrolledAt == null) {
    // First time - enroll
    showBiometricEnrollment()
}
```

---

### Tampering Detection (Student Adds Another Fingerprint)

```
After enrollment, student goes to Settings and adds Person A's fingerprint:

What happens automatically (no code needed):
1. Android detects: Biometric enrollments changed
2. Android finds key named "nss_attendance_key" with auto-invalidate flag
3. Android DELETES the key from KeyStore
4. Key is permanently destroyed
5. Firebase unchanged (still has enrollment date)
```

---

### Subsequent Attendance (Detection)

```
1. Student arrives at event
2. Completes all existing checks ✓
3. App checks Firebase: Is "biometricEnrolledAt" empty?
   → NO (timestamp exists) → Student is already enrolled

4. App tries to load hardware key from KeyStore:
   val key = keyStore.getKey("nss_attendance_key", null)

5. Check key:
   
   Case A: Key exists (valid)
   → Fingerprints unchanged since enrollment ✓
   → Proceed to biometric authentication
   → Mark attendance
   
   Case B: Key is null (missing)
   → Key was invalidated (fingerprints modified) ❌
   → Show error: "Biometric enrollments changed. Contact professor."
   → Block attendance
   → Flag for admin review
```

**Code Logic:**
```kotlin
val enrolledAt = firestore.collection("users")
    .document(rollNumber)
    .get()
    .await()
    .getLong("biometricEnrolledAt")

if (enrolledAt != null) {
    // Already enrolled - check hardware key
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    
    val key = try {
        keyStore.getKey("nss_attendance_key", null)
    } catch (e: Exception) {
        null
    }
    
    if (key == null) {
        // Key invalidated - tampering detected!
        showError("Biometric enrollments changed. Contact professor.")
        blockAttendance()
    } else {
        // Key valid - proceed
        authenticateWithBiometric(key)
    }
}
```

---

## Biometric Authentication Configuration

**CRITICAL: Use fingerprint-only, NO fallback to PIN/password**

```kotlin
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Mark Attendance")
    .setSubtitle("Authenticate with your fingerprint")
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    // ⬆️ ONLY biometric - NO device credentials (PIN/password)
    .setNegativeButtonText("Cancel")
    .build()

// Use with CryptoObject for hardware key validation
val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
cipher.init(Cipher.ENCRYPT_MODE, key)

biometricPrompt.authenticate(
    promptInfo,
    BiometricPrompt.CryptoObject(cipher)
)
```

**What this enforces:**
- Only fingerprint authentication works
- PIN, password, pattern unlock will NOT work
- No fallback authentication methods
- If fingerprint sensor broken → Student must contact professor

---

## Admin Re-enrollment Process

### When Student is Locked Out

**Scenario:** Student added/removed fingerprints, key invalidated, attendance blocked

**Admin Reset Procedure:**

1. **Student contacts professor:**
   - "App says biometric enrollments changed"

2. **Professor verifies clean state:**
   - Instruct student: "Go to Settings → Security → Fingerprint"
   - Student must remove ALL fingerprints
   - Student must add ONLY their own fingerprint (1 fingerprint only)
   - Verify via screenshot or in-person

3. **Professor resets enrollment in Firebase:**
   ```
   Firebase Console:
   └── users/{rollNumber}
       └── Set "biometricEnrolledAt" to null (or delete the field)
   ```

4. **Student re-enrolls:**
   - Student opens app
   - App detects: biometricEnrolledAt is null → First enrollment
   - Student completes enrollment again
   - New hardware key created
   - New timestamp saved to Firebase

5. **Student can mark attendance again**

---

## Edge Cases & Handling

### 1. Device Has No Biometric Hardware

**Detection:**
```kotlin
val biometricManager = BiometricManager.from(context)
when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        showError("Your device doesn't support fingerprint authentication")
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
        showError("Fingerprint sensor currently unavailable")
}
```

**Action:** Show error, student contacts professor for manual attendance

### 2. Student Hasn't Set Up Fingerprint in Settings

**Detection:**
```kotlin
BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
    showError("Please set up fingerprint in your phone settings first")
```

**Action:** Student must add fingerprint in Android Settings, then try again

### 3. Student Gets New Phone

**Detection:**
- Existing device check will catch this (different device ID)

**Action:**
- Professor deletes `biometricEnrolledAt` field
- Student re-enrolls on new device

### 4. Multiple Fingerprints Already Enrolled Before First Attendance

**Limitation:** Android provides NO API to count enrolled fingerprints

**Mitigation:**
- **Manual verification at first enrollment:** Professor/TA verifies only 1 fingerprint during enrollment session
- **OR** Trust + detection: Allow enrollment, rely on hardware key to catch if they add more later
- **OR** Screenshot verification: Student screenshots fingerprint settings during enrollment

---

## Security Considerations

### What This Prevents

✅ **Person A using Person B's phone (after enrollment):**
- If Person B adds Person A's fingerprint AFTER enrollment
- Hardware key invalidates
- Next attendance attempt blocked

✅ **Tampering detection:**
- Any modification to fingerprints after enrollment is detected
- No way to bypass (hardware-level security)

✅ **Device credential bypass:**
- Cannot use PIN/password to authenticate
- Only fingerprint works

### What This Doesn't Prevent (Limitations)

⚠️ **Pre-enrollment collusion:**
- If Person B adds Person A's fingerprint BEFORE first enrollment
- Both fingerprints exist when hardware key is created
- Key binds to this state (2 fingerprints)
- Can only be prevented by manual verification at enrollment

⚠️ **Legitimate multiple fingerprints:**
- Student might legitimately have multiple of their own fingers enrolled
- System can't distinguish "own thumb + own index" vs "own thumb + friend's thumb"
- Policy enforcement required: "Only 1 fingerprint allowed"

### Bypass Difficulty

| Attack Method | Difficulty | Prevention |
|---------------|------------|------------|
| Add fingerprint after enrollment | Impossible | Hardware key auto-invalidates ✓ |
| Remove fingerprint after enrollment | Impossible | Hardware key auto-invalidates ✓ |
| Enroll with 2+ fingerprints initially | Possible | Manual verification at enrollment |
| Factory reset phone | Detectable | New device ID, pattern analysis |
| Root device | Difficult | Use SafetyNet/Play Integrity API (optional) |
| Modified APK | Difficult | ProGuard + Firebase Security Rules |

---

## Implementation Components

### Required Files to Create/Modify

1. **BiometricEnrollmentManager.kt** (NEW)
   - Handles hardware key creation
   - Manages enrollment flow
   - Validates key integrity

2. **AttendanceViewModel.kt** (MODIFY)
   - Add biometric check before marking attendance
   - Integration with existing attendance flow

3. **Firebase Security Rules** (UPDATE)
   - Ensure students can only create enrollment, not modify
   - Only admins can delete enrollment data

4. **User model** (MODIFY)
   - Add `biometricEnrolledAt` field

### Minimal Code Structure

```kotlin
class BiometricEnrollmentManager(
    private val context: Context,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val KEY_NAME = "nss_attendance_key"
    }
    
    fun isEnrolled(rollNumber: String): Boolean {
        // Check Firebase for enrollment date
    }
    
    fun createEnrollmentKey(): Boolean {
        // Create hardware key with auto-invalidate flag
    }
    
    fun isKeyValid(): Boolean {
        // Check if hardware key exists and is valid
    }
    
    fun enrollStudent(rollNumber: String, onSuccess: () -> Unit) {
        // Complete enrollment flow
    }
    
    fun showBiometricPrompt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Show BiometricPrompt with BIOMETRIC_STRONG only
    }
}
```

---

## Firebase Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{rollNumber} {
      // Students can create their enrollment data
      allow create: if request.auth.token.rollNumber == rollNumber;
      
      // Students can read their own data
      allow read: if request.auth.token.rollNumber == rollNumber 
                  || request.auth.token.isAdmin == true;
      
      // Students CANNOT modify or delete enrollment data
      // This prevents self-reset without admin approval
      allow update: if request.auth.token.isAdmin == true;
      allow delete: if request.auth.token.isAdmin == true;
    }
  }
}
```

---

## Testing Checklist

### Happy Path
- [ ] First attendance → Enrollment screen shown
- [ ] Biometric authentication succeeds → Enrollment saved
- [ ] Second attendance → No enrollment, direct to biometric auth
- [ ] Biometric succeeds → Attendance marked

### Tampering Detection
- [ ] Enroll student
- [ ] Add another fingerprint in Settings
- [ ] Try attendance → Error shown, blocked
- [ ] Verify Firebase has enrollment date but key is invalid

### Admin Reset
- [ ] Delete `biometricEnrolledAt` field in Firebase
- [ ] Student opens app → Enrollment screen shown
- [ ] Can re-enroll successfully

### Edge Cases
- [ ] Device with no biometric hardware → Appropriate error
- [ ] No fingerprints enrolled in Settings → Appropriate error
- [ ] Different device → Block with device mismatch error
- [ ] Cancel biometric prompt → Attendance not marked

---

## Privacy & Compliance

**Data Stored:**
- ✅ Enrollment timestamp (when student enrolled)
- ❌ NO fingerprint data
- ❌ NO biometric patterns
- ❌ NO fingerprint images

**What Android Provides:**
- Biometric authentication success/failure only
- Hardware key invalidation detection
- No access to actual biometric data

**GDPR/Privacy Compliant:**
- No biometric data leaves the device
- Biometric data never accessible to app
- Only enrollment metadata stored in Firebase

---

## Recommended Implementation Order

1. **Phase 1: Core Infrastructure**
   - Create `BiometricEnrollmentManager` class
   - Implement hardware key creation/validation
   - Add `biometricEnrolledAt` field to Firebase

2. **Phase 2: Integration**
   - Integrate with existing attendance flow
   - Add enrollment screen UI
   - Update attendance marking logic

3. **Phase 3: Admin Tools**
   - Update Firebase security rules
   - Document admin reset procedure
   - Create admin reset function (optional)

4. **Phase 4: Testing & Rollout**
   - Test all scenarios
   - Pilot with small group
   - Full rollout with clear policy communication

---

## Policy Requirements (Professor Enforcement)

**Communicate to students:**

1. **One Fingerprint Only Rule:**
   - "You must have ONLY your own fingerprint enrolled on your device"
   - "Multiple fingerprints will lock your attendance access"

2. **No Modifications During Semester:**
   - "Do not add or remove fingerprints during the semester"
   - "Any changes will require re-enrollment with professor approval"

3. **Initial Enrollment Verification:**
   - First enrollment happens in supervised session OR
   - Submit screenshot of fingerprint settings showing 1 fingerprint

4. **Violation Consequences:**
   - Automatic lock-out if enrollments change
   - Must meet with professor to re-enroll
   - Repeated violations may result in attendance penalties

---

## Summary

**Minimal Changes Required:**
- Add 1 field to Firebase: `biometricEnrolledAt`
- Create biometric enrollment manager
- Integrate into existing attendance flow
- Enforce "1 fingerprint only" policy at enrollment

**Security Gained:**
- Hardware-backed tampering detection
- Automatic invalidation on fingerprint changes
- No-bypass biometric authentication (no PIN fallback)
- Multi-layer defense against proxy attendance

**Admin Overhead:**
- Initial enrollment verification (one-time)
- Occasional re-enrollment requests (when students modify fingerprints)
- Simple Firebase field deletion for resets

---

**Implementation Date:** To be determined  
**Current Status:** Planning/Documentation phase  
**Next Step:** Create implementation plan when ready to proceed
