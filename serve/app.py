import json
from flask import Flask, request, jsonify, render_template, Response
import requests
import base64

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/describe', methods=['POST'])
def describe():
    try:
        
        if not request.is_json:
            return jsonify({"error": "Invalid content type, must be JSON"}), 400

        data = request.get_json()
        encoded_string = data.get("image")
        if not encoded_string:
            return jsonify({"error": "No image data provided"}), 400

        image_data = [{"data": encoded_string, "id": 12}]
        payload = {
            "prompt": "USER:[img-12]Describe the image briefly and accurately.\nASSISTANT:", 
            "n_predict": 128, 
            "image_data": image_data, 
            "stream": True
        }
        headers = {"Content-Type": "application/json"}
        url = "http://127.0.0.1:8080/completion"  # llama endpoint

        # Send request to LLaVA model
        response = requests.post(url, headers=headers, json=payload, stream=True)
        response.raise_for_status()  # Raise error for HTTP failures

        # Stream response back to client
        def generate():
            for chunk in response.iter_content(chunk_size=1024):
                if chunk:
                    try:
                        chunk_json = json.loads(chunk.decode().split("data: ")[1])
                        content = chunk_json.get("content", "")
                        if content:
                            yield content
                    except (json.JSONDecodeError, IndexError):
                        continue  # Skip malformed chunks

        return Response(generate(), content_type='text/plain')

    except requests.exceptions.RequestException as e:
        return jsonify({"error": f"Error communicating with LLaVA server: {str(e)}"}), 500
    except Exception as e:
        return jsonify({"error": f"Internal server error: {str(e)}"}), 500

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
