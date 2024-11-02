from flask import Flask, jsonify, request, url_for
from flask_sqlalchemy  import SQLAlchemy
from datetime import datetime, timedelta
from flask_migrate import Migrate
from flask_bcrypt import Bcrypt
from flask_jwt_extended import JWTManager, create_access_token, jwt_required, get_jwt_identity
from sqlalchemy import desc
import os
from itsdangerous import URLSafeTimedSerializer
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

app = Flask(__name__)

DATABASE_URL = os.environ['DATABASE_URL']
DATABASE_URL = str(DATABASE_URL).replace("postgres://", "postgresql://", 1)
app.config['SQLALCHEMY_DATABASE_URI'] = DATABASE_URL
app.config['SECRET_KEY'] = 'your_secret_key'
app.config['JWT_SECRET_KEY'] = 'your_jwt_secret_key'  # Secret key for JWT
app.config['JWT_ACCESS_TOKEN_EXPIRES'] = timedelta(hours=1)  # Token expiry time
db = SQLAlchemy(app)
bcrypt = Bcrypt(app)
jwt = JWTManager(app)
migrate = Migrate(app, db)


class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(120), unique=True, nullable=False)
    username = db.Column(db.String(50), nullable=False)
    password = db.Column(db.String(200), nullable=False)  # Hashed password
    role = db.Column(db.String(20), nullable=False, default="user")
    reset_confirmed = db.Column(db.Boolean, default=False)  

