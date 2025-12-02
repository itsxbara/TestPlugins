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
        
        // استخدام محدد عام وآمن للبحث عن البطاقات لتجنب الأخطاء
        return doc.select("a[href*='/titles/']").mapNotNull {
            val href = it.attr("href")
            val container = it.parent()
            
            // محاولة التقاط الصورة من داخل الرابط أو من العنصر الأب
            val posterUrl = it.select("img").attr("src").ifEmpty { 
                container?.select("img")?.attr("src") ?: "" 
            }
            
            // العنوان غالباً يكون نص الرابط أو في عنوان قريب
            val title = it.text().trim().ifEmpty { 
                container?.select("h3, .title")?.text()?.trim() ?: "أنمي" 
            }
            
            if(posterUrl.isEmpty()) return@mapNotNull null

            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(posterUrl)
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1").text().trim()
        val poster = doc.select("img[class*='object-cover']").attr("src")
        val description = doc.select("p.font-light, div.story").text()

        // --- هنا استخدمنا الـ HTML الذي أرسلته لي للحلقات ---
        // نبحث عن الروابط التي تحتوي على 'episode'
        val episodes = doc.select("a[href*='/episode/']").mapNotNull {
            val href = it.attr("href")
            // البحث داخل div.video-data كما ظهر في الكود الخاص بك
            val videoData = it.select("div.video-data")
            
            // استخراج الاسم "الحلقة 1"
            val epName = videoData.select("span").text().trim()
            
            // استخراج الرقم من الاسم
            val epNum = Regex("(\\d+)").find(epName)?.value?.toIntOrNull()
            
            newEpisode(fixUrl(href)) {
                this.name = epName
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrl(poster)
            this.plot = description
            
            // هذا السطر يحل مشكلة الخطأ الأحمر (يجب تحديد النوع Sub)
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

        // --- هنا استخدمنا الـ HTML الذي أرسلته لي للتحميل ---
        // البحث عن زر يحتوي على كلمة download
        doc.select("a[href*='/download/']").forEach { link ->
            val downloadUrl = link.attr("href")
            val text = link.text() // النص مثل: "تحميل مباشر [334.66 ميغابايت]"
            
            // استخدام newExtractorLink لأن ExtractorLink القديمة تم إلغاؤها
            callback.invoke(
                newExtractorLink(
                    source = "Anime3rb Direct",
                    name = text,
                    url = downloadUrl,
                    referer = mainUrl,
                    quality = getQualityFromName(text)
                )
            )
        }

        // البحث عن السيرفرات (Iframe)
        doc.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            // ترتيب المتغيرات المصحح: الرابط، ثم الترجمة، ثم الرد
            loadExtractor(src, subtitleCallback, callback)
        }

        return true
    }
}
