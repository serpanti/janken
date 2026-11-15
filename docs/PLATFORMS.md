# Сборка и проверки

Для Gradle нужны JDK 21 и Android SDK 36. Команды запускаются из корня репозитория. На Windows замените `./gradlew` на `.\gradlew.bat`.

| Платформа | Камера | Проверка |
| --- | --- | --- |
| Android 8.0+ | CameraX, MediaPipe с классификатором Janken | Четыре SDK-теста и восстановление сыгранного раунда после остановки приложения на эмуляторе. |
| Web | `getUserMedia`, MediaPipe в Web Worker | Chrome с тестовым видеопотоком. |
| Windows | OpenCV и MediaPipe в локальном Python-процессе | Распознавание на тестовых изображениях и запуск автономного пакета. |
| macOS и Linux | Desktop-адаптер на Kotlin/JVM и Python | Предусмотрена сборка на целевой системе. Физические камеры и установщики ожидают проверки. |
| iOS и iPadOS 15.4+ | AVFoundation и MediaPipe через Swift | Реализация есть в репозитории. Сборка и проверка на устройстве требуют Mac с Xcode. |

## Web

```sh
./gradlew :webApp:wasmJsBrowserDistribution
```

Результат находится в `webApp/build/dist/wasmJs/productionExecutable`. Раздайте каталог по HTTPS или через HTTP-сервер на localhost. Нужен браузер с WasmGC и разрешением на камеру. Стандартная модель MediaPipe и файлы SDK включены в сборку.

Room хранит историю в OPFS. При повторном открытии используйте тот же адрес и порт. Хранилище привязано к origin и профилю браузера. В один момент базу истории может открыть одна вкладка Janken на этом origin.

## Desktop

Для запуска из исходников нужен Python 3.10–3.12 с зависимостями из `desktop-camera/requirements.txt`.

```powershell
.\desktop-camera\setup-camera.ps1
.\gradlew.bat :desktopApp:run
```

```sh
sh desktop-camera/setup-camera.sh
./gradlew :desktopApp:run
```

Для автономного пакета установите `desktop-camera/requirements-build.txt` и выполните `./gradlew :desktopApp:createDistributable`. Нужен полный JDK с `jpackage` и `jlink`. Результат находится в `desktopApp/build/compose/binaries/main/app` и содержит среды Java и Python, модель и обработчик. Собирайте пакет на целевой ОС и архитектуре. Если инструменты упаковки Windows не читают путь с кириллицей, используйте каталог с латинскими буквами.

## iOS

```sh
cd iosApp
pod install
open Janken.xcworkspace
```

В Xcode выберите схему Janken. Для физического устройства настройте подпись и разрешите камеру после запуска. Цели Kotlin/Native — `iosArm64` и `iosSimulatorArm64`.

## Тесты

```sh
./gradlew :domain:desktopTest :presentation:desktopTest :camera:desktopTest :data:desktopTest
./gradlew :domain:wasmJsNodeTest :presentation:wasmJsNodeTest :camera:wasmJsNodeTest
./gradlew :app:lintDebug :app:connectedDebugAndroidTest
```

Для проверок desktop используйте `./gradlew :desktopApp:test` и `python desktop-camera/test_worker.py` с настроенным окружением камеры. Для web соберите production distribution, установите тестовые зависимости командой `npm ci --prefix tools` и выполните `python tools/make_camera_fixture.py` в окружении desktop-камеры. Затем запустите `npm --prefix tools run test:web-camera`. На Windows по умолчанию используется установленный Chrome. На macOS и Linux можно установить тестовый Chromium командой `npm --prefix tools exec -- playwright install chromium` или задать путь к браузеру через `JANKEN_CHROME`. Скрипт использует тестовый поток и сохраняет результаты в `.work/`.
