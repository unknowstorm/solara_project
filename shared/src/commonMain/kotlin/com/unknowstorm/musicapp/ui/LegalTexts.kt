package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.tr

/**
 * Terms of use and privacy policy texts (English and Ukrainian).
 * BEFORE PUBLISHING: fill in CONTACT_EMAIL and check that every item matches how the app really works.
 * This is a template, not legal advice.
 */
object LegalTexts {
    const val CONTACT_EMAIL = "musics.solara@gmail.com"
    const val YOUTUBE_TERMS_URL = "https://www.youtube.com/t/terms"
    const val GOOGLE_PRIVACY_URL = "http://www.google.com/policies/privacy"
    const val GOOGLE_PERMISSIONS_URL = "https://security.google.com/settings/security/permissions"
    const val SPOTIFY_TERMS_URL = "https://www.spotify.com/legal/end-user-agreement/"
    const val SPOTIFY_PRIVACY_URL = "https://www.spotify.com/legal/privacy-policy/"
    const val SPOTIFY_APPS_URL = "https://www.spotify.com/account/apps/"

    val CONSENT_LINE: String
        get() = tr(
            "I accept the Solara Terms of Use, including the YouTube Terms of Service, and the Privacy Policy.",
            "Я приймаю Умови використання Solara, зокрема Умови використання YouTube, і Політику конфіденційності.",
        )

    val YOUTUBE_INTRO: String get() = tr(YOUTUBE_INTRO_EN, YOUTUBE_INTRO_UK)
    val TERMS: String get() = tr(TERMS_EN, TERMS_UK)
    val PRIVACY: String get() = tr(PRIVACY_EN, PRIVACY_UK)

    // ---------- English ----------

    private val YOUTUBE_INTRO_EN = """
        The YouTube section uses YouTube API Services. Videos play in the embedded YouTube player while the video is visible on screen (full-size or in the floating window). Background playback is not supported for YouTube; this is a YouTube requirement.

        To continue, read the terms below and confirm your agreement.
    """.trimIndent()

    private val TERMS_EN = """
        1. Solara is a student project. The app is provided “as is”.

        2. Music from Jamendo, Audius and Deezer is played under the terms of those services and the authors' licenses. Rights to tracks and artwork belong to their authors. Jamendo is credited as the provider of its music and its artists as the authors of the tracks, and each Jamendo track links to its page on Jamendo. Audius tracks that the artist has closed for third-party apps or made paid are not played.

        3. The YouTube section uses YouTube API Services. By using it, you agree to be bound by the YouTube Terms of Service: $YOUTUBE_TERMS_URL. Videos play only in the embedded YouTube player while the video is visible on screen (full-size or in the floating window). Solara does not download or store videos and does not make copies of them.

        4. The Spotify section controls the Spotify app on your device: the Spotify app itself plays the music, and Spotify Premium is required. Solara does not play, download or store Spotify music or artwork. By using this section, you agree to Spotify's terms: $SPOTIFY_TERMS_URL.
        • Spotify gives no warranties regarding Solara, including implied warranties of merchantability, fitness for a particular purpose and non-infringement, and is not liable for it or for any damage from its use. The developer of Solara is responsible for Solara.
        • You may not modify, or create derivative works based on, the Spotify platform, service or content.
        • You may not decompile, disassemble or reverse engineer Solara, including the Spotify section, except where the law does not allow this to be prohibited.
        • Spotify is a third-party beneficiary of these terms and of the Privacy Policy with respect to the Spotify section and may enforce them directly.

        5. Import from Jamendo and Audius is optional and one-way. Solara reads only the public profile (favorites and playlists) of the account you specify and does not ask for your password or access token. Specify only your own account or one you have the right to use. Imported items are marked as imported; to remove them, tap “Disconnect” in Settings and tick “Also delete imported tracks and playlists”. Solara sends nothing back to Jamendo or Audius.

        6. Solara is not affiliated with Google, YouTube, Spotify, Deezer, Jamendo or Audius. Their names and logos belong to their owners.

        7. Questions: $CONTACT_EMAIL.
    """.trimIndent()

