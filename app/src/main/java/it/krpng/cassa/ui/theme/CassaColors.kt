package it.krpng.cassa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Soft purple wash for customized pizza rows in the draft list.
 * Uses Material tertiary so light/dark remain coherent without hardcoded brand colors.
 */
val ColorScheme.customizedPizzaRowBackground: Color
    get() = tertiary.copy(alpha = 0.22f)
