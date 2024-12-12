import sys
sys.path.append("/tf/data/")
import os
import json
import tensorflow as tf
from tensorflow.keras.callbacks import EarlyStopping
from tensorflow.keras.models import load_model
from peak_hour_frame.layers.transformer.causal_self_attention import CausalSelfAttention
from peak_hour_frame.layers.transformer.cross_attention import CrossAttention
from peak_hour_frame.layers.transformer.encoder_layer import EncoderLayer
from peak_hour_frame.layers.transformer.feed_forward import FeedForward
from peak_hour_model.model import PredictionModel
from peak_hour_frame.util.data_processing import load_data_from_csv
from peak_hour_frame.util.save_model_with_accuracy import SaveModelWithAccuracy
from peak_hour_frame.util.util import find_best_model
import argparse
import requests

def train_model(data_file, model_path, json_filename=None):
    n_input = 93*24
    n_out_labels = 46
    n_out = n_out_labels * 24
    n_delay = 31 * 24
    n_test_labels = 365 + 124
    n_test = n_test_labels * 24

    save_dir = data_file
    os.makedirs(save_dir, exist_ok=True)
    os.makedirs(model_path, exist_ok=True)

    # Ожидаемые формы данных
    data_shapes = {
        'train_x': (1380, 2232, 8),
        'train_holiday': (1380, 46, 4),
        'train_pphour': (1380, 1104, 5),
        'train_y_cl': (1380, 46, 1)
    }

    # Загрузка данных
    loaded_data = load_data_from_csv(save_dir, data_shapes=data_shapes)
    train_x = loaded_data['train_x']
    train_holiday = loaded_data['train_holiday']
    train_pphour = loaded_data['train_pphour']
    train_y_cl = loaded_data['train_y_cl']

    input_data_description = {
        'train_x': {
            'shape': train_x.shape,
            'timesteps': train_x.shape[1],
            'features': train_x.shape[2],
            'description': "Historical consumption data"
        },
        'train_holiday': {
            'shape': train_holiday.shape,
            'timesteps': train_holiday.shape[1],
            'features': train_holiday.shape[2],
            'description': "Holiday indicator data"
        },
        'train_pphour': {
            'shape': train_pphour.shape,
            'timesteps': train_pphour.shape[1],
            'features': train_pphour.shape[2],
            'description': "Power price per hour data"
        }
    }

    output_data_description = {
        'train_y_cl': {
            'shape': train_y_cl.shape,
            'timesteps': train_y_cl.shape[1],
            'features': train_y_cl.shape[2],
            'description': "Consumption prediction output"
        }
    }

    data_timesteps, data_features = train_x.shape[1], train_x.shape[2]
    pphour_timesteps, pphour_features = train_pphour.shape[1], train_pphour.shape[2]
    holiday_timesteps, holiday_features = train_holiday.shape[1], train_holiday.shape[2]

    model_transformer = PredictionModel(
        data_timesteps=data_timesteps,
        data_features=data_features,
        holiday_timesteps=holiday_timesteps,
        holiday_features=holiday_features
    )
    model_transformer.compile(
        loss='sparse_categorical_crossentropy',
        optimizer='adagrad',
        metrics=['sparse_categorical_accuracy', 'accuracy']
    )

    # Early stopping и callback для сохранения модели
    early_stop = EarlyStopping(monitor='val_sparse_categorical_accuracy', patience=20)
    callbacks = [early_stop, SaveModelWithAccuracy(model_path)]

    # Обучение модели
    history = model_transformer.fit(
        [train_x, train_holiday], train_y_cl,
        epochs=1,
        batch_size=1,
        validation_split=0.2,
        callbacks=callbacks
    )

    # Загрузка лучшей модели, если она существует
    loaded_model = model_transformer
    best_model_path = find_best_model(model_path)
    if best_model_path:
        loaded_model = load_model(best_model_path, custom_objects={
            'CausalSelfAttention': CausalSelfAttention,
            'EncoderLayer': EncoderLayer,
            'CrossAttention': CrossAttention,
            'FeedForward': FeedForward,
        })
        print(f"Loaded model from {best_model_path}")
    else:
        print("No saved model found.")

    tf.saved_model.save(loaded_model, model_path)

    if json_filename:
        model_description = {
            "inputDescription": input_data_description,
            "outputDescription": output_data_description
        }

        json_path = os.path.join(model_path, json_filename)
        with open(json_path, 'w') as json_file:
            json.dump(model_description, json_file, indent=4)
        print(f"Model description saved to {json_path}")



if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Train a neural network model.')
    parser.add_argument('--data', type=str, required=True, help='Path to the data file.')
    parser.add_argument('--model', type=str, required=True, help='Path to save the trained model.')
    parser.add_argument('--json', type=str, required=False, default="model_description.json", help='Name of JSON file to save model description.')

    # Чтение аргументов
    args = parser.parse_args()

    # Запуск тренировки с переданными параметрами
    train_model(args.data, args.model, args.json)
