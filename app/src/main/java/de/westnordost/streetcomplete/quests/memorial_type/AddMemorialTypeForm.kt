package de.westnordost.streetcomplete.quests.memorial_type

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AImageListQuestAnswerFragment
import de.westnordost.streetcomplete.view.image_select.Item

class AddMemorialTypeForm : AImageListQuestAnswerFragment<String,String>() {

    override val items = listOf(
        Item("statue", R.drawable.memorial_type_statue, R.string.quest_memorialType_statue),
        Item("bust", R.drawable.memorial_type_bust, R.string.quest_memorialType_bust),
        Item("plaque", R.drawable.memorial_type_plaque, R.string.quest_memorialType_plaque),
        Item("war_memorial", R.drawable.memorial_type_war_memorial, R.string.quest_memorialType_war_memorial),
        Item("stolperstein", R.drawable.memorial_type_stolperstein, R.string.quest_memorialType_stolperstein),
        Item("stone", R.drawable.memorial_type_stone, R.string.quest_memorialType_stone)
    )

    override val itemsPerRow = 3

    override fun onClickOk(selectedItems: List<String>) {
        applyAnswer(selectedItems.single())
    }
}
