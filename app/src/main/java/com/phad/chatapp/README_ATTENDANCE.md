# Attendance System Documentation

## Overview

The attendance system is a feature that allows users to mark their attendance using facial recognition and location tracking. It also provides an admin interface for approving or rejecting attendance requests.

## Features

- **Face Recognition**: Uses ML Kit to detect and compare faces for attendance verification
- **Location Tracking**: Captures the user's location when marking attendance
- **Admin Approval**: Allows administrators to review and approve/reject attendance requests
- **Reference Images**: Uses stored reference images for face comparison
- **Cloudinary Integration**: For secure storage of attendance images

## Components

### Models

- `Attendance`: Represents an attendance record with status (pending, approved, rejected)
- `AttendanceApprovalItem`: Data model for displaying attendance items in the admin approval list
- `User`: Extended with `referenceImageUrl` for face recognition

### Repositories

- `AttendanceRepository`: Handles database operations for attendance records
- `UserRepository`: Enhanced with methods to retrieve users by roll number and update reference images

### Services

- `AttendanceService`: Manages attendance submission, approval, and rejection
- `CloudinaryService`: Handles image upload to Cloudinary
- `FaceRecognitionService`: Provides face detection and comparison functionality
- `LocationService`: Manages location tracking for attendance

### UI Components

- `AttendanceManagerFragment`: UI for users to mark their attendance
- `AdminAttendanceApprovalFragment`: UI for admins to approve/reject attendance requests
- `AttendanceApprovalAdapter`: RecyclerView adapter for the approval list
- `Dialog layouts`: For detailed attendance viewing and approval

## Workflow

1. **Marking Attendance**:
   - User opens the Attendance Manager
   - App captures a selfie or user uploads an image
   - App retrieves the user's current location
   - Face is detected and compared with the reference image
   - If match is successful, attendance is auto-approved; otherwise, it's sent for manual approval

2. **Approving Attendance**:
   - Admin opens the Attendance Approval screen
   - System displays all pending attendance requests
   - Admin can view details, including submitted and reference images
   - Admin approves or rejects each request

## Firebase Structure

### Collections

- `attendances`: Stores attendance records
  - Fields: id, rollNumber, timestamp, location, imageUrl, status, verificationMethod

- `users`: Extended with reference image URLs
  - Fields: (existing fields), referenceImageUrl

## Integration Points

- **MainActivity**: Navigation methods for attendance screens
- **ProfileFragment**: UI buttons for accessing attendance features
- **Main Menu**: Options for marking and approving attendance

## Security Considerations

- Images are stored securely in Cloudinary
- Only admins can approve/reject attendance requests
- Location data is only collected during attendance marking

## Future Enhancements

- Implement more sophisticated face recognition algorithms
- Add attendance reports and analytics
- Support for group attendance marking
- QR code-based attendance as an alternative to face recognition 