# Персонализация агента

Агент поддерживает персонализацию через файл `PersonalConfig.kt`. Это позволяет агенту знать о вас, ваших привычках, предпочтениях и контексте работы.

## Настройка

1. Откройте файл `console-agent/src/main/kotlin/com/example/aichat/console/PersonalConfig.kt`

2. Заполните все поля, помеченные `TODO:`

   - **Основная информация**: имя, роль, компания, языки программирования
   - **Привычки и предпочтения**: рабочие привычки, предпочтения в коде, стиль коммуникации
   - **Рабочий стиль**: стиль работы, предпочитаемые инструменты и технологии
   - **Интересы и контекст**: интересы, текущие проекты, цели
   - **Дополнительная информация**: любая другая релевантная информация

3. Сохраните файл

4. Перезапустите агента

## Пример заполнения

```kotlin
val name: String = "Александр"
val role: String = "Android разработчик"
val company: String? = "Yandex"
val primaryLanguage: String = "Kotlin"
val additionalLanguages: List<String> = listOf("Java", "Python", "JavaScript")

val workHabits: List<String> = listOf(
    "Предпочитаю работать утром",
    "Делаю перерывы каждые 2 часа",
    "Люблю планировать задачи на день заранее"
)

val codingPreferences: List<String> = listOf(
    "Использую функциональный стиль",
    "Предпочитаю явные типы",
    "Люблю чистый код и SOLID принципы"
)

val communicationStyle: String = "Предпочитаю краткие, но информативные ответы"

val workStyle: List<String> = listOf(
    "Разбиваю задачи на мелкие части",
    "Сначала планирую, потом кодирую",
    "Люблю делать code review"
)

val preferredTools: List<String> = listOf(
    "Android Studio",
    "IntelliJ IDEA",
    "Git"
)

val preferredTechnologies: List<String> = listOf(
    "Jetpack Compose",
    "Ktor",
    "Coroutines",
    "Room"
)

val interests: List<String> = listOf(
    "Mobile Development",
    "AI/ML",
    "Open Source"
)

val currentProjects: List<String> = listOf(
    "AIChat - Android приложение для чата с AI",
    "CRM система с MCP интеграцией"
)

val goals: List<String> = listOf(
    "Изучить MCP протокол глубже",
    "Улучшить производительность приложения",
    "Внедрить RAG в больше проектов"
)

val additionalInfo: List<String> = listOf(
    "Работаю удаленно",
    "Живу в Москве",
    "Люблю кофе и программирование"
)
```

## Как это работает

После заполнения `PersonalConfig.kt`:

1. При запуске агент загружает вашу персональную информацию
2. Эта информация добавляется в системный промпт для всех оркестраторов:
   - `AgentOrchestrator` - базовый агент
   - `RAGAgentOrchestrator` - агент с RAG
   - `TeamAssistantOrchestrator` - командный ассистент

3. Агент использует эту информацию для:
   - Адаптации стиля коммуникации под ваши предпочтения
   - Предоставления более релевантных рекомендаций
   - Учета ваших привычек и рабочего стиля
   - Персонализации ответов и объяснений

## Проверка статуса

При запуске агента вы увидите статус персонализации:

```
[Personalization]: ✓ Personal information loaded
[Personalization]: Имя: Александр | Роль: Android разработчик | Язык: Kotlin
```

Или, если информация не заполнена:

```
[Personalization]: ⚠ Personal information not configured
[Personalization]: Fill PersonalConfig.kt to enable personalized responses
```

## Безопасность

⚠️ **Важно**: Файл `PersonalConfig.kt` содержит персональную информацию. 

- Файл уже добавлен в `.gitignore` (правило `**/personal_info*`)
- Если вы хотите сохранить свой заполненный конфиг локально, но не коммитить его, вы можете:
  1. Создать копию `PersonalConfig.kt` как `PersonalConfig.local.kt`
  2. Или использовать переменные окружения для чувствительных данных

## Дополнительные возможности

Вы можете расширить `PersonalConfig` добавив свои поля и методы. Все, что вы добавите в `formatPersonalContext()`, будет включено в системный промпт агента.
