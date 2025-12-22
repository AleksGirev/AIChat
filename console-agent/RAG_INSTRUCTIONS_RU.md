# Инструкция: Использование RAG с вашим документом

## Пошаговая инструкция

### Шаг 1: Установите Ollama (если еще не установлено)

1. Перейдите на сайт: https://ollama.ai
2. Скачайте и установите Ollama для вашей операционной системы
3. Убедитесь, что Ollama запущен (обычно запускается автоматически)

Проверка установки:
```bash
ollama --version
```

### Шаг 2: Загрузите модель для генерации эмбеддингов

Откройте терминал и выполните:
```bash
ollama pull nomic-embed-text
```

Это займет некоторое время (модель ~274 МБ). Альтернативная модель:
```bash
ollama pull mxbai-embed-large
```

### Шаг 3: Убедитесь, что Ollama работает

Проверьте, что Ollama API доступен:
```bash
curl http://localhost:11434/api/tags
```

Если Ollama не запущен, запустите его:
```bash
ollama serve
```

### Шаг 4: Подготовьте ваш документ

Убедитесь, что вы знаете полный путь к вашему документу. Поддерживаемые форматы:
- `.pdf` - PDF документы
- `.md` - Markdown файлы
- `.txt` - Текстовые файлы
- `.kt`, `.java` - Исходный код
- И другие текстовые форматы

**Пример путей:**
- На macOS/Linux: `/Users/ВашеИмя/Documents/my_document.pdf`
- На Windows: `C:\Users\ВашеИмя\Documents\my_document.pdf`
- Или относительный путь: `./documents/my_document.pdf`

### Шаг 5: Индексируйте ваш документ

Откройте терминал в папке проекта (`/Users/Girev.Aleksandr2/AndroidStudioProjects/AIChat`) и выполните:

**Для одного файла (без пробелов в пути):**
```bash
./gradlew :console-agent:runRag -Prag.args="rag index /полный/путь/к/вашему/документу.pdf"
```

**Для файла с пробелами в пути (используйте кавычки):**
```bash
./gradlew :console-agent:runRag -Prag.args='rag index "/Users/Girev.Aleksandr2/Downloads/Александр Гирев.pdf"'
```

**Для нескольких файлов:**
```bash
./gradlew :console-agent:runRag -Prag.args="rag index /путь/к/файлу1.pdf /путь/к/файлу2.txt"
```

**Для целой папки:**
```bash
./gradlew :console-agent:runRag -Prag.args="rag index /путь/к/папке/с/документами"
```

**Пример для macOS (замените путь на ваш):**
```bash
# Если путь без пробелов:
./gradlew :console-agent:runRag -Prag.args="rag index /Users/Girev.Aleksandr2/Documents/my_document.pdf"

# Если путь содержит пробелы (ваш случай):
./gradlew :console-agent:runRag -Prag.args='rag index "/Users/Girev.Aleksandr2/Downloads/Александр Гирев.pdf"'
```

**Важно:** Если путь содержит пробелы, используйте одинарные кавычки снаружи и двойные кавычки для пути внутри.

Вы увидите прогресс индексации:
```
[25%] Loading documents: 1/1
[50%] Splitting chunks: 1/1 documents
[75%] Generating embeddings: 50/100 chunks
[100%] Completed! Stored 100 chunks
```

### Шаг 6: Поиск в вашем документе

После индексации вы можете искать информацию:

```bash
./gradlew :console-agent:runRag -Prag.args="rag search 'ваш запрос на русском или английском'"
```

**Примеры запросов:**
```bash
./gradlew :console-agent:runRag -Prag.args='rag search "как работает аутентификация"'
./gradlew :console-agent:runRag -Prag.args='rag search "основные концепции"'
./gradlew :console-agent:runRag -Prag.args='rag search "мой номер телефона"'
```

**Важно:** Для поисковых запросов используйте одинарные кавычки снаружи и двойные кавычки для запроса внутри.

Вы получите результаты с оценкой схожести и фрагментами текста из документа.

### Шаг 7: Просмотр статистики

Чтобы увидеть, сколько документов проиндексировано:
```bash
./gradlew :console-agent:runRag -Prag.args="rag stats"
```

### Дополнительные команды

**Очистить индекс** (удалить все проиндексированные документы):
```bash
./gradlew :console-agent:runRag -Prag.args="rag clear"
```

## Использование в вашем коде

Если вы хотите использовать RAG в своем Kotlin коде:

```kotlin
import com.example.aichat.console.rag.RAGPipeline
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Создаем pipeline
    val pipeline = RAGPipeline.create(
        dbPath = "rag_index.db",
        ollamaModel = "nomic-embed-text"
    )
    
    try {
        // Индексируем документ
        val result = pipeline.processDocuments(
            paths = listOf("/путь/к/вашему/документу.pdf"),
            onProgress = { progress ->
                println("${progress.getProgressPercentage()}% - ${progress.getStatusMessage()}")
            }
        )
        
        if (result.isSuccess) {
            println("Успешно проиндексировано ${result.getOrThrow()} фрагментов")
            
            // Ищем информацию
            val searchResult = pipeline.search("ваш запрос", limit = 5)
            searchResult.getOrThrow().forEach { result ->
                println("Схожесть: ${result.similarity}")
                println("Содержимое: ${result.chunk.content}")
            }
        }
    } finally {
        pipeline.close()
    }
}
```

## Решение проблем

### Ошибка: "Connection refused" или "Failed to connect"
- Убедитесь, что Ollama запущен: `ollama serve`
- Проверьте, что порт 11434 не занят другим приложением

### Ошибка: "Model not found"
- Убедитесь, что модель загружена: `ollama list`
- Если модели нет, загрузите её: `ollama pull nomic-embed-text`

### Ошибка: "File not found"
- Проверьте правильность пути к файлу
- Используйте абсолютный путь вместо относительного
- Убедитесь, что файл существует и доступен для чтения

### Медленная индексация
- Это нормально для больших документов
- Процесс может занять несколько минут для документов в сотни страниц
- Прогресс отображается в реальном времени

## Где хранятся данные?

После индексации создается файл `rag_index.db` в текущей директории (где вы запускали команду). Это SQLite база данных, которая содержит:
- Текстовые фрагменты из ваших документов
- Эмбеддинги (векторные представления) этих фрагментов
- Метаданные (источник, позиция в документе и т.д.)

Вы можете использовать этот файл для поиска без повторной индексации документов.

