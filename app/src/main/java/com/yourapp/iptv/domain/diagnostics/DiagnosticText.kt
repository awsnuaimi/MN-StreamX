package com.yourapp.iptv.domain.diagnostics

import com.yourapp.iptv.domain.model.HomeLanguage

class DiagnosticText(private val language: HomeLanguage) {
    fun text(ar: String, en: String, de: String) = when (language) {
        HomeLanguage.ARABIC -> ar
        HomeLanguage.ENGLISH -> en
        HomeLanguage.GERMAN -> de
    }
    fun title() = text("تشخيص الأعطال","Diagnostics","Fehlerdiagnose")
    fun start() = text("بدء التشخيص","Run diagnostics","Diagnose starten")
    fun cancel() = text("إلغاء الفحص","Cancel checks","Diagnose abbrechen")
    fun share() = text("مشاركة التقرير","Share report","Bericht teilen")
    fun clear() = text("مسح سجل الجلسة","Clear session log","Sitzungsprotokoll leeren")
    fun back() = text("رجوع","Back","Zurück")
    fun privacy() = text("التقرير يستبعد بيانات الدخول ومفاتيح الخدمات وروابط البث وأسماء القنوات ومعرّفات الجهاز.",
        "Reports exclude credentials, service keys, stream URLs, channel names and device identifiers.",
        "Berichte enthalten keine Zugangsdaten, Dienstschlüssel, Stream-URLs, Sendernamen oder Gerätekennungen.")
    fun requestNote() = text("يُجري الفحص طلبات قراءة محدودة إلى مصدر الاشتراك. لا يفتح فيديو أو يحمّل القائمة كاملة.",
        "Checks make a few bounded read requests to the subscription source. No video or full catalog is loaded.",
        "Die Prüfung sendet wenige begrenzte Leseanfragen an die Abo-Quelle. Kein Video oder vollständiger Katalog wird geladen.")
    fun categoryNote() = text("فحص التصنيفات لا يثبت توفر كل العناوين أو قابلية تشغيلها. اختبر الفيديو ثم ارجع إلى سجل الجلسة.",
        "Category checks do not prove that every title is available or playable. Play a video, then inspect the session log.",
        "Die Kategorienprüfung bestätigt nicht die Verfügbarkeit oder Wiedergabe aller Titel. Ein Video testen und danach das Sitzungsprotokoll prüfen.")
    fun sessionNote() = text("الأحداث تخص جلسة التطبيق الحالية فقط؛ لا يمثل هذا سجل انهيار أندرويد.",
        "Events belong to the current app process only; this is not an Android crash log.",
        "Ereignisse gelten nur für die aktuelle App-Sitzung; dies ist kein Android-Absturzprotokoll.")
    fun device() = text("الجهاز والموارد","Device and resources","Gerät und Ressourcen")
    fun checks() = text("نتائج فحص المصدر","Source checks","Prüfung der Quelle")
    fun events() = text("آخر أحداث الجلسة","Recent session events","Letzte Sitzungsereignisse")
    fun noEvents() = text("لا توجد أعطال مسجلة في هذه الجلسة.","No failures have been recorded in this session.","In dieser Sitzung wurden keine Fehler aufgezeichnet.")
    fun idle() = text("اضغط بدء التشخيص لفحص المصدر.","Run diagnostics to check the source.","Diagnose starten, um die Quelle zu prüfen.")
    fun busy() = text("جارٍ الفحص…","Checking…","Prüfung läuft…")
    fun exporting() = text("جارٍ إنشاء التقرير…","Preparing report…","Bericht wird erstellt…")
    fun failure() = text("تعذر إكمال العملية. أعد المحاولة.","The operation could not be completed. Retry.","Vorgang konnte nicht abgeschlossen werden. Erneut versuchen.")
    fun shareFailed() = text("تعذر فتح تطبيق لمشاركة التقرير على هذا الجهاز.","A report sharing app could not be opened on this device.","Auf diesem Gerät konnte keine App zum Teilen des Berichts geöffnet werden.")
    fun current() = text("المصدر الحالي","Current source","Aktuelle Quelle")
    fun other() = text("مصدر آخر في الجلسة","Another session source","Andere Quelle der Sitzung")
    fun app() = text("التطبيق","Application","Anwendung")
    fun unknown() = text("غير متاح","Unavailable","Nicht verfügbar")
    fun yes() = text("نعم","Yes","Ja")
    fun no() = text("لا","No","Nein")
    fun area(value: DiagnosticArea) = when (value) {
        DiagnosticArea.CONNECTION -> text("الاتصال","Connection","Verbindung")
        DiagnosticArea.ACCOUNT -> text("حساب الاشتراك","Subscription account","Abo-Konto")
        DiagnosticArea.PLAYLIST -> text("قائمة M3U","M3U playlist","M3U-Liste")
        DiagnosticArea.LIVE_CATALOG -> text("تصنيفات القنوات","Live categories","Senderkategorien")
        DiagnosticArea.MOVIE_CATALOG -> text("تصنيفات الأفلام","Movie categories","Filmkategorien")
        DiagnosticArea.SERIES_CATALOG -> text("تصنيفات المسلسلات","Series categories","Serienkategorien")
        DiagnosticArea.DETAILS -> text("تفاصيل المحتوى","Media details","Inhaltsdetails")
        DiagnosticArea.PLAYBACK -> text("التشغيل","Playback","Wiedergabe")
        DiagnosticArea.UPDATE -> text("تحديث التطبيق","App update","App-Update")
        DiagnosticArea.SYSTEM -> text("موارد الجهاز","Device resources","Geräteressourcen")
    }
    fun status(value: DiagnosticStatus) = when (value) {
        DiagnosticStatus.PASS -> text("ناجح","Passed","Erfolgreich")
        DiagnosticStatus.NOTICE -> text("ملاحظة","Notice","Hinweis")
        DiagnosticStatus.FAIL -> text("فشل","Failed","Fehlgeschlagen")
    }
    fun issue(value: DiagnosticIssue) = when (value) {
        DiagnosticIssue.OK -> text("استجابة ناجحة","Successful response","Erfolgreiche Antwort")
        DiagnosticIssue.NO_SOURCE -> text("لم يُضبط اشتراك صالح","No subscription configured","Kein Abo eingerichtet")
        DiagnosticIssue.OFFLINE -> text("لا توجد شبكة نشطة","No active network","Kein aktives Netzwerk")
        DiagnosticIssue.UNVALIDATED -> text("لم يؤكد أندرويد الوصول للإنترنت؛ قد يبقى المصدر متاحًا","Android has not validated Internet access; the source may still work","Android hat den Internetzugang nicht bestätigt; die Quelle kann dennoch erreichbar sein")
        DiagnosticIssue.DNS -> text("تعذر حل اسم الخادم","DNS lookup failed","DNS-Auflösung fehlgeschlagen")
        DiagnosticIssue.TIMEOUT -> text("انتهت مهلة الاتصال أو القراءة","Connection or read timed out","Zeitüberschreitung bei Verbindung oder Lesen")
        DiagnosticIssue.TLS -> text("تعذر تأمين الاتصال؛ تحقق من الشهادة ووقت الجهاز","TLS failed; check the certificate and device time","TLS fehlgeschlagen; Zertifikat und Gerätezeit prüfen")
        DiagnosticIssue.CONNECTION -> text("تعذر الاتصال أو انقطع نقل البيانات","Connection failed or transfer interrupted","Verbindung fehlgeschlagen oder Übertragung unterbrochen")
        DiagnosticIssue.HTTP -> text("أعاد الخادم حالة HTTP غير ناجحة","The server returned an unsuccessful HTTP status","Der Server meldet einen erfolglosen HTTP-Status")
        DiagnosticIssue.AUTH_REJECTED -> text("رفض الخادم بيانات الدخول","The server rejected authentication","Der Server hat die Anmeldung abgelehnt")
        DiagnosticIssue.ACCOUNT_INACTIVE -> text("الخادم يصف الحساب بأنه غير نشط","The server reports an inactive account","Der Server meldet ein inaktives Konto")
        DiagnosticIssue.INVALID_RESPONSE -> text("صيغة الاستجابة غير متوقعة","Unexpected response format","Unerwartetes Antwortformat")
        DiagnosticIssue.EMPTY_RESPONSE -> text("استجابة الخادم فارغة","The server response is empty","Die Serverantwort ist leer")
        DiagnosticIssue.LIMIT -> text("تجاوزت الاستجابة حد الفحص المختصر؛ النتيجة غير حاسمة","Response exceeds the bounded diagnostic limit; result is inconclusive","Antwort überschreitet die begrenzte Prüfung; Ergebnis ist nicht abschließend")
        DiagnosticIssue.REDIRECT -> text("أعاد الخادم تحويلًا؛ لم يُتبع خلال الفحص","The server redirected; diagnostics did not follow it","Der Server hat umgeleitet; die Diagnose ist nicht gefolgt")
        DiagnosticIssue.PLAYLIST_HEADER -> text("ترويسة M3U موجودة في بداية الاستجابة","M3U header found in the response prefix","M3U-Kopf im Anfang der Antwort gefunden")
        DiagnosticIssue.CATEGORIES -> text("عدد التصنيفات التي أعادها الخادم","Number of categories returned by the server","Anzahl der vom Server gelieferten Kategorien")
        DiagnosticIssue.PLAYBACK -> text("أبلغ المشغّل عن عطل؛ راجع الرمز الفني","Player reported a failure; inspect the technical code","Der Player meldet einen Fehler; technischen Code prüfen")
        DiagnosticIssue.DECODER -> text("مشكلة في مفكّك الترميز أو توافق الصيغة؛ راجع الرمز والذاكرة المتاحة","Decoder or format compatibility problem; inspect the code and available memory","Decoder- oder Formatproblem; Code und verfügbaren Speicher prüfen")
        DiagnosticIssue.UPDATE -> text("تعذر إكمال تحديث التطبيق؛ راجع رمز الحالة","App update could not complete; inspect the issue code","App-Update konnte nicht abgeschlossen werden; Fehlercode prüfen")
        DiagnosticIssue.HISTORY_WRITE -> text("تعذّر حفظ سجل المشاهدة في التخزين المحلي", "Watch history could not be saved to local storage", "Der Wiedergabeverlauf konnte nicht lokal gespeichert werden")
        DiagnosticIssue.HISTORY_QUEUE -> text("تعذّر قبول سجل مشاهدة إضافي؛ طابور الحفظ ممتلئ أو غير متاح", "Another watch event could not be accepted; the write queue is full or unavailable", "Ein weiterer Verlaufseintrag konnte nicht angenommen werden; die Schreibwarteschlange ist voll oder nicht verfügbar")
        DiagnosticIssue.INTERNAL -> text("تعذر إكمال العملية داخل التطبيق","The app could not complete the operation","Die App konnte den Vorgang nicht abschließen")
        DiagnosticIssue.CANCELLED -> text("أُلغي الفحص أو تغيّر المصدر","Checks cancelled or source changed","Prüfung abgebrochen oder Quelle gewechselt")
    }
}