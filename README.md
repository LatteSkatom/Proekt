Проект: Proekt (Android-приложение)

О проекте
Proekt — мобильное Android‑приложение для работы с пользовательскими данными и профилем, включающее основные разделы приложения (главный экран, добавление данных, аналитика) и поддерживающее авторизацию через Firebase. Приложение ориентировано на удобную навигацию между экранами и управление профилем пользователя.

Ключевые возможности
- Авторизация и выход из аккаунта (Firebase Auth).
- Режим гостя: доступ к базовым разделам без входа.
- Профиль пользователя:
  - отображение логина и аватара;
  - смена логина;
  - смена пароля при повторной аутентификации.
- Работа с изображениями: выбор аватара из галереи и локальное хранение.
- Основные экраны:
  - главный экран;
  - экран добавления данных;
  - экран аналитики;
  - экран настроек и профиля.

Как устроено приложение
- Архитектура использует Activity и XML‑разметки.
- Навигация реализована переходами между Activity.
- Данные профиля и логин хранятся в Firebase Firestore.
- Изображения аватара сохраняются локально на устройстве, а при наличии URL подхватываются из профиля Firebase.

Структура базы данных в Firebase (Firestore)
- Коллекция users/{uid}
  - email (string)
  - login (string)
  - name (string)
  - avatarUrl (string|null)
  - createdAt (timestamp)
  - updatedAt (timestamp, при изменении логина)
  - Подколлекция subscriptions
    - serviceName (string)
    - cost (number)
    - frequency (string)
    - nextPaymentDate (string)
    - isActive (boolean)
    - createdAt (timestamp)
- Коллекция logins/{login}
  - uid (string)
- Коллекция emails/{sha256(email)}
  - uid (string)

Технологии и зависимости
- Kotlin/Java (Android SDK)
- Firebase Auth, Firebase Firestore
- Material Components
- Glide (загрузка изображений)

Структура проекта
- app/ — исходный код приложения и ресурсы (layout, drawables).
- build/ — артефакты сборки.
- gradle/ — Gradle-обвязка.
- settings.gradle.kts, build.gradle.kts — конфигурация сборки.

