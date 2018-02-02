/*
* This quest was generated by the StreetComplete QuestCreator (https://github.com/ENT8R/StreetCompleteQuestCreator)
*/

package de.westnordost.streetcomplete.quests.parking_fee;

import android.os.Bundle;

import java.util.Map;

import javax.inject.Inject;

import de.westnordost.streetcomplete.R;
import de.westnordost.streetcomplete.data.osm.SimpleOverpassQuestType;
import de.westnordost.streetcomplete.data.osm.changes.StringMapChangesBuilder;
import de.westnordost.streetcomplete.data.osm.download.OverpassMapDataDao;
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment;
import de.westnordost.streetcomplete.quests.YesNoQuestAnswerFragment;

public class AddParkingFee extends SimpleOverpassQuestType
{
	@Inject public AddParkingFee(OverpassMapDataDao overpassServer) { super(overpassServer); }

	@Override protected String getTagFilters() { return "nodes, ways with amenity=parking and access!=private and !fee"; }

	public AbstractQuestAnswerFragment createForm() { return new YesNoQuestAnswerFragment(); }

	public void applyAnswerTo(Bundle answer, StringMapChangesBuilder changes)
	{
		String yesno = answer.getBoolean(YesNoQuestAnswerFragment.ANSWER) ? "yes" : "no";
		changes.add("fee", yesno);
	}

	@Override public String getCommitMessage() { return "Add whether there is a parking fee"; }
	@Override public int getIcon() { return R.drawable.ic_quest_parking; }
	@Override public int getTitle(Map<String, String> tags) { return R.string.quest_parking_fee_title; }
}
