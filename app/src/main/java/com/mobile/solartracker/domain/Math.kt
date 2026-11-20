package com.mobile.solartracker.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Python's round(x, 2) rounds half-to-even; keep the same behaviour. */
internal fun Double.round2(): Double =
    BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_EVEN).toDouble()

internal fun Double.round1(): Double =
    BigDecimal.valueOf(this).setScale(1, RoundingMode.HALF_EVEN).toDouble()
