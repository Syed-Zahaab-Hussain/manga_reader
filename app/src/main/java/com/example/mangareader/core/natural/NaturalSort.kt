package com.example.mangareader.core.natural

object NaturalSort {

    val STRING_COMPARATOR = Comparator<String> { a, b -> compare(a, b) }

    fun <T> sortedByString(list: List<T>, selector: (T) -> String): List<T> =
        list.sortedWith(compareBy(STRING_COMPARATOR, selector))

    fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val charA = a[i]
            val charB = b[j]
            if (charA.isDigit() && charB.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                val numberA = a.substring(i, endA).trimStart('0')
                val numberB = b.substring(j, endB).trimStart('0')
                val result = if (numberA.length != numberB.length) {
                    numberA.length.compareTo(numberB.length)
                } else {
                    numberA.compareTo(numberB)
                }
                if (result != 0) return result
                i = endA
                j = endB
            } else {
                val result = charA.lowercaseChar().compareTo(charB.lowercaseChar())
                if (result != 0) return result
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }
}
