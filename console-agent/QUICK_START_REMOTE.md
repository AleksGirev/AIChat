# Быстрый старт: Remote AI Chat

## Запуск программы

### Вариант 1: Через команду console-agent (рекомендуется)

```bash
# Установить команду (один раз)
./scripts/install-console-agent.sh

# Использовать из любого места
console-agent
```

### Вариант 2: Через Gradle

```bash
./gradlew :console-agent:runRemoteChat
```

Использует:
- Сервер: `http://193.42.127.171:11434`
- Модель: `qwen2:7b-instruct`

### Вариант 2: С переменными окружения

```bash
export OLLAMA_BASE_URL=http://193.42.127.171:11434
export OLLAMA_MODEL=qwen2:7b-instruct
./gradlew :console-agent:runRemoteChat
```

### Вариант 3: С аргументами

```bash
./gradlew :console-agent:runRemoteChat -Premotechat.args="http://193.42.127.171:11434 qwen2:7b-instruct"
```

## Пример сессии

```
🌐 Remote AI Assistant
============================================================
Server: http://193.42.127.171:11434
Model: qwen2:7b-instruct
Type 'exit' to quit, 'help' for commands
============================================================

Connecting to remote Ollama server...
✓ Connected successfully
  Server: http://193.42.127.171:11434
  Model: qwen2:7b-instruct

You: Привет!
AI: Привет! Как дела? Чем могу помочь?

You: help
Available commands:
  help  - Show this help message
  exit  - Exit the chat
  quit  - Exit the chat
  info  - Show server and model information

You: info
Server Information:
  URL: http://193.42.127.171:11434
  Model: qwen2:7b-instruct

You: exit
Goodbye! 👋
```

## Команды

- `help` - справка
- `info` - информация о сервере
- `exit` / `quit` - выход

## Требования

- Удаленный Ollama сервер должен быть доступен
- Модель должна быть загружена на сервере
- Сетевое подключение

Подробнее: [REMOTE_CHAT_README.md](REMOTE_CHAT_README.md)
