# Настройка Ollama для удаленного доступа

## Проблема

Ollama по умолчанию слушает только на `127.0.0.1:11434` (localhost), что делает его недоступным извне. Ошибка `bind: address already in use` означает, что сервис уже запущен, но на локальном интерфейсе.

## Решение: Настройка Ollama для внешнего доступа

### Вариант 1: Переменная окружения OLLAMA_HOST (Рекомендуется)

Установите переменную окружения `OLLAMA_HOST` перед запуском Ollama:

```bash
# Для текущей сессии
export OLLAMA_HOST=0.0.0.0:11434
ollama serve

# Или для конкретного IP
export OLLAMA_HOST=193.42.127.171:11434
ollama serve
```

### Вариант 2: Системный сервис (systemd)

Если Ollama запущен как системный сервис, создайте или отредактируйте файл конфигурации:

```bash
# Создать файл конфигурации
sudo mkdir -p /etc/systemd/system/ollama.service.d/
sudo nano /etc/systemd/system/ollama.service.d/override.conf
```

Добавьте в файл:

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

Затем перезагрузите конфигурацию и перезапустите сервис:

```bash
sudo systemctl daemon-reload
sudo systemctl restart ollama
sudo systemctl status ollama
```

### Вариант 3: Файл конфигурации Ollama

Создайте или отредактируйте файл конфигурации Ollama:

```bash
# Создать директорию конфигурации (если не существует)
mkdir -p ~/.ollama

# Создать или отредактировать файл конфигурации
nano ~/.ollama/config
```

Добавьте:

```
OLLAMA_HOST=0.0.0.0:11434
```

### Вариант 4: Запуск с параметрами

Если вы запускаете Ollama вручную, можно указать хост при запуске:

```bash
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

## Проверка текущего состояния

### 1. Проверить, запущен ли Ollama

```bash
# Проверить процессы
ps aux | grep ollama

# Проверить, какой порт слушает Ollama
sudo netstat -tlnp | grep 11434
# или
sudo ss -tlnp | grep 11434
# или
sudo lsof -i :11434
```

### 2. Проверить, на каком интерфейсе слушает

Если вы видите `127.0.0.1:11434` или `localhost:11434` - это означает, что Ollama слушает только на localhost.

Если вы видите `0.0.0.0:11434` или `:::11434` - Ollama доступен извне.

### 3. Остановить текущий процесс Ollama

```bash
# Найти процесс
ps aux | grep ollama

# Остановить (замените PID на реальный)
kill <PID>

# Или если запущен как сервис
sudo systemctl stop ollama
```

## Настройка файрвола

Убедитесь, что порт 11434 открыт в файрволе:

### UFW (Ubuntu/Debian)

```bash
sudo ufw allow 11434/tcp
sudo ufw status
```

### firewalld (CentOS/RHEL)

```bash
sudo firewall-cmd --permanent --add-port=11434/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-ports
```

### iptables

```bash
sudo iptables -A INPUT -p tcp --dport 11434 -j ACCEPT
sudo iptables-save
```

## Пошаговая инструкция для вашего сервера

1. **Остановите текущий процесс Ollama:**
   ```bash
   # Найти и остановить процесс
   pkill ollama
   # или
   sudo systemctl stop ollama
   ```

2. **Установите переменную окружения и запустите:**
   ```bash
   export OLLAMA_HOST=0.0.0.0:11434
   ollama serve
   ```

3. **Проверьте, что Ollama слушает на всех интерфейсах:**
   ```bash
   sudo netstat -tlnp | grep 11434
   # Должно показать: 0.0.0.0:11434 или :::11434
   ```

4. **Проверьте доступность извне:**
   ```bash
   # С другого компьютера или с вашего локального
   curl http://193.42.127.171:11434/api/tags
   ```

5. **Для постоянной настройки (если используете systemd):**
   ```bash
   sudo mkdir -p /etc/systemd/system/ollama.service.d/
   echo '[Service]
   Environment="OLLAMA_HOST=0.0.0.0:11434"' | sudo tee /etc/systemd/system/ollama.service.d/override.conf
   sudo systemctl daemon-reload
   sudo systemctl restart ollama
   ```

## Безопасность

⚠️ **Важно:** Открытие Ollama для внешнего доступа может быть небезопасно, если сервер доступен из интернета. Рекомендуется:

1. **Использовать файрвол** - разрешить доступ только с определенных IP:
   ```bash
   sudo ufw allow from <YOUR_IP> to any port 11434
   ```

2. **Использовать reverse proxy** (nginx/traefik) с аутентификацией

3. **Использовать VPN** для доступа к серверу

4. **Настроить TLS/SSL** для шифрования соединений

## Проверка после настройки

После настройки выполните проверку:

```bash
# С сервера (должно работать)
curl http://localhost:11434/api/tags

# С внешнего компьютера (должно работать после настройки)
curl http://193.42.127.171:11434/api/tags

# Тест генерации
curl http://193.42.127.171:11434/api/generate -d '{
  "model": "qwen2:7b-instruct",
  "prompt": "Привет! Кто ты?",
  "stream": false
}'
```

## Использование в проекте

После настройки вы можете использовать удаленный Ollama в проекте:

```bash
# Установить переменную окружения
export OLLAMA_BASE_URL=http://193.42.127.171:11434

# Или в коде
val ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
```
