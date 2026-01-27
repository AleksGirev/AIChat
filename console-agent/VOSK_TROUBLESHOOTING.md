# Устранение проблем с Vosk на macOS

## Проблема: UnsatisfiedLinkError на macOS

Если вы видите ошибку:
```
java.lang.UnsatisfiedLinkError: Error looking up function 'vosk_recognizer_set_grm'
```

Это известная проблема с версией Vosk 0.3.45+ на macOS.

## Решения

### Решение 1: Использовать стабильную версию (рекомендуется)

Версия 0.3.38 более стабильна на macOS. Код уже настроен на использование этой версии.

Если проблема сохраняется, попробуйте:

1. Очистить кэш Gradle:
```bash
./gradlew clean
rm -rf ~/.gradle/caches
```

2. Пересобрать проект:
```bash
./gradlew :console-agent:build --refresh-dependencies
```

### Решение 2: Использовать альтернативный подход

Если Vosk продолжает вызывать проблемы, можно использовать:

1. **Python API Vosk** через subprocess
2. **Whisper.cpp** (локальный Whisper)
3. **Онлайн API** (Google Cloud Speech, Azure Speech)

### Решение 3: Установить нативные библиотеки вручную

1. Скачайте нативные библиотеки Vosk для macOS:
   - https://github.com/alphacep/vosk-api/releases

2. Распакуйте и добавьте в classpath или системный путь

## Альтернатива: Использовать Python Vosk через subprocess

Если Java версия не работает, можно использовать Python версию:

```kotlin
// Пример использования Python Vosk
fun recognizeWithPythonVosk(audioPath: String): String {
    val process = ProcessBuilder(
        "python3", "-c", """
        import json
        import sys
        from vosk import Model, KaldiRecognizer
        
        model = Model("$modelPath")
        rec = KaldiRecognizer(model, 16000)
        
        with open("$audioPath", "rb") as f:
            data = f.read()
            if rec.AcceptWaveform(data):
                result = json.loads(rec.Result())
                print(result.get("text", ""))
            else:
                result = json.loads(rec.FinalResult())
                print(result.get("text", ""))
        """.trimIndent()
    ).start()
    
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output.trim()
}
```

Требования:
- Python 3 установлен
- Vosk Python библиотека: `pip install vosk`

## Проверка установки

Проверьте, что модель установлена:
```bash
ls -la vosk-models/vosk-model-small-ru-0.22/
```

Проверьте версию Vosk в зависимостях:
```bash
./gradlew :console-agent:dependencies | grep vosk
```

## Дополнительная информация

- GitHub Issue: https://github.com/alphacep/vosk-api/issues/1682
- Vosk Installation: https://alphacephei.com/vosk/install
