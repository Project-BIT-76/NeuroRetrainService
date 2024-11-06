import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from tensorflow.keras import models, layers
import argparse

def train_model(data_file, model_path):
    # Загрузка данных
    data = pd.read_csv(data_file, delimiter=';')

    # Разделение на признаки и целевую переменную
    X = data.drop('quality', axis=1)
    y = data['quality']

    # Преобразование метки качества в бинарные классы (например, плохое (0) и хорошее (1) вино)
    y = (y >= 6).astype(int)  # Качество >= 6 считается хорошим, иначе плохим

    # Разделение на обучающую и тестовую выборки
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    # Масштабирование данных
    scaler = StandardScaler()
    X_train = scaler.fit_transform(X_train)
    X_test = scaler.transform(X_test)

    # Создание модели нейронной сети
    model = models.Sequential()

    # Добавление слоев
    model.add(layers.Dense(64, activation='relu', input_shape=(X_train.shape[1],)))
    model.add(layers.Dense(32, activation='relu'))
    model.add(layers.Dense(16, activation='relu'))

    # Выходной слой
    model.add(layers.Dense(1, activation='sigmoid'))

    # Компиляция модели
    model.compile(optimizer='adam',
                  loss='binary_crossentropy',
                  metrics=['accuracy'])

    # Обучение модели
    history = model.fit(X_train, y_train, epochs=50, batch_size=32, validation_data=(X_test, y_test))

    # Сохранение модели
    model.save(model_path)

    # Оценка модели на тестовой выборке
    test_loss, test_acc = model.evaluate(X_test, y_test)

    print(f"Test accuracy: {test_acc:.4f}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Train a neural network model.')
    parser.add_argument('--data', type=str, required=True, help='Path to the data file.')
    parser.add_argument('--model', type=str, required=True, help='Path to save the trained model.')
    
    args = parser.parse_args()
    
    train_model(args.data, args.model)
