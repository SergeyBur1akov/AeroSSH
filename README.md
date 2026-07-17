# AeroSSH

**Безопасный SSH-клиент для Android**

Полноценный терминал с шифрованием уровня LUKS, биометрической защитой и SFTP-клиентом.

---

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
- Стрелки навигации ▲▼◄►
- HOME, END, PG↑, PG↓
- Панель быстрых символов: `| / \ - _ ~ @ : .`
- Ctrl+горячие клавиши: ^C ^D ^Z ^L
- Haptic feedback на кнопках

### Мультисессия
- Вкладки с переключением
- Зелёный индикатор активной сессии
- Закрытие вкладок (×)

### Сохранённые серверы
- Список серверов с поиском
- Добавление/редактирование
- Swipe влево = удалить, вправо = подключиться
- Группы/теги серверов
- Quick Connect панель
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

### Безопасность (максимальный уровень)

| Уровень | Механизм |
|---------|----------|
| Шифрование данных | AES-256-GCM |
| Деривация ключа | PBKDF2-HMAC-SHA256 × 600K итераций |
| Hardware backing | Android Keystore + StrongBox |
| Хранилище БД | SQLCipher |
| Хранилище настроек | EncryptedSharedPreferences |
| Хранилище known_hosts | EncryptedSharedPreferences |
| Пароли в памяти | CharArray + fill(0) |
| Сравнение хешей | Constant-time (anti-timing) |
| Скриншоты | FLAG_SECURE |
| Буфер обмена | Auto-clear (30 сек) |
| Root detection | Предупреждение при root |
| Emulator detection | Предупреждение при эмуляторе |
| Brute force | 10 попыток → стирание vault |
| Минимальный пароль | 8 символов + uppercase + lowercase + digit |
| Reverse engineering | ProGuard обфускация |
| Сеть | cleartextTraffic=false |
| Бэкапы | allowBackup=false |

### Настройки
- Тема: Dark / OLED Black / Light
- Размер шрифта (10-32sp)
- Межстрочный интервал (1.0-2.5x)
- Буфер прокрутки (1K/5K/10K строк)
- Кодировка (UTF-8 / KOI8-R / CP1251)
- Keepalive, автопереподключение
- Смена пароля vault
- Полное стирание данных

---

## Архитектура

```
com.companyname.aerossh/
├── MainActivity.kt          # Список серверов
├── LockActivity.kt          # Экран блокировки (vault)
├── TerminalActivity.kt      # Мульти-сессия терминала
├── SftpActivity.kt          # Файловый менеджер
├── SettingsActivity.kt      # Настройки
├── TerminalView.kt          # Кастомный View терминала
├── TerminalBuffer.kt        # Буфер терминала (ANSI)
├── TerminalSearch.kt        # Поиск по терминалу
├── AnsiParser.kt            # Парсер ANSI escape
├── SshService.kt            # SSH через SSHJ
│
├── data/
│   ├── Server.kt            # Room Entity
│   ├── ServerDao.kt         # DAO
│   ├── ServerRepository.kt  # Репозиторий (encrypt/decrypt)
│   ├── SshKey.kt            # SSH ключи (Entity)
│   ├── SshKeyDao.kt         # DAO ключей
│   └── AppDatabase.kt       # Room + SQLCipher
│
├── security/
│   ├── LuksEncryption.kt    # LUKS-шифрование (AES-GCM)
│   ├── SecurityManager.kt   # Root/emulator/clipboard
│   ├── SecureStorage.kt     # EncryptedSharedPreferences
│   └── SessionLogger.kt     # Шифрованные логи
│
├── crypto/
│   ├── KeystoreHelper.kt    # Android Keystore wrapper
│   ├── KeyManager.kt        # Генерация SSH ключей
│   ├── HostKeyManager.kt    # known_hosts
│   └── BiometricHelper.kt   # BiometricPrompt
│
├── sftp/
│   └── SftpService.kt       # SFTP через SSHJ
│
├── sshconfig/
│   └── SshConfigParser.kt   # Парсер ~/.ssh/config
│
└── ui/
    ├── ServerAdapter.kt     # RecyclerView адаптер
    ├── SftpAdapter.kt       # SFTP адаптер
    ├── ServerDialogFragment.kt
    ├── HostKeyDialogFragment.kt
    └── Prefs.kt             # SharedPreferences helper
```

---

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

---

## Сборка

```bash
# Клонировать
git clone https://github.com/SergeyBur1akov/AeroSSH.git
cd AeroSSH

# Собрать debug APK
./gradlew assembleDebug

# APK будет в:
# app/build/outputs/apk/debug/app-debug.apk
```

Или открой проект в **Android Studio** → Build → Build APK.

---

## Статус

| Спринт | Статус |
|--------|--------|
| P0: Полноценный терминал | ✅ |
| P1: Сохранённые серверы | ✅ |
| P2: Удобство ввода | ✅ |
| P3: Мультисессия | ✅ |
| P4: SSH-ключи и хосты | ✅ |
| P4.5: Биометрия | ✅ |
| P5: SFTP-клиент | ✅ |
| P6: Настройки | ✅ |
| Безопасность (LUKS) | ✅ |
| Аудит уязвимостей | ✅ |

---

## Лицензия

Apache License 2.0
