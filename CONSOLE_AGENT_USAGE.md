# Использование Console Agent

## Быстрый старт

### 1. Установка команды (один раз)

```bash
./scripts/install-console-agent.sh
```

Это создаст команду `console-agent`, доступную из любого места в терминале.

### 2. Использование

```bash
# Запустить Remote AI Chat (подключение к удаленному серверу)
console-agent

# Или явно указать команду
console-agent remote

# Запустить Offline AI Chat (локальный Ollama)
console-agent offline

# Показать справку
console-agent help
```

## Команды

| Команда | Описание |
|---------|----------|
| `console-agent` или `console-agent remote` | Запустить Remote AI Chat (по умолчанию) |
| `console-agent offline` | Запустить Offline AI Chat (локальный Ollama) |
| `console-agent help` | Показать справку |

## Переменные окружения

```bash
# Указать удаленный сервер
export OLLAMA_BASE_URL=http://193.42.127.171:11434
export OLLAMA_MODEL=qwen2:7b-instruct

# Запустить
console-agent
```

## Использование без установки

Если не хотите устанавливать команду, можно запускать скрипт напрямую:

```bash
./scripts/console-agent
./scripts/console-agent remote
./scripts/console-agent offline
```

## Примеры

```bash
# Базовое использование
console-agent

# С настройкой сервера
export OLLAMA_BASE_URL=http://your-server:11434
console-agent

# Offline режим (требует локальный Ollama)
console-agent offline
```

## Дополнительная информация

- **Remote Chat:** `console-agent/REMOTE_CHAT_README.md`
- **Offline Chat:** `console-agent/OFFLINE_CHAT_README.md`
- **Установка:** `scripts/README_CONSOLE_AGENT.md`
