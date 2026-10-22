<div align="center">

![NEO PLAYER](docs/assets/banner-fa.svg)

**پخش‌کنندهٔ موسیقی اندروید با محوریت کتابخانهٔ محلی، شخصی‌سازی پخش، قابلیت‌های هوشمند آفلاین و رابط فارسی و انگلیسی.**

<img alt="Version" src="https://img.shields.io/badge/version-v0.8.0-alpha-ff8c30?style=flat-square" />
<img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-354158?style=flat-square" />
<a href="https://github.com/Realjustawall/neo-player/actions/workflows/android-alpha.yml"><img alt="CI status" src="https://img.shields.io/github/actions/workflow/status/Realjustawall/neo-player/android-alpha.yml?branch=main&amp;label=CI&amp;style=flat-square" /></a>

<a href="https://github.com/Realjustawall/neo-player/releases/latest"><img src="https://img.shields.io/badge/%D8%AF%D8%B1%DB%8C%D8%A7%D9%81%D8%AA-ff8c30?style=flat-square&labelColor=161b22" alt="دریافت" /></a>
<a href="docs/gallery.fa.md"><img src="https://img.shields.io/badge/%DA%AF%D8%A7%D9%84%D8%B1%DB%8C%20%D8%AF%D9%85%D9%88-5968c3?style=flat-square&labelColor=161b22" alt="گالری دمو" /></a>
<a href="#quick-start"><img src="https://img.shields.io/badge/%D8%B1%D8%A7%D9%87%E2%80%8C%D8%A7%D9%86%D8%AF%D8%A7%D8%B2%DB%8C-354158?style=flat-square&labelColor=161b22" alt="راه‌اندازی" /></a>
<a href="https://github.com/Realjustawall/neo-player/issues"><img src="https://img.shields.io/badge/%DA%AF%D8%B2%D8%A7%D8%B1%D8%B4%20%D9%85%D8%B4%DA%A9%D9%84-354158?style=flat-square&labelColor=161b22" alt="گزارش مشکل" /></a>

[English](README.md) · [فارسی](README.fa.md)

</div>

---

[قابلیت‌های شاخص](#features) · [پیش‌نمایش](#preview) · [راه‌اندازی](#quick-start) · [توسعه](#development) · [مستندات](#documentation) · [امنیت و حریم خصوصی](#security) · [مجوز و بازخورد](#license)

<a id="features"></a>

## قابلیت‌های شاخص

| قابلیت | کاربرد |
|---|---|
| کتابخانهٔ محلی | آهنگ، آلبوم، هنرمند، پوشه، موردعلاقه‌ها و پلی‌لیست پایدار. |
| پخش | پخش مبتنی بر Media3، تنظیم پخش بدون فاصله، کراس‌فید دو رمزگشا، AutoMix و افکت‌های صدا. |
| Track+ | کاور، پس‌زمینه، ویدیوی Canvas، ویژوالایزر و پروفایل ظاهر برای هر آهنگ. |
| متن و هوش مصنوعی محلی | متن ساده و LRC، زمان‌بندی واژه‌ها و پیاده‌سازی اختیاری صدای فارسی و انگلیسی با Vosk. |
| ابزار آفلاین | حالت کاملاً آفلاین، پیشنهاد محلی، پشتیبان، پوشه‌بندی پلی‌لیست و ورود و خروج M3U8. |
| رادیوی محلی | میزبان و شنونده، پیوستن با QR و به‌روزرسانی زمان‌بندی همگام‌سازی. |

<a id="preview"></a>

## پیش‌نمایش

تصاویر تازهٔ اندروید هنوز آماده نیستند. [مدارک ریلیز](https://github.com/Realjustawall/neo-player/releases/tag/v0.8.0-alpha) شامل بررسی نصب و اجراست؛ تصاویر پروژهٔ دسکتاپ به‌عنوان عکس اندروید استفاده نشده‌اند.

[مشاهدهٔ گالری کامل](docs/gallery.fa.md)

<a id="quick-start"></a>

## راه‌اندازی

۱. فایل **APK ریلیز** را از دکمهٔ بالا دریافت کنید.
۲. روی اندروید ۸ یا جدیدتر نصب کنید و هنگام درخواست، اجازهٔ دسترسی به کتابخانهٔ صوتی را بدهید.
۳. موسیقی‌های دستگاه را اسکن کنید، زبان را انتخاب کنید و پخش را آغاز کنید.
۴. برای کارکرد کاملاً آفلاین، فایل متن یا مدل محلی را وارد کنید یا مدل اختیاری را از قبل آماده کنید.

ساخت حساب لازم نیست. در ریلیز معمولی، مدل‌های هوش مصنوعی اختیاری هنگام نیاز دریافت می‌شوند؛ ساخت ویژه می‌تواند مدل‌ها را از ابتدا همراه داشته باشد.

<a id="development"></a>

## توسعه

JDK 17، Android SDK 36 و Gradle 8.13.

```bash
git clone https://github.com/Realjustawall/neo-player.git
cd neo-player
gradle assembleDebug
```

دستورهای بررسی:

```bash
gradle testDebugUnitTest
gradle lintRelease
gradle assembleRelease bundleRelease
```

ساخت اختیاری همراه مدل‌ها:

```bash
gradle -PNEO_EMBED_VOSK_MODELS=true assembleRelease bundleRelease
```

<a id="documentation"></a>

## مستندات

- [مرجع کامل قابلیت و ساخت](REFERENCE.md)
- [گردش‌کارهای ساخت](.github/workflows)
- [سورس برنامهٔ اندروید](app/src/main)

<a id="security"></a>

## امنیت و حریم خصوصی

تحلیل محلی، وضعیت کتابخانه و مدل نصب‌شده روی دستگاه اجرا می‌شوند. وقتی حالت کاملاً آفلاین خاموش است، آماده‌سازی اختیاری مدل یا ارائه‌دهنده می‌تواند به شبکه متصل شود. دسترسی صوتی برای فهرست‌سازی لازم است؛ فایل پشتیبان را در محل تحت کنترل خود نگه دارید.

<a id="license"></a>

## مجوز و بازخورد

تحت مجوز [Apache-2.0](LICENSE) منتشر می‌شود.

[گزارش اشکال و پیشنهاد بهبود](https://github.com/Realjustawall/neo-player/issues). نسخه، سیستم‌عامل و روش بازتولید را بنویسید و اطلاعات خصوصی یا رمزها را در گزارش قرار ندهید.

---

نگهداری توسط **[Realjustawall](https://github.com/Realjustawall)**.
