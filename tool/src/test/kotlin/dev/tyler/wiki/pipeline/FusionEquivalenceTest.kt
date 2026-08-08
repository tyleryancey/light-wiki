package dev.tyler.wiki.pipeline

import dev.tyler.wiki.parser.HtmlTree
import dev.tyler.wiki.pipeline.TreeTestSupport.ARTICLE_FIXTURES
import dev.tyler.wiki.pipeline.TreeTestSupport.articleFixture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * process() runs passes 2-4 fused into one rebuild; the sequential pass
 * functions remain in ArticlePipeline as the executable specification of
 * the donor pass order. This gate holds the two equal on every harvested
 * article, so the fusion can never silently drift from the specification.
 */
class FusionEquivalenceTest {

    @Test
    fun `fused local passes equal the sequential composition on every fixture`() {
        for (slug in ARTICLE_FIXTURES) {
            val dropped = ArticlePipeline.dropAppendixSections(
                HtmlTree.parse(articleFixture(slug)),
            )
            assertEquals(
                ArticlePipeline.stripClutter(
                    ArticlePipeline.fixImages(ArticlePipeline.stripLinks(dropped)),
                ),
                ArticlePipeline.fusedLocalPasses(dropped),
                "$slug: fused pipeline diverged from the sequential passes",
            )
        }
    }
}
