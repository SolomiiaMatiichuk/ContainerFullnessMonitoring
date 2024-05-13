from flask import Flask, jsonify, request
from flask_sqlalchemy  import SQLAlchemy
from datetime import datetime
from flask_migrate import Migrate
from sqlalchemy import desc
import os

app = Flask(__name__)

DATABASE_URL = os.environ['DATABASE_URL']
DATABASE_URL = str(DATABASE_URL).replace("postgres://", "postgresql://", 1)
app.config['SQLALCHEMY_DATABASE_URI'] = DATABASE_URL
db = SQLAlchemy(app)
migrate = Migrate(app, db)


class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(50), unique=True, nullable=False)
    #containers = db.relationship('Container', backref='appuser', lazy=True)

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

@app.route('/')
def index():

    existing_user = User.query.get(1)
    if not existing_user:
        new_user = User(id=1, username='default_user')
        db.session.add(new_user)
        db.session.commit()
    return 'Hello, world! This is my server application.'



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