    private val PRIVACY_EN = """
        1. Who is responsible for the data. The Solara project (a student project). Contact: $CONTACT_EMAIL.

        2. What data we process.
        • Account: email address, nickname, avatar and settings. When you sign in with Google, we receive your Google profile name, email address and photo. We request access to your YouTube account data only if you connect it yourself (see section 3).
        • Library: likes, playlists and listening history for music from Jamendo, Audius, Deezer and local files. They are used for recommendations. Spotify and YouTube data does not enter the library.
        • Imports: if you connect a Jamendo or Audius account, we store its public ID and name, the date of the last import, and the likes and playlists read from the public profile (track titles, artists, artwork links and order). Imported items are marked as imported.
        • Technical data: your IP address and device information are passed to the services the app contacts (Supabase, Jamendo, Audius, Deezer, YouTube, Spotify).

        3. YouTube API Services. The YouTube section uses YouTube API Services. By using it, you agree to be bound by the YouTube Terms of Service ($YOUTUBE_TERMS_URL). Google Privacy Policy: $GOOGLE_PRIVACY_URL.
        • Your search query is sent to the YouTube Data API.
        • Videos play in the embedded YouTube player. When it loads, YouTube receives data about your device and the app, and the player may use cookies and local storage and show ads. Solara does not control this.
        • The video you pick from the list starts automatically. Therefore the YouTube player collects and transmits device and app data already when it loads and while it plays, not after a separate “Play” tap. A video never starts on its own without your selection.
        • YouTube search results are kept only in the memory of the running app. Solara does not save videos and does not add YouTube data to likes, playlists, history or recommendations.
        • Videos marked by YouTube as “made for kids” are not shown in Solara.
        • Connecting your YouTube account is optional and starts only when you tap “Connect”. You sign in on Google's own page; Solara never sees your Google password. Solara asks only for read-only access (youtube.readonly) to your playlists and liked videos.
        • These playlists and liked videos are read through the YouTube API and shown only in the YouTube section. They are kept only in the memory of the running app, are not stored on our server, are not added to likes, playlists, history, recommendations or the queue, and are not mixed with other sources.
        • On our server we store only an encrypted token that lets Solara read your YouTube data again, and the date of connection. The token is not available to the app on your device. We check regularly that access has not been revoked.
        • “Disconnect” revokes the token at Google and deletes it from our server right away. This does not affect your data on YouTube itself. If you revoke access on Google's page ($GOOGLE_PERMISSIONS_URL), we delete the stored token within 30 days. Deleting your Solara account also deletes the token.
        • Solara's use and transfer to any other app of information received from Google APIs will adhere to the Google API Services User Data Policy (https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements. This data is used only to show you your own playlists and liked videos in the YouTube section. It is not sold, not used for advertising or to build user profiles, and not transferred to third parties except as needed to provide this feature or to comply with law. People do not read this data unless you give your consent, it is necessary for security purposes, or it is required by law.

        4. Spotify. The Spotify section controls the Spotify app on your device; the Spotify app itself plays the music (Spotify Premium is required). Spotify terms: $SPOTIFY_TERMS_URL. Spotify Privacy Policy: $SPOTIFY_PRIVACY_URL.
        • After connecting, Solara receives from the Spotify app the title and artist of the current track, the artwork, the playback state and position, and Spotify's recommendation collections, in order to show them in the mini-player, the player and the Spotify section.
        • Optional library view (Spotify Web API): if you tap “Import my Spotify library”, you sign in on Spotify's own page (Solara never sees your Spotify password) and give read-only access (user-library-read, playlist-read-private, playlist-read-collaborative). Solara then reads your Spotify profile name, the names of your playlists and the titles, artists and artwork of your saved tracks and of the tracks in playlists you own or collaborate on, only to show them to you in the Spotify section. The playlists and tracks are kept only in the memory of the running app: they are not sent to our server, not saved on the device, not added to likes, playlists, history, recommendations or the queue, and not mixed with other sources; they are erased when you close the app. So that the library and your profile name are shown again each time you open the Spotify section or Settings without tapping “Import”, Solara stores one encrypted Spotify refresh token on this device only (never on our server). It is deleted when you tap “Turn off”, “Remove import and sign out” or “Disconnect Spotify”, or if Spotify rejects it because you revoked access. Every item links back to Spotify.
        • This data is kept only in the memory of the running app and is erased when you disconnect Spotify and when you close the app. Solara does not store it on a server or on the device, and does not add it to likes, playlists, history or recommendations.
        • The “+” button sends the track to “Liked Songs” in your Spotify account. Solara stores nothing about this itself.
        • You can disconnect Spotify with the “Disconnect Spotify” button in the Spotify section. You can revoke the app's access to your Spotify account at: $SPOTIFY_APPS_URL.
        • Spotify is a third-party beneficiary of this Privacy Policy with respect to the Spotify section and may enforce it directly.

        5. Import from Jamendo and Audius (optional). You can import likes and playlists from your public Jamendo or Audius profile.
        • You enter a nickname, a profile link or an ID. Solara reads only public data: favorites and playlists. We do not ask for your password or an access token, and we do not store any.
        • We store the public account ID and name, the date of the last import, and the imported likes and playlists (titles, artists, artwork links, order). The data is stored with your account at Supabase and is used only in your library and for recommendations.
        • The import runs on our server (a Supabase function), which sends requests to the Jamendo and Audius APIs with the ID you entered.
        • “Import again” adds new likes and playlists. “Disconnect” removes the link to the account; with the option “Also delete imported tracks and playlists” everything that was imported is deleted. Your own likes and playlists created in Solara are not affected.
        • The import is one-way: Solara does not change anything in your Jamendo or Audius account. Later changes there appear in Solara only after you tap “Import again”, and items removed there are not removed here.
        • Jamendo and Audius process data under their own policies. Tracks that an artist has closed for third-party apps may be skipped during playback.

        6. Who receives data: Supabase (accounts and library), Google (sign-in and YouTube), Spotify (controlling the Spotify app on your device), Jamendo, Audius, Deezer (catalog requests).

        7. Cookies and on-device data. The embedded YouTube player may store cookies. The app stores local settings, for example your agreement to the terms.

        8. Your rights. You can request deletion of your data and account by writing to $CONTACT_EMAIL. Imported data can also be deleted in the app: Settings → “Disconnect” with the option “Also delete imported tracks and playlists”. The YouTube connection is removed with “Disconnect” (it revokes the token and deletes it from our server).

        9. Google access. If you signed in with Google, you can revoke access at: $GOOGLE_PERMISSIONS_URL.

        10. Contact. Questions and complaints about privacy: $CONTACT_EMAIL.
    """.trimIndent()

