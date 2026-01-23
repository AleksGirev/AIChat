# Чеклист проверки работы приложения

## ✅ Статус проверки

### 1. Локальный Ollama сервер
- ✅ **Версия**: 0.13.5 (работает)
- ✅ **Модель**: qwen2:7b-instruct (доступна, 4.4 GB)
- ✅ **Порт**: 11434 (отвечает)

### 2. Код приложения
- ✅ **Компиляция**: Нет ошибок линтера
- ✅ **Клиент LLM**: Поддерживает параметры генерации
  - system prompt
  - temperature
  - maxTokens (num_predict)
  - topP
- ✅ **Offline Chat**: Обновлен с поддержкой параметров

### 3. Тестовые скрипты
- ✅ **baseline**: `scripts/test-recipe-baseline.sh` (создан)
- ✅ **optimized**: `scripts/test-recipe-optimized.sh` (создан)
- ✅ **Права**: Исполняемые (chmod +x)

### 4. CLI команды
- ✅ **console-agent offline**: Настроен для локального подключения
- ✅ **console-agent remote**: Настроен для удаленного сервера
- ✅ **console-agent help**: Работает

## 🧪 Тестирование

### Быстрый тест подключения

```bash
# Проверить Ollama
curl http://localhost:11434/api/version

# Проверить модель
curl http://localhost:11434/api/tags

# Простой тест генерации
curl -X POST http://localhost:11434/api/generate -d '{
  "model": "qwen2:7b-instruct",
  "prompt": "Hello!",
  "stream": false
}'
```

### Тест через CLI

```bash
# Локальное подключение
console-agent offline

# Удаленное подключение
console-agent remote
```

### Тест рецептов

```bash
# Baseline
./scripts/test-recipe-baseline.sh

# Optimized
./scripts/test-recipe-optimized.sh
```

## 📋 Ожидаемое поведение

### Offline Chat
1. Подключается к `http://localhost:11434`
2. Использует модель `qwen2:7b-instruct`
3. Поддерживает команды: `help`, `info`, `exit`
4. Обрабатывает таймауты корректно

### Remote Chat
1. Подключается к `http://193.42.127.171:11434`
2. Использует модель `qwen2:7b-instruct`
3. Таймаут: 300 секунд (5 минут)

### Тестовые скрипты
1. Baseline: простой запрос без параметров
2. Optimized: запрос с system prompt и оптимизированными параметрами

## ⚠️ Известные ограничения

1. **Java Runtime**: Требуется для компиляции и запуска через Gradle
2. **Ollama должен быть запущен**: `ollama serve` или через systemd
3. **Модель должна быть загружена**: `ollama pull qwen2:7b-instruct`

## 🔧 Устранение проблем

### Если Ollama не отвечает
```bash
# Проверить статус
curl http://localhost:11434/api/version

# Запустить Ollama
ollama serve
```

### Если модель не найдена
```bash
# Загрузить модель
ollama pull qwen2:7b-instruct

# Проверить список
ollama list
```

### Если команда console-agent не найдена
```bash
# Добавить в PATH
export PATH="$PATH:$HOME/.local/bin"

# Или использовать напрямую
./scripts/console-agent offline
```
