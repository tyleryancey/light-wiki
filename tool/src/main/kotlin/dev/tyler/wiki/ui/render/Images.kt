package dev.tyler.wiki.ui.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.tyler.wiki.data.LruCache
import dev.tyler.wiki.data.WikiHosts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Article image loading: OkHttp fetch (two-host allowlist asserted) →
 * BitmapFactory bounds-decode → inSampleSize downsample → ImageBitmap,
 * behind a bounded in-memory LRU. Failure returns null — the renderer drops
 * the whole figure (image + caption), never an orphan caption.
 *
 * This path is a platform first (BitmapFactory appears nowhere in sdk/ or
 * examples/) — proven by the M6 spike on the AVD before anything built on it.
 */
object Images {

    private const val TAG = "LightWikiImages"
    private const val USER_AGENT = "LightWiki/0.1 (+https://github.com/tyleryancey/light-wiki)"

    /** Never decode wider than the panel (1080 px) or taller than two panelsful. */
    private const val MAX_WIDTH_PX = 1080
    private const val MAX_HEIGHT_PX = 2480

    /** ~24 thumbnails ≈ a few MB downsampled; bounded and process-scoped. */
    private val cache = LruCache<String, ImageBitmap>(24)

    private val client = OkHttpClient()

    /** Fetch + decode [url], or null on any failure (drop-figure contract). */
    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                WikiHosts.assertAllowed(url)
                val bytes = client.newCall(
                    Request.Builder().url(url).header("User-Agent", USER_AGENT).build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w(TAG, "HTTP ${response.code}: $url")
                        return@withContext null
                    }
                    response.body?.bytes()
                        ?: return@withContext null.also { android.util.Log.w(TAG, "empty body: $url") }
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                // Bound both dimensions: width to the panel, height to two
                // panelsful — keeps any single bitmap's bytes bounded even
                // for pathological tall/wide originals.
                val options = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(
                        sampleSize(bounds.outWidth, MAX_WIDTH_PX),
                        sampleSize(bounds.outHeight, MAX_HEIGHT_PX),
                    )
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: return@withContext null.also { android.util.Log.w(TAG, "decode failed: $url") }
                bitmap.asImageBitmap().also { cache.put(url, it) }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "image load failed: $url", e)
                null
            }
        }
    }

    /**
     * Power-of-two downsample factor so decoded width ≤ [maxWidth]
     * (integer division; at most one pixel of decoder rounding above).
     * The old `>= max` loop left decoded width in [max, 2·max) — M6 review.
     */
    internal fun sampleSize(width: Int, maxWidth: Int): Int {
        var sample = 1
        while (width / sample > maxWidth) sample *= 2
        return sample
    }
}
