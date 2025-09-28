import os
import face_recognition
import numpy as np
import requests
from io import BytesIO
from PIL import Image
from flask import Flask, request, jsonify
import logging

# Configure logging
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

# In-memory cache for face encodings to improve performance
face_encoding_cache = {}

def download_image(image_url):
    """
    Download an image from a URL and convert to RGB format
    """
    try:
        logger.info(f"Downloading image from {image_url}")
        response = requests.get(image_url)
        response.raise_for_status()
        img = Image.open(BytesIO(response.content))
        return np.array(img.convert('RGB'))
    except Exception as e:
        logger.error(f"Error downloading image: {str(e)}")
        raise

def get_face_encoding(image_url, use_cache=True):
    """
    Get face encoding from an image URL with optional caching
    """
    if use_cache and image_url in face_encoding_cache:
        logger.info(f"Using cached face encoding for {image_url}")
        return face_encoding_cache[image_url]
    
    try:
        # Download and process the image
        image = download_image(image_url)
        
        # Detect face locations in the image
        face_locations = face_recognition.face_locations(image, model="hog")
        
        if not face_locations:
            logger.warning(f"No faces detected in {image_url}")
            return None
        
        # Use the first face if multiple faces are detected
        if len(face_locations) > 1:
            logger.warning(f"Multiple faces detected in {image_url}, using the first one")
        
        # Get face encoding
        face_encodings = face_recognition.face_encodings(image, face_locations)
        
        if not face_encodings:
            logger.warning(f"Could not encode face in {image_url}")
            return None
        
        # Cache the encoding if requested
        if use_cache:
            face_encoding_cache[image_url] = face_encodings[0]
        
        return face_encodings[0]
    except Exception as e:
        logger.error(f"Error getting face encoding: {str(e)}")
        return None

@app.route('/compare-faces', methods=['POST'])
def compare_faces():
    """
    Compare two faces and return match result
    """
    try:
        # Get image URLs from request
        reference_image_url = request.form.get('reference_image_url')
        submitted_image_url = request.form.get('submitted_image_url')
        
        if not reference_image_url or not submitted_image_url:
            return jsonify({
                'error': 'Missing required parameters: reference_image_url and submitted_image_url'
            }), 400
        
        logger.info(f"Comparing faces: {reference_image_url} vs {submitted_image_url}")
        
        # Get face encodings
        reference_encoding = get_face_encoding(reference_image_url)
        submitted_encoding = get_face_encoding(submitted_image_url, use_cache=False)
        
        if reference_encoding is None:
            return jsonify({
                'error': 'No face detected in reference image'
            }), 400
        
        if submitted_encoding is None:
            return jsonify({
                'error': 'No face detected in submitted image'
            }), 400
        
        # Calculate face distance
        face_distance = face_recognition.face_distance([reference_encoding], submitted_encoding)[0]
        
        # Convert distance to confidence score (0-1)
        confidence = 1.0 - face_distance
        
        # Determine if it's a match (threshold can be adjusted)
        threshold = 0.6
        is_match = face_distance <= threshold
        
        logger.info(f"Face comparison result: match={is_match}, confidence={confidence:.4f}, distance={face_distance:.4f}")
        
        return jsonify({
            'is_match': bool(is_match),
            'confidence': float(confidence),
            'face_distance': float(face_distance)
        })
    except Exception as e:
        logger.error(f"Error in face comparison: {str(e)}")
        return jsonify({
            'error': f'Internal server error: {str(e)}'
        }), 500

@app.route('/register-face', methods=['POST'])
def register_face():
    """
    Register a face for a user
    """
    try:
        # Get parameters from request
        roll_number = request.form.get('roll_number')
        image_url = request.form.get('image_url')
        
        if not roll_number or not image_url:
            return jsonify({
                'error': 'Missing required parameters: roll_number and image_url'
            }), 400
        
        logger.info(f"Registering face for roll number: {roll_number}")
        
        # Get face encoding
        face_encoding = get_face_encoding(image_url)
        
        if face_encoding is None:
            return jsonify({
                'success': False,
                'message': 'No face detected in the image'
            })
        
        # Store the encoding in cache
        face_encoding_cache[image_url] = face_encoding
        
        return jsonify({
            'success': True,
            'message': f'Face registered successfully for roll number {roll_number}'
        })
    except Exception as e:
        logger.error(f"Error in face registration: {str(e)}")
        return jsonify({
            'success': False,
            'message': f'Error: {str(e)}'
        })

@app.route('/health', methods=['GET'])
def health_check():
    """
    Health check endpoint
    """
    return jsonify({
        'status': 'ok',
        'version': '1.0.0'
    })

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False) 