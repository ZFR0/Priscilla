package com.example.priscilla.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CircleShape
// This is the default, modern shape style
val ModernShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp)
)

val ImperialShapes = Shapes(
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp)
)

val NobleCutShapes = Shapes(
    small = CutCornerShape(8.dp),
    medium = CutCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
    large = CutCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
)

val PillowedShapes = Shapes(
    small = CircleShape,
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
)