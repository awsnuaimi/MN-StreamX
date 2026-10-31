package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.shape.GenericShape

/** TV reference: chamfered primary action and selected language/category chip. */
internal val VodCutShape = GenericShape { size, _ ->
    val cut = minOf(size.width, size.height) * .24f
    moveTo(0f, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height - cut)
    close()
}