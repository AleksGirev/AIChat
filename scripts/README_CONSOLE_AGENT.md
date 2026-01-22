# Установка команды console-agent

## Быстрая установка

Выполните скрипт установки:

```bash
./scripts/install-console-agent.sh
```

Это создаст симлинк в `~/.local/bin/console-agent` (или `~/bin/console-agent`).

## Использование

После установки вы можете использовать команду `console-agent` из любого места:

```bash
# Запустить Remote AI Chat (по умолчанию)
console-agent

# Или явно указать команду
console-agent remote

# Запустить Offline AI Chat
console-agent offline

# Показать справку
console-agent help
```

## Ручная установка

Если скрипт установки не работает, создайте симлинк вручную:

```bash
# Создать директорию, если её нет
mkdir -p ~/.local/bin

# Создать симлинк
ln -s /path/to/AIChat/scripts/console-agent ~/.local/bin/console-agent

# Добавить в PATH (если еще не добавлено)
echo 'export PATH="$PATH:$HOME/.local/bin"' >> ~/.zshrc
source ~/.zshrc
```

## Использование без установки

Вы также можете запускать скрипт напрямую:

```bash
./scripts/console-agent
./scripts/console-agent remote
./scripts/console-agent offline
```

## Переменные окружения

```bash
# Указать удаленный сервер
export OLLAMA_BASE_URL=http://193.42.127.171:11434
export OLLAMA_MODEL=qwen2:7b-instruct
console-agent
```

## Примеры

```bash
# Запуск remote chat
console-agent

# Запуск offline chat
console-agent offline

# Справка
console-agent help
```
