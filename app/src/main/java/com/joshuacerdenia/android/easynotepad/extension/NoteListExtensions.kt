package com.joshuacerdenia.android.easynotepad.extension

import com.joshuacerdenia.android.easynotepad.ORDER_CATEGORY
import com.joshuacerdenia.android.easynotepad.ORDER_DATE_CREATED
import com.joshuacerdenia.android.easynotepad.ORDER_TITLE
import com.joshuacerdenia.android.easynotepad.data.model.Note

fun List<Note>.sortedBy(order: Int): List<Note> {
    return when (order) {
        ORDER_DATE_CREATED -> this.sortedByDescending { it.dateCreated }
        ORDER_CATEGORY -> this.sortedBy { it.category }
        ORDER_TITLE -> this.sortedBy { it.title }
        else -> this.sortedByDescending { it.lastModified }
    }
}

fun List<Note>.queriedBy(_query: String): List<Note> {
    if (_query.isEmpty()) return this
    val query = _query.lowercase()
    return this.filter { note ->
        note.title.lowercase().contains(query)
            || note.category.lowercase().contains(query)
            || note.body.lowercase().contains(query)
    }
}