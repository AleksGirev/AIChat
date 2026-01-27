# ✅ Vosk Speech Recognition - Установка завершена

Модель Vosk для оффлайн распознавания речи успешно установлена!

## 📍 Расположение модели

```
vosk-models/vosk-model-small-ru-0.22/
```

## 🚀 Использование

### Автоматическое определение

Агент автоматически найдет модель при запуске, если она находится в `vosk-models/` директории проекта.

### Запуск с голосовым вводом

```bash
./gradlew :console-agent:runOfflineChat
```

При запуске вы увидите:
```
✓ Vosk model found: /path/to/vosk-models/vosk-model-small-ru-0.22
```

### Использование команды voice

В консоли агента:
```
You: voice
🎤 Recording... (speak now, 5 seconds)
✓ Recognized: ваш текст
AI: [ответ от LLM]
```

## 📋 Требования

1. ✅ Модель Vosk установлена
2. ⚠️ Установите инструменты для записи аудио:
   - **Linux**: `sudo apt-get install alsa-utils sox`
   - **macOS**: `brew install sox`

## 🔧 Переустановка модели

Если нужно переустановить модель:

```bash
cd console-agent
./setup-vosk.sh
```

## 📚 Дополнительная информация

См. [VOICE_RECOGNITION_README.md](VOICE_RECOGNITION_README.md) для подробной документации.
