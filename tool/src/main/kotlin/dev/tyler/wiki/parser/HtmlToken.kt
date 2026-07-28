package dev.tyler.wiki.parser

/**
 * Tokens produced by [HtmlLexer]. This is not a general HTML5 tokenizer —
 * it is scoped to the tag/attribute/entity repertoire observed in
 * MediaWiki `action=parse&prop=text` output, held honest by the fixtures
 * in test resources.
 */
sealed interface HtmlToken {
    /** `<name attr="v">`. [selfClosing] is true for `<name ... />`. */
    data class StartTag(
        val name: String,
        val attrs: Map<String, String>,
        val selfClosing: Boolean = false,
    ) : HtmlToken

    /** `</name>` */
    data class EndTag(val name: String) : HtmlToken

    /** Character data with entities already decoded. */
    data class Text(val text: String) : HtmlToken
}