    // ---------- Українська ----------

    private val YOUTUBE_INTRO_UK = """
        Розділ YouTube використовує YouTube API Services. Відео відтворюється у вбудованому плеєрі YouTube, доки відео видно на екрані (у повному вигляді або в плаваючому вікні). Фонове відтворення для YouTube не підтримується, це вимога YouTube.

        Щоб продовжити, прочитайте умови нижче й підтвердьте згоду.
    """.trimIndent()

    private val TERMS_UK = """
        1. Solara — навчальний проєкт. Застосунок надається «як є».

        2. Музика з Jamendo, Audius і Deezer відтворюється на умовах цих сервісів і ліцензій авторів. Права на треки та обкладинки належать їхнім авторам. Jamendo вказується як постачальник своєї музики, а її виконавці як автори треків, і кожен трек Jamendo має посилання на свою сторінку на Jamendo. Треки Audius, які виконавець закрив для сторонніх застосунків або зробив платними, не відтворюються.

        3. Розділ YouTube використовує YouTube API Services. Користуючись ним, ви погоджуєтеся дотримуватися Умов використання YouTube: $YOUTUBE_TERMS_URL. Відео відтворюється лише у вбудованому плеєрі YouTube, доки відео видно на екрані (у повному вигляді або в плаваючому вікні). Solara не завантажує й не зберігає відео та не створює їхніх копій.

        4. Розділ Spotify керує застосунком Spotify на вашому пристрої: музику відтворює сам застосунок Spotify, потрібен Spotify Premium. Solara не відтворює, не завантажує й не зберігає музику та обкладинки Spotify. Користуючись цим розділом, ви погоджуєтеся з умовами Spotify: $SPOTIFY_TERMS_URL.
        • Spotify не надає жодних гарантій щодо Solara, зокрема неявних гарантій товарної придатності, придатності для певної мети та непорушення прав, і не відповідає за неї та за шкоду від її використання. За Solara відповідає її розробник.
        • Заборонено змінювати платформу, сервіс чи контент Spotify та створювати похідні роботи на їхній основі.
        • Заборонено декомпілювати, розбирати та піддавати зворотному проєктуванню Solara, зокрема розділ Spotify, крім випадків, коли закон не дозволяє це забороняти.
        • Spotify є третьою стороною-бенефіціаром цих умов і Політики конфіденційності в частині, що стосується розділу Spotify, і має право безпосередньо вимагати їх дотримання.

        5. Імпорт з Jamendo і Audius є добровільним і односторонньому. Solara читає лише публічний профіль (вподобання та плейлисти) вказаного вами акаунта й не запитує ваш пароль чи токен доступу. Вказуйте лише власний акаунт або той, який ви маєте право використовувати. Імпортовані елементи позначаються як імпортовані; щоб їх видалити, натисніть у «Налаштуваннях» «Відключити» й позначте «Також видалити імпортовані треки й плейлисти». Solara нічого не надсилає назад до Jamendo чи Audius.

        6. Solara не пов’язана з Google, YouTube, Spotify, Deezer, Jamendo і Audius. Їхні назви та логотипи належать їхнім власникам.

        7. Запитання: $CONTACT_EMAIL.
    """.trimIndent()

