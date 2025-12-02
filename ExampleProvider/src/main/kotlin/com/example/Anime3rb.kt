package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Anime3rb : MainAPI() {
    override var mainUrl = "https://anime3rb.com"
    override var name = "Anime3rb"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "$mainUrl/search?q=" to "بحث",
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val res = app.get(url)
        val doc = res.document
        
        // بحث ذكي: نبحث عن أي رابط يؤدي لصفحة أنمي (يحتوي على /titles/)
        // ونتأكد أنه يحتوي على صورة وعنوان لكي لا نسحب روابط فارغة
        return doc.select("a[href*='/titles/']").mapNotNull {
            val href = it.attr("href")
            val container = it.parent() // الصعود للأب للتأكد من المحتوى
            
            // محاولة التقاط الصورة والعنوان من الرابط نفسه أو من العنصر المحيط به
            val posterUrl = it.select("img").attr("src").ifEmpty { 
                container?.select("img")?.attr("src") ?: "" 
            }
            val title = it.text().ifEmpty { 
                container?.select("h3, .title")?.text() ?: "انمي بدون عنوان" 
            }
            
            // تجاهل الروابط التي لا تحتوي على صورة (لتجنب الروابط النصية فقط)
            if(posterUrl.isEmpty()) return@mapNotNull null

            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(posterUrl)
            }
        }.distinctBy { it.url } // منع التكرار
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1").text().trim()
        val poster = doc.select("img[class*='object-cover']").attr("src")
        val description = doc.select("p.font-light, div.story").text()

        // استخراج الحلقات بناءً على الكود الذي أرسلته (فئة btn ورابط episode)
        val episodes = doc.select("a[href*='/episode/']").mapNotNull {
            val href = it.attr("href")
            // البحث داخل div.video-data كما ظهر في الكود الخاص بك
            val videoData = it.select("div.video-data")
            val epName = videoData.select("span").text() // "الحلقة 1"
            val epNum = Regex("(\\d+)").find(epName)?.value?.toIntOrNull()
            
            // التقاط صورة الحلقة المصغرة إن وجدت
            val epThumb = it.select("img").attr("src")

            newEpisode(fixUrl(href)) {
                this.name = epName
                this.episode = epNum
                this.posterUrl = fixUrl(epThumb)
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrl(poster)
            this.plot = description
            addEpisodes(DubStatus.Sub, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // 1. استخراج رابط التحميل المباشر (بناءً على الكود الذي أرسلته)
        // نبحث عن الرابط الذي يحتوي على كلمة "download"
        doc.select("a[href*='/download/']").forEach { link ->
            val downloadUrl = link.attr("href")
            val text = link.text() // مثال: تحميل مباشر [334.66 ميغابايت]
            
            callback.invoke(
                newExtractorLink(
                    source = "تحميل مباشر",
                    name = text, // سيظهر النص مع الحجم
                    url = downloadUrl,
                    referer = mainUrl,
                    quality = getQualityFromName(text)
                )
            )
        }

        // 2. البحث عن السيرفرات الأخرى (Iframe) للاحتياط
        doc.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            loadExtractor(src, subtitleCallback, callback)
        }

        return true
    }
}
