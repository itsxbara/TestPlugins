package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Anime3rb : MainAPI() {
    // إعدادات الموقع الأساسية
    override var mainUrl = "https://anime3rb.com"
    override var name = "Anime3rb"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    // الصفحة الرئيسية (بحث وهمي لتجاوز تعقيدات الصفحة الرئيسية حالياً)
    override val mainPage = mainPageOf(
        "$mainUrl/search?q=" to "بحث",
    )

    // دالة البحث: تستخرج النتائج من بطاقات الموقع
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val res = app.get(url)
        val doc = res.document
        
        return doc.select("div.col-6.col-md-2, div.anime-card-container").mapNotNull {
            val title = it.select("h3, a.title, div.title").text()
            val href = it.select("a").attr("href")
            val posterUrl = it.select("img").attr("src")
            
            if(href.isEmpty()) return@mapNotNull null

            newAnimeSearchResponse(title, fixUrl(href), TvType.Anime) {
                this.posterUrl = fixUrl(posterUrl)
            }
        }
    }

    // دالة جلب التفاصيل والحلقات
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1.title, h1").text().trim()
        val poster = doc.select("img.poster, div.poster img").attr("src")
        val description = doc.select("div.story, p.synopsis").text()

        // استخراج الحلقات بالطريقة الجديدة (newEpisode)
        val episodes = doc.select("div.episodes-list a, div.episodes-card a").map {
            val epName = it.text()
            val epUrl = it.attr("href")
            val epNum = Regex("\\d+").find(epName)?.value?.toIntOrNull()

            newEpisode(fixUrl(epUrl)) {
                this.name = epName
                this.episode = epNum
            }
        }

        // إرجاع البيانات (تم إضافة DubStatus.Sub لحل مشكلة النوع)
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = fixUrl(poster)
            this.plot = description
            addEpisodes(DubStatus.Sub, episodes)
        }
    }

    // دالة استخراج الروابط والمشاهدة
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // 1. استخراج روابط التحميل المباشرة
        doc.select("a.download-link, a:contains(تحميل مباشر)").forEach { link ->
            val downloadUrl = link.attr("href")
            val qualityText = link.text() 
            val quality = getQualityFromName(qualityText)

            callback.invoke(
                ExtractorLink(
                    source = "Anime3rb Direct",
                    name = "Anime3rb Direct",
                    url = downloadUrl,
                    referer = mainUrl,
                    quality = quality
                )
            )
        }

        // 2. استخراج السيرفرات المدمجة (iframe)
        doc.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            // تصحيح الترتيب: subtitleCallback أولاً ثم callback
            loadExtractor(src, subtitleCallback, callback)
        }

        return true
    }
}
