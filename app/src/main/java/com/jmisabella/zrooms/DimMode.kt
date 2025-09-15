package com.jmisabella.zrooms

sealed class DimMode {
    data class Duration(val value: Double) : DimMode()
}