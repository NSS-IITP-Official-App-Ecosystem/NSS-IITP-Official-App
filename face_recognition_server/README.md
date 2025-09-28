# Face Recognition Server

A Python Flask server for face recognition using the `face_recognition` library. This server provides APIs for face comparison and registration.

## Features

- Face comparison between two images
- Face registration for users
- In-memory caching for better performance
- Dockerized for easy deployment

## API Endpoints

### 1. Compare Faces

**Endpoint:** `/compare-faces`
**Method:** POST
**Parameters:**
- `reference_image_url`: URL of the reference face image
- `submitted_image_url`: URL of the submitted face image

**Response:**
```json
{
  "is_match": true,
  "confidence": 0.85,
  "face_distance": 0.15
}
```

### 2. Register Face

**Endpoint:** `/register-face`
**Method:** POST
**Parameters:**
- `roll_number`: Roll number of the user
- `image_url`: URL of the face image to register

**Response:**
```json
{
  "success": true,
  "message": "Face registered successfully for roll number 2201CE49"
}
```

### 3. Health Check

**Endpoint:** `/health`
**Method:** GET

**Response:**
```json
{
  "status": "ok",
  "version": "1.0.0"
}
```

## Deployment Instructions

### Local Development

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Run the server:
```bash
python app.py
```

### Docker Deployment

1. Build the Docker image:
```bash
docker build -t face-recognition-server .
```

2. Run the Docker container:
```bash
docker run -p 5000:5000 face-recognition-server
```

### Cloud Deployment

#### Google Cloud Run

1. Build and push the Docker image:
```bash
gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/face-recognition-server
```

2. Deploy to Cloud Run:
```bash
gcloud run deploy face-recognition-service --image gcr.io/YOUR_PROJECT_ID/face-recognition-server --platform managed
```

## Configuration

The server uses the following configuration:

- Face match threshold: 0.6 (lower values are stricter)
- Face detection model: HOG (faster but less accurate than CNN)
- In-memory caching for face encodings

## Dependencies

- Flask: Web framework
- face_recognition: Face detection and recognition
- NumPy: Numerical operations
- Pillow: Image processing
- Requests: HTTP client 