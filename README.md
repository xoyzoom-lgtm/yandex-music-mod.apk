# 🎵 YM Mod для Android

Неофициальный Android-клиент Яндекс Музыки с функциями десктопных модов: выглядит как обычное
приложение (сайт на весь экран, без своих панелей), а меню мода открывается из настроек Яндекс Музыки.

> [!NOTE]
> **Проект основан на [Vzlomhik2005/Yandex-Music-Mod](https://github.com/Vzlomhik2005/Yandex-Music-Mod)**
> (лицензия MIT) — моде для десктопного приложения Яндекс Музыки — и переделан под Android.
> Часть идей (Last.fm, тексты LRCLIB, масштаб) взята из [PulseSync-mod](https://github.com/PulseSync-LLC/PulseSync-mod) (MIT), код не копировался.
> Текст оригинальной лицензии сохранён в [`app/src/main/assets/licenses/ORIGINAL_LICENSE.txt`](app/src/main/assets/licenses/ORIGINAL_LICENSE.txt)
> и доступен в приложении на экране «О программе».

> [!IMPORTANT]
> Это **легальная** версия: приложение открывает официальный сайт [music.yandex.ru](https://music.yandex.ru),
> вход выполняется через ваш Яндекс ID, а **качество звука, реклама и доступ к трекам определяются вашей подпиской**.
> В отличие от оригинала, здесь нет обхода Яндекс Плюса, подмены ответов API, скачивания и расшифровки треков
> и «снятия бана». Без Плюса мобильная версия сайта показывает экран подписки — это решение Яндекса.
> Приложение не связано с ООО «Яндекс», Discord Inc. и Last.fm.

## Возможности

| Функция | Откуда | Статус |
|---|---|---|
| Сайт на весь экран, системные панели в цвет страницы, без водяных знаков и своих панелей | — | ✅ |
| Пункт «YM Mod — настройки мода» в настройках Яндекс Музыки, ярлык на иконке, плавающая кнопка (по желанию) | оригинал («Меню мода») | ✅ |
| Фоновое воспроизведение, уведомление с кнопками и **лайком**, экран блокировки, перемотка, гарнитура | — | ✅ |
| Высокое качество по умолчанию (в пределах вашей подписки) | оригинал (auto-best-quality) | ✅ |
| Не показывать окно «Скачайте приложение» | — | ✅ |
| Блокировка счётчиков аналитики (Яндекс Метрика) | оригинал | ✅ |
| Discord Rich Presence («Слушает …» с обложкой и таймером) | оригинал | ✅ в сборке с SDK (см. ниже) |
| Скробблинг в Last.fm | PulseSync-mod | ✅ |
| Синхронизированные тексты песен из [LRCLIB](https://lrclib.net), перемотка по строке | PulseSync-mod / оригинал | ✅ |
| Смена шрифтов (системные + любой с [Bunny Fonts](https://fonts.bunny.net)) | оригинал (font-changer) | ✅ |
| Масштаб текста 75–150% | PulseSync-mod | ✅ |
| Снег | оригинал | ✅ |
| Перенос лайков из плейлиста и ссылка на «Мне нравится» | оригинал (auto-liker) | ✅ |
| Проверка региона (Яндекс Музыка работает не во всех странах) | оригинал (ip-checker) | ✅ |
| Режим разработчика: отладка через `chrome://inspect`, сохранение разметки страницы | оригинал (devtools) | ✅ |
| Проверка обновлений в Releases | оригинал (уведомление об обновлении) | ✅ |
| Открытие ссылок music.yandex.* и «Поделиться» → YM Mod | — | ✅ |
| Тёмная / светлая / системная тема, полная версия сайта по переключателю | — | ✅ |

**Не перенесено намеренно:** обход Плюса (plus-unlocker), скачивание треков в FLAC/MP3 с расшифровкой,
«снятие бана аккаунта», подмена экспериментов, телеметрия PostHog.

### Где меню мода

- в настройках Яндекс Музыки (аватар → настройки) — кнопка **«YM Mod — настройки мода»**;
- долгое нажатие на иконку приложения → **«Настройки мода»**;
- плавающая кнопка поверх сайта — включается в меню мода.

### Если что-то на сайте не работает

Вёрстка Яндекс Музыки меняется, и разработчик может не видеть сайт из своего региона. Чтобы помочь:

1. Откройте нужный экран, затем меню мода → **«Для разработчиков» → «Сохранить разметку страницы»**.
   Сохранится HTML экрана без скриптов и введённых данных.
2. Приложите файл к issue в этом репозитории (или скриншоты экрана).

## Установка

1. Откройте [Releases](https://github.com/xoyzoom-lgtm/yandex-music-mod.apk/releases/latest) и скачайте `YmMod-*.apk`.
2. Разрешите установку из неизвестных источников и установите APK.
3. Войдите в свой аккаунт Яндекса.

Требуется Android 7.0+ и актуальный Android System WebView (обновляется через Google Play).

## Как это устроено

```
app/src/main/
├── assets/
│   ├── js/ymmod-bridge.js          # внедряется в страницу: плеер, управление, функции мода
│   └── licenses/                    # MIT этого проекта и оригинала
├── java/io/github/xoyzoom/ymmod/
│   ├── YmModApp.kt / AppContainer.kt  # точка входа и ручное DI
│   ├── MainActivity.kt              # WebView + Compose-экраны
│   ├── web/                         # WebView, мост с JS, события страницы, ссылки, блокировка счётчиков
│   ├── player/                      # модель состояния плеера, парсер сообщений, шина команд
│   ├── playback/PlaybackService.kt  # foreground-сервис + MediaSession + уведомление
│   ├── presence/                    # Rich Presence: маппинг трека → активность, интерфейс SDK
│   ├── scrobble/                    # Last.fm: подпись запросов, правила скробблинга
│   ├── lyrics/                      # LRCLIB и разбор LRC
│   ├── net/                         # HTTP, проверка обновлений и региона
│   ├── settings/                    # DataStore: все настройки мода
│   └── ui/                          # Jetpack Compose: сайт, меню мода, текст песни, «О программе»
└── res/
app/src/presenceDiscord/             # сборка с Discord Social SDK: Kotlin + JNI (C++/CMake)
app/src/presenceStub/                # сборка без SDK: заглушка
```

**Чтение плеера.** Оригинал достаёт данные из React Fiber десктопной разметки (`PLAYERBAR_DESKTOP`),
которой в мобильной вёрстке нет. Здесь `ymmod-bridge.js` использует стандартный
[Media Session API](https://developer.mozilla.org/docs/Web/API/Media_Session_API) — через него сайт сам
публикует название, исполнителя и обложку — и события `<audio>`. Данные уходят в приложение через
`WebViewCompat.addWebMessageListener`, который доступен только страницам `music.yandex.*`
(в отличие от `addJavascriptInterface`, видимого любому сайту).

**Функции мода на странице.** Настройки передаются в скрипт до загрузки страницы (`window.__ymmodConfig`
в document-start скрипте) и на лету (`__ymmod.configure`). Скрипт ставит «высокое» качество в те же
`localStorage`/cookie `ymPlayerQuality`, что и меню качества сайта, помечает окно «Скачайте приложение»
показанным, добавляет пункт мода в меню настроек (`Settings_list`) и нажимает кнопки плеера по `data-test-id`
мобильной вёрстки (`MOBILE_PLAYERBAR`, `MOBILE_NEXT_TRACK_BUTTON`, `LIKE_BUTTON` …).

**Last.fm.** Нужны свои API key и shared secret ([создать](https://www.last.fm/api/account/create)).
Вход — `auth.getMobileSession`, пароль не сохраняется. Трек скробблится, когда прослушана половина
(или 4 минуты), треки короче 30 секунд не скробблятся.

**Discord.** `PresenceController` превращает состояние плеера в `RichPresence` (название, исполнитель,
обложка, таймер) и передаёт его в `PresenceSink`. В сборке с SDK это `DiscordSocialPresenceSink`:
JNI-модуль (`app/src/presenceDiscord/cpp`) вызывает `discordpp::Client::SetApplicationId` и
`UpdateRichPresence` с типом *Listening*. Используется режим
[Rich Presence без авторизации](https://discord.com/developers/docs/discord-social-sdk/development-guides/setting-rich-presence#rich-presence-without-authentication)
(Social SDK 1.10+): активность передаётся в установленное приложение Discord, OAuth не нужен.
В сборке без SDK используется `LoggingPresenceSink`, который пишет в logcat (`adb logcat -s YmModPresence`).

## Подключение Discord Rich Presence

[Discord Social SDK](https://discord.com/developers/docs/discord-social-sdk/overview) распространяется
только через Discord Developer Portal под лицензией Discord, поэтому в репозиторий он не входит.
Без него приложение собирается и работает, просто без Discord.

1. Создайте приложение в [Discord Developer Portal](https://discord.com/developers/applications)
   и включите для него Social SDK. Название приложения увидят в профиле: «Слушает *название*».
2. Скачайте Social SDK для Android версии **1.10 или новее** и возьмите из него `discord_partner_sdk.aar`.
3. **Локальная сборка:** положите файл в `app/libs/discord_partner_sdk.aar` и укажите ID
   в `~/.gradle/gradle.properties`: `discordApplicationId=123456789012345678`.
4. **GitHub Actions:**
   - переменная репозитория `DISCORD_APPLICATION_ID` (*Settings → Secrets and variables → Actions → Variables*);
   - секрет `DISCORD_SDK_AAR_URL` — прямая ссылка на `.aar` или zip SDK в приватном хранилище.
     Скрипт `.github/scripts/fetch-discord-sdk.sh` скачает его перед сборкой.

У пользователя должно быть установлено приложение Discord с выполненным входом. Статус соединения
виден в настройках YM Mod. Разрешения SDK для голосового чата (микрофон, Bluetooth) из манифеста
убраны — приложение их не запрашивает.

## Отладка

В debug-сборке в настройках есть пункт **«Отладка: фикстуры плеера»**: готовые состояния (пауза,
длинные строки, эмодзи, нет позиции, недоступная обложка и т. д.) подменяют данные из WebView для
интерфейса, уведомления и Discord. На экране видно, какая активность уйдёт в Discord.
Сайт и его трафик не затрагиваются. Код лежит в `app/src/debug` и в релиз не попадает.

## Сборка

Нужны JDK 17 и Android SDK (API 35).

```bash
./gradlew testDebugUnitTest   # unit-тесты
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

### Релизы

Релиз собирается одним из двух способов:

- **Из интерфейса GitHub:** *Actions → Android CI → Run workflow*, ветка `main`, в поле версии
  укажите, например, `0.2.0`. Тег `v0.2.0` и релиз с APK создадутся автоматически.
- **Пушем тега:**

  ```bash
  git tag v0.2.0
  git push origin v0.2.0
  ```

Чтобы обновления ставились поверх старых версий, все релизы должны быть подписаны **одним ключом**.
Создайте ключ и добавьте секреты в *Settings → Secrets and variables → Actions*:

```bash
keytool -genkeypair -v -keystore release.jks -alias ymmod -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # → SIGNING_KEYSTORE_BASE64
```

| Секрет | Значение |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | содержимое `release.jks` в base64 |
| `SIGNING_STORE_PASSWORD` | пароль хранилища |
| `SIGNING_KEY_ALIAS` | `ymmod` |
| `SIGNING_KEY_PASSWORD` | пароль ключа |

Без секретов релиз подписывается временным debug-ключом (в логах сборки будет предупреждение).

## Лицензия

[MIT](LICENSE). © 2026 xoyzoom-lgtm (Android-версия), © 2026 Markus ([оригинальный проект](https://github.com/Vzlomhik2005/Yandex-Music-Mod)).
