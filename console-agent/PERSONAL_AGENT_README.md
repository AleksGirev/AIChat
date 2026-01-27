# Personal Agent - Персонализированный агент с локальной LLM

Персонализированный агент, который использует локальную Ollama LLM и вашу персональную информацию из `PersonalConfig.kt`.

## Особенности

- ✅ **Локальная LLM по умолчанию**: Использует Ollama на `http://localhost:11434`
- ✅ **Персонализация**: Автоматически загружает информацию из `PersonalConfig.kt`
- ✅ **RAG поддержка**: Может работать с индексированными документами
- ✅ **Team Assistant режим**: Поддержка управления задачами через Tasks MCP

## Требования

1. **Ollama должен быть запущен локально**:
   ```bash
   ollama serve
   ```

2. **Модель должна быть загружена** (по умолчанию используется `qwen2:7b-instruct`):
   ```bash
   ollama pull qwen2:7b-instruct
   ```

3. **Персональная информация заполнена** в `PersonalConfig.kt`

## Запуск

### Вариант 1: Через Gradle

```bash
./gradlew :console-agent:runPersonalAgent
```

### Вариант 2: Через команду console-agent

```bash
# Установить команду (один раз)
./scripts/install-console-agent.sh

# Запустить персонализированного агента
console-agent personal
```

### Вариант 3: С кастомными настройками Ollama

```bash
OLLAMA_BASE_URL=http://localhost:11434 \
OLLAMA_MODEL=qwen2:7b-instruct \
./gradlew :console-agent:runPersonalAgent
```

## Режимы работы

### Обычный режим (по умолчанию)
```bash
console-agent personal
```
Персонализированный чат без RAG и MCP инструментов.

### С RAG (рекомендуется)
```bash
console-agent personal --no-rag  # RAG включен по умолчанию
```
Использует индексированные документы для ответов.

### Team Assistant режим
```bash
console-agent personal --team-assistant
```
Комбинация RAG + Tasks MCP для управления задачами команды.

## Команды в агенте

- `/help <вопрос>` - Поиск помощи в документации (требует RAG)
- `/rag on` - Включить RAG
- `/rag off` - Выключить RAG
- `/rag status` - Показать статус RAG
- `exit` - Выход

## Пример использования

```bash
$ console-agent personal

=== Personal RAG-Enhanced AI Agent ===

[LLM]: Using local Ollama
[LLM]:   Base URL: http://localhost:11434
[LLM]:   API URL: http://localhost:11434/v1
[LLM]:   Model: qwen2:7b-instruct
[LLM]: ✓ Ollama server is accessible

[Agent]: Инициализация...
[RAG]: Initializing RAG pipeline...
[Agent]: Готов к работе!

[Personalization]: ✓ Personal information loaded
[Personalization]: Имя: Александр | Роль: Тимлид мобильной разработки | Язык: Kotlin

[RAG]: RAG mode enabled - questions will be answered using indexed documents

> Составь план обучения для меня
[Agent]: Основываясь на вашей роли тимлида мобильной разработки и ваших целях...
```

## Настройка

### Изменить модель Ollama

```bash
export OLLAMA_MODEL=llama3:8b
console-agent personal
```

### Изменить URL Ollama

```bash
export OLLAMA_BASE_URL=http://192.168.1.100:11434
console-agent personal
```

### Настройка RAG

Для работы RAG нужна отдельная модель для эмбеддингов:
```bash
export RAG_OLLAMA_BASE_URL=http://localhost:11434
export RAG_OLLAMA_MODEL=nomic-embed-text
console-agent personal
```

## Отличия от обычного агента

| Функция | Обычный агент | Personal Agent |
|---------|---------------|----------------|
| LLM | YandexGPT (требует токен) | Ollama (локально) |
| Персонализация | Нет | Да (из PersonalConfig) |
| MCP Mobile | Да | Нет (опционально) |
| RAG | Опционально | По умолчанию |
| Требования | YandexGPT токен | Только Ollama |

## Устранение проблем

### Ошибка подключения к Ollama

```
[LLM]: ⚠ Warning: Could not connect to Ollama
```

**Решение**: Убедитесь, что Ollama запущен:
```bash
ollama serve
```

### Модель не найдена

**Решение**: Загрузите модель:
```bash
ollama pull qwen2:7b-instruct
```

### Персонализация не работает

**Решение**: Проверьте, что `PersonalConfig.kt` заполнен и скомпилирован:
```bash
./gradlew :console-agent:compileKotlin
```

## Дополнительная информация

- [PERSONALIZATION_README.md](PERSONALIZATION_README.md) - Настройка персонализации
- [README.md](README.md) - Общая документация консольного агента
- [LOCAL_LLM_SETUP.md](../docs/LOCAL_LLM_SETUP.md) - Настройка локальной LLM
