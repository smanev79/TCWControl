package com.tcwcontrol.core.parser

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

internal object Xml {
    fun parse(input: InputStream): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(input).documentElement
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: Exception) {
            // Not every platform parser knows every feature; status.xml has no DTD anyway.
        }
    }
}

internal fun Element.children(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

internal fun Element.child(name: String): Element? = children().firstOrNull { it.tagName == name }

internal fun Element.text(name: String): String = child(name)?.textContent?.trim().orEmpty()

/** Children whose tag is [prefix] followed by a number (S1, AI2, R3...), sorted by that number. */
internal fun Element.numbered(prefix: String): List<Pair<Int, Element>> =
    children().mapNotNull { el ->
        val n = el.tagName.removePrefix(prefix)
        if (n.length < el.tagName.length) n.toIntOrNull()?.let { it to el } else null
    }.sortedBy { it.first }
