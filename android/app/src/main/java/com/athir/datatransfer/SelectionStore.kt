package com.athir.datatransfer

object SelectionStore {
    data class SelectedItem(
        val id: String,
        val name: String,
        val sizeMB: Double,
        val sizeBytes: Long,
        val uri: String,
        val mimeType: String
    )

    private val selectedItems = LinkedHashMap<String, SelectedItem>()

    @Synchronized
    fun add(item: SelectedItem) {
        selectedItems[item.id] = item
    }

    @Synchronized
    fun remove(id: String) {
        selectedItems.remove(id)
    }

    @Synchronized
    fun toggle(item: SelectedItem): Boolean {
        return if (selectedItems.containsKey(item.id)) {
            selectedItems.remove(item.id)
            false
        } else {
            selectedItems[item.id] = item
            true
        }
    }

    @Synchronized
    fun clear() {
        selectedItems.clear()
    }

    @Synchronized
    fun getAll(): List<SelectedItem> {
        return ArrayList(selectedItems.values)
    }

    @Synchronized
    fun getCount(): Int {
        return selectedItems.size
    }

    @Synchronized
    fun getTotalMB(): Double {
        var sum = 0.0
        for (item in selectedItems.values) {
            sum += item.sizeMB
        }
        return Math.round(sum * 10.0) / 10.0
    }

    @Synchronized
    fun getTotalBytes(): Long {
        var sum = 0L
        for (item in selectedItems.values) {
            sum += item.sizeBytes
        }
        return sum
    }
}