    private val PRIVACY_UK = """
        1. Хто відповідає за дані. Проєкт Solara (навчальний проєкт). Контакт: $CONTACT_EMAIL.

        2. Які дані ми обробляємо.
        • Акаунт: адреса електронної пошти, нікнейм, аватар і налаштування. Під час входу через Google ми отримуємо ім’я, адресу пошти та фото вашого профілю Google. Ми запитуємо доступ до даних вашого акаунта YouTube лише якщо ви підключите його самі (див. розділ 3).
        • Бібліотека: вподобання, плейлисти та історія прослуховування музики з Jamendo, Audius, Deezer і локальних файлів. Вони використовуються для рекомендацій. Дані Spotify і YouTube до бібліотеки не потрапляють.
        • Імпорт: якщо ви підключаєте акаунт Jamendo або Audius, ми зберігаємо його публічний id та ім’я, дату останнього імпорту й вподобання та плейлисти, прочитані з публічного профілю (назви треків, виконавці, посилання на обкладинки та порядок). Імпортовані елементи позначаються як імпортовані.
        • Технічні дані: IP-адреса та відомості про пристрій передаються сервісам, до яких звертається застосунок (Supabase, Jamendo, Audius, Deezer, YouTube, Spotify).

        3. YouTube API Services. Розділ YouTube використовує YouTube API Services. Користуючись ним, ви погоджуєтеся дотримуватися Умов використання YouTube ($YOUTUBE_TERMS_URL). Політика конфіденційності Google: $GOOGLE_PRIVACY_URL.
        • Ваш пошуковий запит надсилається до YouTube Data API.
        • Відео відтворюється вбудованим плеєром YouTube. Під час його завантаження YouTube отримує дані про пристрій і застосунок, а плеєр може використовувати cookies і локальне сховище та показувати рекламу. Solara цим не керує.
        • Відео, яке ви вибрали у списку, запускається автоматично. Тому плеєр YouTube збирає й передає дані про пристрій і застосунок уже під час завантаження та відтворення, а не після окремого натискання «Play». Відео ніколи не запускається саме без вашого вибору.
        • Результати пошуку YouTube зберігаються лише в пам’яті запущеного застосунку. Solara не зберігає відео й не додає дані YouTube до вподобань, плейлистів, історії та рекомендацій.
        • Відео, позначені YouTube як «для дітей», у Solara не показуються.
        • Підключення акаунта YouTube є добровільним і починається лише після натискання «Підключити». Ви входите на сторінці самого Google; Solara не бачить ваш пароль Google. Solara запитує лише доступ на читання (youtube.readonly) до ваших плейлистів і вподобаних відео.
        • Ці плейлисти та вподобані відео читаються через YouTube API й показуються лише в розділі YouTube. Вони зберігаються лише в пам’яті запущеного застосунку, не зберігаються на нашому сервері, не додаються до вподобань, плейлистів, історії, рекомендацій і черги та не змішуються з іншими джерелами.
        • На нашому сервері ми зберігаємо лише зашифрований токен, який дозволяє Solara знову читати ваші дані YouTube, і дату підключення. Токен недоступний застосунку на вашому пристрої. Ми регулярно перевіряємо, чи не відкликано доступ.
        • «Відключити» відкликає токен у Google і одразу видаляє його з нашого сервера. Це не зачіпає ваші дані на самому YouTube. Якщо ви відкличете доступ на сторінці Google ($GOOGLE_PERMISSIONS_URL), ми видалимо збережений токен протягом 30 днів. Видалення акаунта Solara також видаляє токен.
        • Використання та передавання Solara будь-якому іншому застосунку інформації, отриманої від Google API, відповідатиме Політиці щодо даних користувачів у сервісах Google API (Google API Services User Data Policy, https://developers.google.com/terms/api-services-user-data-policy), зокрема вимогам Limited Use (обмеженого використання). Ці дані використовуються лише для показу вам ваших плейлистів і вподобаних відео в розділі YouTube. Їх не продають, не використовують для реклами чи створення профілів користувачів і не передають третім особам, окрім випадків, необхідних для роботи цієї функції або виконання закону. Люди не читають ці дані, крім випадків, коли ви дали згоду, це потрібно з міркувань безпеки або вимагає закон.

        4. Spotify. Розділ Spotify керує застосунком Spotify на вашому пристрої, музику відтворює сам застосунок Spotify (потрібен Spotify Premium). Умови Spotify: $SPOTIFY_TERMS_URL. Політика конфіденційності Spotify: $SPOTIFY_PRIVACY_URL.
        • Після підключення Solara отримує від застосунку Spotify назву та виконавця поточного треку, обкладинку, стан і позицію відтворення, а також добірки рекомендацій Spotify, щоб показати їх у міні-плеєрі, плеєрі та розділі Spotify.
        • Додатковий перегляд бібліотеки (Spotify Web API): якщо ви натиснете «Імпортувати мою бібліотеку Spotify», ви входите на сторінці самого Spotify (Solara не бачить ваш пароль Spotify) і надаєте доступ лише на читання (user-library-read, playlist-read-private, playlist-read-collaborative). Тоді Solara читає ім’я вашого профілю Spotify, назви ваших плейлистів, а також назви, виконавців і обкладинки збережених треків і треків у плейлистах, якими ви володієте або які ведете спільно, лише щоб показати їх вам у розділі Spotify. Плейлисти й треки зберігаються лише в пам’яті запущеного застосунку: вони не надсилаються на наш сервер, не зберігаються на пристрої, не додаються до вподобань, плейлистів, історії, рекомендацій і черги та не змішуються з іншими джерелами; вони стираються, коли ви закриваєте застосунок. Щоб бібліотека й ім’я профілю показувались знову при кожному відкритті розділу Spotify або «Налаштувань» без натискання «Імпортувати», Solara зберігає лише на цьому пристрої (не на нашому сервері) один зашифрований токен оновлення Spotify. Він видаляється, коли ви натискаєте «Вимкнути», «Видалити імпорт і вийти» або «Відключити Spotify», або якщо Spotify його відхиляє, бо ви відкликали доступ. Кожен елемент містить посилання на Spotify.
        • Ці дані зберігаються лише в пам’яті запущеного застосунку та стираються під час відключення Spotify і закриття застосунку. Solara не зберігає їх на сервері й на пристрої, не додає до вподобань, плейлистів, історії та рекомендацій.
        • Кнопка «+» надсилає трек до «Улюблених треків» вашого акаунта Spotify. Solara у себе про це нічого не зберігає.
        • Відключити Spotify можна кнопкою «Відключити Spotify» в розділі Spotify. Доступ застосунку до вашого акаунта Spotify можна відкликати на сторінці: $SPOTIFY_APPS_URL.
        • Spotify є третьою стороною-бенефіціаром цієї Політики конфіденційності в частині, що стосується розділу Spotify, і має право безпосередньо вимагати її дотримання.

        5. Імпорт з Jamendo і Audius (за бажанням). Ви можете імпортувати вподобання та плейлисти зі свого публічного профілю Jamendo або Audius.
        • Ви вводите нік, посилання на профіль або id. Solara читає лише публічні дані: вподобання та плейлисти. Ми не запитуємо пароль чи токен доступу й не зберігаємо їх.
        • Ми зберігаємо публічний id і ім’я акаунта, дату останнього імпорту та імпортовані вподобання й плейлисти (назви, виконавців, посилання на обкладинки, порядок). Дані зберігаються разом з вашим акаунтом у Supabase і використовуються лише у вашій бібліотеці та для рекомендацій.
        • Імпорт виконується на нашому сервері (функція Supabase), яка надсилає запити до API Jamendo і Audius із введеним вами id.
        • «Імпортувати знову» додає нові вподобання й плейлисти. «Відключити» прибирає зв’язок з акаунтом; якщо позначити «Також видалити імпортовані треки й плейлисти», усе імпортоване буде видалено. Ваші власні вподобання й плейлисти, створені в Solara, це не зачіпає.
        • Імпорт односторонній: Solara нічого не змінює у вашому акаунті Jamendo чи Audius. Пізніші зміни там з’являються в Solara лише після натискання «Імпортувати знову», а елементи, видалені там, тут не видаляються.
        • Jamendo і Audius обробляють дані за власними політиками. Треки, які виконавець закрив для сторонніх застосунків, можуть пропускатися під час відтворення.

        6. Кому передаються дані: Supabase (акаунти та бібліотека), Google (вхід і YouTube), Spotify (керування застосунком Spotify на вашому пристрої), Jamendo, Audius, Deezer (запити до каталогів).

        7. Cookies і дані на пристрої. Вбудований плеєр YouTube може зберігати cookies. Застосунок зберігає локальні налаштування, наприклад вашу згоду з умовами.

        8. Ваші права. Ви можете запросити видалення своїх даних і акаунта, написавши на $CONTACT_EMAIL. Імпортовані дані можна також видалити в застосунку: «Налаштування» → «Відключити» з позначкою «Також видалити імпортовані треки й плейлисти». Підключення YouTube знімається кнопкою «Відключити» (вона відкликає токен і видаляє його з нашого сервера).

        9. Доступ Google. Якщо ви входили через Google, ви можете відкликати доступ на сторінці: $GOOGLE_PERMISSIONS_URL.

        10. Контакти. Запитання та скарги щодо конфіденційності: $CONTACT_EMAIL.
    """.trimIndent()
}
