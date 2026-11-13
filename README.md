# Janken

Камень, ножницы, бумага с управлением жестами через камеру Android. Игра рисует скелет кисти поверх видео и показывает распознанный ход. Кнопки доступны в том же матче, поэтому способ ввода можно менять между ходами и во время раунда.

<img src="docs/android-game.png" width="320" alt="Игровой экран Janken на Android с кнопками выбора жеста.">

## Запуск

Откройте проект в Android Studio и запустите конфигурацию `app` на устройстве с Android 8.0 или новее. Для игры жестами выберите «Камера» и разрешите доступ. В режиме «Кнопки» можно играть без камеры.

```sh
./gradlew :app:assembleDebug
```

Для сборки нужны JDK 21 и Android SDK 36. На Windows используйте `gradlew.bat`. APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## Архитектура

- `domain` ведёт матч, выбирает ход бота и проверяет удержание жеста.
- `presentation` обрабатывает события игры через MVI и публикует состояние в `StateFlow`.
- `shared` отображает игру на Compose Multiplatform и Material 3.
- `camera` распознаёт ход по 2D-точкам кисти и метке модели. `app` подключает CameraX, MediaPipe и Android-хранилище настроек.

Распознавание работает на устройстве. В Android включён классификатор Janken с четырьмя классами, включая посторонние жесты. [Как проверяли распознавание](docs/MODEL_AND_TRAINING.md).

## Тесты

```sh
./gradlew :domain:jvmTest :presentation:jvmTest :camera:jvmTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Вторая команда запускает проверки MediaPipe на подключённом устройстве или эмуляторе. [Лицензии и источники](NOTICE.md).
