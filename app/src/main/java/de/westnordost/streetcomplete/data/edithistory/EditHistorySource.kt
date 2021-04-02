package de.westnordost.streetcomplete.data.edithistory

interface EditHistorySource {
    interface Listener {
        fun onAdded(edit: Edit)
        fun onSynced(edit: Edit)
        fun onDeleted(edits: List<Edit>)
        fun onInvalidated()
    }

    fun getMostRecentUndoable(): Edit?

    fun getAll(): List<Edit>

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}