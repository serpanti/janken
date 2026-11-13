# Third-party components

The application uses AndroidX, Kotlin, kotlinx.coroutines, Compose Multiplatform, Skiko/Skia and Google MediaPipe Tasks. Compose and Skiko use Apache-2.0; Skia has a BSD-style license. Third-party notices embedded in the packaged dependencies remain applicable. Dependencies retain their respective licenses.

The Android model `janken_gesture_recognizer.task` combines the Google hand landmarker and frozen gesture embedder with a classifier trained for this prototype. The Google embedder was obtained from https://storage.googleapis.com/mediapipe-assets/gesture_embedder.tar.gz.

Training inputs included Laurence Moroney's Rock Paper Scissors dataset, [Rock-Paper-Scissors Images by Julien de la Bruère-Terreault](https://github.com/DrGFreeman/rps-cv-data-science) (CC BY-SA 4.0), the [Google Model Maker RPS sample](https://storage.googleapis.com/mediapipe-tasks/gesture_recognizer/rps_data_sample.zip) for the `None` class, and an additional set of indoor photos. The training archives are kept outside the repository.

MediaPipe documentation and model information:
https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer

The instrumentation test images `fist.jpg`, `victory.jpg`, `right_hands.jpg` and `thumb_up.jpg` are unmodified Google MediaPipe test assets, not original artwork for this app. Sources:
https://storage.googleapis.com/mediapipe-assets/fist.jpg
https://storage.googleapis.com/mediapipe-assets/victory.jpg
https://storage.googleapis.com/mediapipe-assets/thumb_up.jpg
https://storage.googleapis.com/mediapipe-assets/right_hands.jpg

The Gradle wrapper is from Gradle v8.13.0 and is licensed under Apache License 2.0. A copy is in LICENSE.

Camera/model integration was informed by Google's Apache-2.0 Android example:
https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/gesture_recognizer/android

`HandShapeFixtures.kt` contains SDK coordinates from the public images listed above, including quarter-turn rotations.

The dorsal-view test images under `app/src/androidTest/assets/dorsal` are unmodified CGI images from [Laurence Moroney's Rock Paper Scissors Dataset (2019)](https://laurencemoroney.com/datasets.html), licensed [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/). `DorsalHandFixtures.kt` contains MediaPipe coordinates derived from the [test archive](https://storage.googleapis.com/download.tensorflow.org/data/rps-test-set.zip).

The small fonts gesture_symbols.ttf and japanese_subtitle.ttf are renamed subsets derived from Google's Noto Emoji and Noto Sans JP, under the SIL Open Font License 1.1. License texts are in licenses/fonts. Original sources:
https://github.com/google/fonts/tree/main/ofl/notoemoji
https://github.com/google/fonts/tree/main/ofl/notosansjp
