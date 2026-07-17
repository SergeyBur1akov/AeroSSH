# AeroSSH

**Безопасный SSH-клиент для Android**

Полноценный терминал с шифрованием уровня LUKS, биометрической защитой и SFTP-клиентом.

## Возможности

### Терминал
- Интерактивная SSH-оболочка через PTY
- ANSI-цвета (16 базовых + bold/bright)
- Поиск по терминалу с подсветкой
- История команд (свайп вверх по полю ввода)
- Swipe-down = Ctrl+C
- Pinch-to-zoom для шрифта
- Копирование текста тапом/долгим нажатием

### Клавиатура терминала
- TAB, ESC, CTRL (toggle-режим)
- Стрелки навигации, HOME, END, PG↑, PG↓
- Панель быстрых символов: `| / \ - _ ~ @ : .`
- Ctrl+горячие клавиши: ^C ^D ^Z ^L
- Haptic feedback на кнопках

### Мультисессия
- Вкладки с переключением
- Зелёный индикатор активной сессии

### Сохранённые серверы
- Список серверов с поиском
- Swipe влево = удалить, вправо = подключиться
- Группы/теги серверов
- Импорт SSH config (`~/.ssh/config`)

### SFTP-клиент
- Навигация по файловой системе сервера
- Создание/удаление/переименование
- Скачивание файлов с открытием
- Breadcrumbs-навигация

### SSH-ключи
- Генерация Ed25519 / RSA / ECDSA
- Импорт OpenSSH ключей
- Проверка хостов (known_hosts)
- Диалог "Unknown Host" с отпечатком

### Безопасность

| Механизм | Реализация |
|----------|------------|
| Шифрование данных | AES-256-GCM |
| Деривация ключа | PBKDF2-HMAC-SHA256 × 600K |
| Hardware backing | Android Keystore + StrongBox |
| Хранилище БД | SQLCipher |
| Хранилище настроек | EncryptedSharedPreferences |
| Пароли в памяти | CharArray + fill(0) |
| Сравнение хешей | Constant-time (anti-timing) |
| Скриншоты | FLAG_SECURE |
| Буфер обмена | Auto-clear (30 сек) |
| Root detection | Предупреждение при root |
| Brute force | 10 попыток → стирание vault |
| Обфускация | ProGuard |

### Настройки
- Тема: Dark / OLED Black / Light
- Размер шрифта (10-32sp)
- Буфер прокрутки (1K/5K/10K строк)
- Кодировка (UTF-8 / KOI8-R / CP1251)
- Keepalive, автопереподключение

## Сборка

```bash
git clone https://github.com/SergeyBur1akov/AeroSSH.git
cd AeroSSH/AeroSSH-Kotlin
./gradlew assembleDebug
```

Или открой проект в **Android Studio** → Build → Build APK.

## Технологии

| Компонент | Технология |
|-----------|------------|
| SSH | SSHJ 0.38.0 |
| Шифрование БД | SQLCipher 4.5.6 |
| Хранилище | Room 2.6.1 + EncryptedSharedPreferences |
| Биометрия | AndroidX Biometric 1.2.0 |
| UI | Material Components + ViewBinding |
| Async | Kotlin Coroutines |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |

## Лицензия

MIT License