class Container(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    name = db.Column(db.String(50), nullable=False)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    length = db.Column(db.Float, nullable=False)
    latest_distance = db.Column(db.Float, nullable=False)
   # fullness_data = db.relationship('FullnessData', backref='container', lazy=True)

class FullnessData(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    container_id = db.Column(db.Integer, db.ForeignKey('container.id'), nullable=False)
    fullness = db.Column(db.Integer, nullable=False)
    timestamp = db.Column(db.DateTime, nullable=False)


def send_email(recipient, subject, body):
    sender_email = "hoshko.bohdan.m@gmail.com"
    sender_password = "hrbd krid prri ivmp"

    # Set up the MIME
    message = MIMEMultipart()
    message['From'] = sender_email
    message['To'] = recipient
    message['Subject'] = subject
    message.attach(MIMEText(body, 'plain'))

    try:
        # Connect to Gmail SMTP server
        with smtplib.SMTP('smtp.gmail.com', 587) as server:
            server.starttls()  # Secure the connection
            server.login(sender_email, sender_password)
            server.sendmail(sender_email, recipient, message.as_string())
        print("Email sent successfully.")
    except Exception as e:
        print(f"Error sending email: {e}")


# Generate confirmation token for password reset
def generate_reset_token(email):
    serializer = URLSafeTimedSerializer(app.config['SECRET_KEY'])
    return serializer.dumps(email, salt='password-reset-salt')

# Confirm the reset token
def confirm_reset_token(token, expiration=3600):
    serializer = URLSafeTimedSerializer(app.config['SECRET_KEY'])
    try:
        email = serializer.loads(token, salt='password-reset-salt', max_age=expiration)
    except:
        return False
    return email

# Endpoint to request password reset
@app.route('/forgot-password', methods=['POST'])
def forgot_password():
    # Get email from query parameters instead of JSON body
    email = request.args.get('email')
    
    if not email:
        return jsonify({"msg": "Email is required"}), 400
    
    user = User.query.filter_by(email=email).first()
    
    if not user:
        return jsonify({"msg": "Email not registered"}), 400
    
    # Generate reset token and URL
    token = generate_reset_token(email)
    reset_url = url_for('confirm_reset', token=token, _external=True)
    
    # Send email with reset link
    send_email(email, "Reset Your Password", f"Click the link to reset your password: {reset_url}")
    
    return jsonify({"msg": "Password reset link has been sent to your email"}), 200


@app.route('/confirm-reset/<token>', methods=['GET'])
def confirm_reset(token):
    email = confirm_reset_token(token)
    
    if not email:
        return jsonify({"msg": "The reset link is invalid or has expired"}), 400

    # Fetch the user and mark reset as confirmed
    user = User.query.filter_by(email=email).first()
    if not user:
        return jsonify({"msg": "User not found"}), 404

    user.reset_confirmed = True
    db.session.commit()

    return jsonify({"msg": "Password reset confirmed. You may now set a new password."}), 200


# Check if password reset is confirmed (for app polling)
@app.route('/checkPasswordResetConfirmed', methods=['GET'])
def check_password_reset_confirmed():
    email = request.args.get('email')
    user = User.query.filter_by(email=email).first()
    if user and user.reset_confirmed:
        return jsonify(True), 200
    return jsonify(False), 404



@app.route('/set-new-password', methods=['POST'])
def set_new_password():
    data = request.get_json()
    print("Received data:", data)  # Log received data for debugging
    email = data.get('email')
    new_password = data.get('password')

    # Validate inputs
    if not email or not new_password:
        return jsonify({"msg": "Email and password are required"}), 400

    # Fetch the user and ensure reset has been confirmed
    user = User.query.filter_by(email=email).first()
    if not user:
        return jsonify({"msg": "User not found"}), 404

    if not user.reset_confirmed:
        return jsonify({"msg": "Password reset has not been confirmed"}), 400

    # Update the password
    hashed_password = bcrypt.generate_password_hash(new_password).decode('utf-8')
    user.password = hashed_password
    user.reset_confirmed = False  # Reset the confirmation flag
    db.session.commit()

    return jsonify({"msg": "Password has been updated"}), 200


# Check if admin user exists and create if not
def create_admin():
    admin_email = "admin@gmail.com"
    admin_password = "admin_password"
    admin = User.query.filter_by(email=admin_email).first()
    if not admin:
        hashed_password = bcrypt.generate_password_hash(admin_password).decode('utf-8')
        admin = User(email=admin_email, username="Admin", password=hashed_password, role="admin")
        db.session.add(admin)
        db.session.commit()

# Function to generate confirmation token
def generate_confirmation_token(email):
    serializer = URLSafeTimedSerializer(app.config['SECRET_KEY'])
    return serializer.dumps(email, salt='email-confirmation-salt')

# Function to confirm the token
def confirm_token(token, expiration=3600):
    serializer = URLSafeTimedSerializer(app.config['SECRET_KEY'])
    try:
        email = serializer.loads(token, salt='email-confirmation-salt', max_age=expiration)
    except:
        return False
    return email

@app.route('/')
def index():
    create_admin()
    existing_user = User.query.get(1)
    if not existing_user:
        new_user = User(id=1, username='default_user', password='some', email='some@gmail.com')
        db.session.add(new_user)
        db.session.commit()
    return 'Hello, world! This is my server application.'


# Registration endpoint
@app.route('/register', methods=['POST'])
def register():
    data = request.get_json()
    email = data.get('email')
    username = data.get('username')
    password = data.get('password')

    if User.query.filter_by(email=email).first():
        return jsonify({"msg": "Email already registered"}), 400

    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')

    # Generate confirmation token
    token = generate_confirmation_token(email)
    confirm_url = url_for('confirm_email', token=token, username=username, password=hashed_password, _external=True)

    # Send confirmation email
    send_email(email, 'Confirm Your Account', f'Please confirm your email by clicking this link: {confirm_url}')

    return jsonify({"msg": "Please check your email to confirm your account."}), 200



@app.route('/confirm/<token>', methods=['GET'])
def confirm_email(token):
    email = confirm_token(token)
    if not email:
        return jsonify({"msg": "The confirmation link is invalid or has expired."}), 400

    username = request.args.get('username')
    hashed_password = request.args.get('password')

    
    new_user = User(email=email, username=username, password=hashed_password)
    db.session.add(new_user)
    db.session.commit()

    return jsonify({"msg": "Your account has been confirmed and registered. You may now log in."}), 200


# Login endpoint
@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')

    user = User.query.filter_by(email=email).first()
    if user and bcrypt.check_password_hash(user.password, password):
        access_token = create_access_token(identity={'email': user.email, 'username': user.username, 'role': user.role})
        return jsonify(access_token=access_token, real_name=user.username, email=user.email, role=user.role), 200
    return jsonify({"msg": "Invalid credentials"}), 401



@app.route('/check_confirmation', methods=['GET'])
def check_confirmation():
    email = request.args.get('email')
    user = User.query.filter_by(email=email).first()
    if user:
        return jsonify(True), 200  # Return True if confirmed, otherwise False
    return jsonify(False), 404


@app.route('/data-endpoint', methods=['POST'])
def receive_data():
    data = request.get_json()
    distance = data['distance']
    timestamp_str = data['timestamp']
    user_id = data['user_id']
    container_id = data['container_id']
    container_length = data['container_length']
    timestamp = datetime.strptime(timestamp_str, "%Y-%m-%d %H:%M:%S")

    # Save data to database
    user = User.query.get(user_id)
    if not user:
        return jsonify({'error': 'User not found'}), 404

    container = Container.query.filter_by(id=container_id, user_id=user_id).first()
    if not container:
        # Create a new container if not found
        container = Container(user_id=user_id, id=container_id, name=f'Container_{container_id}', latitude=0.0,
                              longitude=0.0, length=container_length, latest_distance=distance)
        db.session.add(container)
        db.session.commit()
    else:
        # update latest_distance for existing container
        container.latest_distance = distance
        container.length = container_length
        container.user_id = user_id
        db.session.commit()

    fullness_data = FullnessData(container_id=container_id, fullness=distance, timestamp=timestamp)
    db.session.add(fullness_data)
    db.session.commit()

    container.fullness_data_id = fullness_data.id
    db.session.commit()

    return jsonify({'message': 'Data received and stored successfully'}), 201


@app.route('/user-containers/<int:user_id>', methods=['GET'])
def get_user_containers(user_id):
    user = User.query.get(user_id)
    if not user:
        return jsonify({'error': 'User not found'}), 404

    containers = Container.query.filter_by(user_id=user_id).all()
    container_list = [{'id': container.id, 'name': container.name, 'latitude': container.latitude, 'longitude': container.longitude, 'length': container.length, 'latest_distance':container.latest_distance} for container in containers]

    return jsonify(container_list)


@app.route('/container-fullness/<int:container_id>', methods=['GET'])
def get_container_fullness(container_id):
    container = Container.query.get(container_id)
    if not container:
        return jsonify({'error': 'Container not found'}), 404

    fullness_data = FullnessData.query.filter_by(container_id=container_id).order_by(desc(FullnessData.timestamp)).all()
    data_list = [{'fullness': data.fullness, 'timestamp': data.timestamp.strftime('%Y-%m-%d %H:%M:%S')} for data in
                 fullness_data]

    return jsonify(data_list)


@app.route('/update-container-location/<int:container_id>', methods=['PUT'])
def update_container_location(container_id):
    container = Container.query.get(container_id)
    if not container:
        return jsonify({'error': 'Container not found'}), 404

    data = request.get_json()
    latitude = data.get('latitude')
    longitude = data.get('longitude')

    if latitude is None or longitude is None:
        return jsonify({'error': 'Latitude and longitude must be provided'}), 400

    container.latitude = latitude
    container.longitude = longitude
    db.session.commit()

    return jsonify({'message': 'Container location updated successfully'}), 200


@app.route('/clean-data/<int:user_id>', methods=['DELETE'])
def clean_data(user_id):
    user = User.query.get(user_id)
    if not user:
        return jsonify({'error': 'User not found'}), 404

    # Delete all containers and associated fullness data
    containers = Container.query.filter_by(user_id=user_id).all()
    for container in containers:
        FullnessData.query.filter_by(container_id=container.id).delete()
    Container.query.filter_by(user_id=user_id).delete()

    db.session.commit()

    return jsonify({'message': 'Data cleaned successfully'}), 200


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0')
