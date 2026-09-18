package com.iread.novel.ui.reader

import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan

data class ReaderPage(val start: Int, val end: Int, val top: Int, val bottom: Int)

/** One native layout drives both page boundaries and drawing; offsets always refer to source text. */
class MeasuredChapter(val body: String, width: Int, height: Int, fontPx: Float, spacing: Float) {
    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontPx
        typeface = Typeface.create("serif", Typeface.NORMAL)
    }
    private val indented = SpannableString(body).apply {
        var start = 0
        body.split('\n').forEach { paragraph ->
            val end = (start + paragraph.length + 1).coerceAtMost(body.length)
            if (end > start && !paragraph.startsWith("　") && !paragraph.startsWith(" "))
                setSpan(LeadingMarginSpan.Standard((fontPx * 2).toInt(), 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end
        }
    }
    val layout: StaticLayout = StaticLayout.Builder.obtain(indented, 0, body.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, spacing)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        .build()
    val pages: List<ReaderPage> = buildList {
        var first = 0
        while (first < layout.lineCount) {
            val top = layout.getLineTop(first)
            var end = first + 1
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= height.coerceAtLeast(1)) end++
            add(ReaderPage(layout.getLineStart(first), layout.getLineEnd(end - 1), top, layout.getLineBottom(end - 1)))
            first = end
        }
    }
    fun pageForOffset(offset: Int): Int = pages.indexOfLast { it.start <= offset }.coerceAtLeast(0)
    fun offsetAt(page: Int, verticalOffset: Int): Int = layout.getLineStart(
        layout.getLineForVertical(pages[page.coerceIn(pages.indices)].top + verticalOffset),
    )
}

internal fun readerAnchorForItemIndex(itemIndex: Int, paragraphStarts: List<Int>): Int =
    if (paragraphStarts.isEmpty()) 0 else paragraphStarts[(itemIndex - 1).coerceIn(0, paragraphStarts.lastIndex)]
