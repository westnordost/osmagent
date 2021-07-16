package de.westnordost.streetcomplete.quests.shop_type

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.LAST_CHECK_DATE_KEYS
import de.westnordost.streetcomplete.data.meta.SURVEY_MARK_KEY
import de.westnordost.streetcomplete.data.meta.toCheckDateString
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import java.time.LocalDate

class CheckShopType : OsmFilterQuestType<ShopTypeAnswer>() {

    override val elementFilter = """
        nodes, ways, relations with (
            shop = vacant
            or disused:shop
          ) and (
            older today -1 years
            or ${LAST_CHECK_DATE_KEYS.joinToString(" or ") { "$it < today -1 years" }}
          )
    """
    override val commitMessage = "Check if vacant shop is still vacant"
    override val wikiLink = "Tag:shop=vacant"
    override val icon = R.drawable.ic_quest_check_shop

    override fun getTitle(tags: Map<String, String>) = R.string.quest_shop_vacant_type_title

    override fun createForm() = ShopTypeForm()

    override fun applyAnswerTo(answer: ShopTypeAnswer, changes: StringMapChangesBuilder) {
        val otherCheckDateKeys = LAST_CHECK_DATE_KEYS.filterNot { it == SURVEY_MARK_KEY }
        for (otherCheckDateKey in otherCheckDateKeys) {
            changes.deleteIfExists(otherCheckDateKey)
        }
        when (answer) {
            is IsShopVacant -> {
                changes.addOrModify(SURVEY_MARK_KEY, LocalDate.now().toCheckDateString())
            }
            is ShopType -> {
                changes.deleteIfExists("disused:shop")
                if (!answer.tags.containsKey("shop")) {
                    changes.deleteIfExists("shop")
                }

                changes.deleteIfExists(SURVEY_MARK_KEY)
                for ((key, value) in answer.tags) {
                    changes.addOrModify(key, value)
                }

                // Remove any tags which need resetting or resurveying now
                changes.deleteIfExists("name")
                changes.deleteIfExists("ref")
                changes.deleteIfExists("loc_ref")
                changes.deleteIfExists("opening_hours")
                changes.deleteIfExists("operator")
                changes.deleteIfExists("brand")
                changes.deleteIfExists("brand:wikidata")
                // contact keys
                changes.deleteIfExists("phone")
                changes.deleteIfExists("fax")
                changes.deleteIfExists("website")
                changes.deleteIfExists("email")
                changes.deleteIfExists("contact:phone")
                changes.deleteIfExists("contact:mobile")
                changes.deleteIfExists("contact:fax")
                changes.deleteIfExists("contact:website")
                changes.deleteIfExists("contact:email")
                changes.deleteIfExists("contact:facebook")
                changes.deleteIfExists("contact:twitter")
                changes.deleteIfExists("contact:instagram")
                // payment keys
                changes.deleteIfExists("payment:cash")
                changes.deleteIfExists("payment:coins")
                changes.deleteIfExists("payment:notes")
                changes.deleteIfExists("payment:cheque")
                changes.deleteIfExists("payment:electronic_purses")
                changes.deleteIfExists("payment:cards")
                changes.deleteIfExists("payment:debit_cards")
                changes.deleteIfExists("payment:credit_cards")
                changes.deleteIfExists("payment:contactless")


            }
        }
    }
}
