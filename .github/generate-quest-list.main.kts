/**
 * Note: Run from the project's root directory like this:
 * kotlinc -script -Xplugin=/path/to/kotlinx-serialization-compiler-plugin.jar .github/generate-quest-list.main.kts
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.1")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
@file:Suppress("PLUGIN_IS_NOT_ENABLED")

import kotlin.text.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL

val projectDirectory = File(".")
val sourceDirectory = projectDirectory.resolve("app/src/main/java/de/westnordost/streetcomplete/")
val iconsDirectory = projectDirectory.resolve("res/graphics/quest icons/")

val csvFile = projectDirectory.resolve("quest-list.csv")

val noteQuestName = "OsmNoteQuest"
val noteQuestFile = sourceDirectory.resolve("data/osmnotes/notequests/OsmNoteQuestType.kt")

val wikiRowSpan2 = " rowspan=\"2\" |"

main()

@Serializable
data class Project(val name: String,
                   val description: String,
                   val project_url: String,
                   val doc_url: String,
                   val icon_url: String,
                   val contact_name: String)

@Serializable
data class TaginfoTag(val key: String,
                      val value: String,
                      val description: String,
                      val doc_url: String,
                      val icon_url: String)

data class TaginfoChange(val key: String,
                         val value: String,
                         val change: String)

@Serializable
data class Base(val data_url: String,
                val project: Project,
                val tags: List<TaginfoTag>,
                val data_format: Int = 1)

fun main() {
    println("Starting!")
    val questFileContent = sourceDirectory.resolve("quests/QuestModule.kt").readText()
    val questNameRegex = Regex("(?<=^ {8})[A-Z][a-zA-Z]+(?=\\()", RegexOption.MULTILINE)
    val questNames = listOf(noteQuestName) + questNameRegex.findAll(questFileContent).map { it.value }

    val questFiles = getFilesRecursively(sourceDirectory.resolve("quests/"))

    generateWikiCsv(questNames, questFiles)

    generateTaginfoJson(questNames, questFiles)
}

fun generateWikiCsv(questNames: List<String>, questFiles: List<File>) {
    val strings = getStrings(projectDirectory.resolve("app/src/main/res/values/strings.xml"))
    val wikiQuests = parseWikiTable(getWikiTableContent())
    val repoQuests = questNames.mapIndexed { defaultPriority, name ->
        getQuestWiki(name, defaultPriority, questFiles, strings, wikiQuests)
    }.sortedBy { it.wikiOrder }

    writeCsvFile(repoQuests, wikiQuests)
}

fun generateTaginfoJson(questNames: List<String>, questFiles: List<File>) {
    val project = Project(
        "StreetComplete",
        "Surveyor app for Android",
        "https://github.com/streetcomplete/StreetComplete",
        "https://wiki.openstreetmap.org/wiki/StreetComplete",
        "https://raw.githubusercontent.com/streetcomplete/StreetComplete/master/app/src/main/res/mipmap-xhdpi/ic_launcher.png",
        "Peter Newman"
    )

    val strings = getStrings(projectDirectory.resolve("app/src/main/res/values/strings.xml"))
    val taginfoTags = questNames.map { getQuestTaginfo(it, questFiles, strings) }.flatten()

    // TODO(Peter): Populate this
    val data = Base("foo.json", project, taginfoTags)

    println(Json { encodeDefaults = true; prettyPrint = true }.encodeToString<Base>(data))
}

data class RepoQuest(
    val name: String,
    val file: File,
    val icon: File,
    val title: String,
    val defaultPriority: Int,
    val wikiOrder: Int
) {
    val csvString: String get() {
        val wikiOrder = if (wikiOrder == -1) "\"???\"" else wikiOrder + 1
        return "\"$name\", \"$title\", \"${getIconUrl(icon)}\", ${defaultPriority + 1}, $wikiOrder"
    }
}

class WikiQuest(rowCells: List<String>, rowIndex: Int) {
    private val wikiOrder: Int = rowIndex
    private val icon: String
    val question: String
    private val askedForElements: String
    private val modifiedTags: String
    private val defaultPriority: String
    private val sinceVersion: String
    private val notes: String
    private val code: String

    init {
        val rowCellContents = rowCells.map {
            var cellContent = it

            if (it.startsWith(wikiRowSpan2)) {
                cellContent = it.substring(wikiRowSpan2.length)
            }
            else if (it.startsWith(" rowspan=") || it.startsWith(" colspan=")) {
                throw Error("Unsupported rowspan > 2 or colspan detected in table row $rowIndex: $it")
            }

            cellContent.trim()
        }

        icon = rowCellContents[0]
        question = rowCellContents[1]
        askedForElements = rowCellContents[2]
        modifiedTags = rowCellContents[3]
        defaultPriority = rowCellContents[4]
        sinceVersion = rowCellContents[5]
        notes = rowCellContents[6]
        code = rowCellContents[7]
    }

    fun isOutdated(repoQuests: List<RepoQuest>): Boolean = !repoQuests.any { it.wikiOrder == wikiOrder }

    val csvString: String get() = "\"???\", \"$question\", \"???\", \"???\", ${wikiOrder + 1}"
}

fun getFilesRecursively(directory: File): List<File> {
    return directory.listFiles()!!.flatMap {
        if (it.isDirectory) getFilesRecursively(it) else listOf(it)
    }
}

fun getIconUrl(icon: File): String {
        val iconsPath = icon.toRelativeString(projectDirectory).replace(" ", "%20")
        return "https://raw.githubusercontent.com/streetcomplete/StreetComplete/master/${iconsPath}"
}

fun getStrings(stringsFile: File): Map<String, String> {
    fun normalizeString(string: String) = string
        .trim('"') // strip optional quotes around the string
        .replace("\\n", "\n") // replace \n with real newline characters
        .replace(Regex("\\\\(['\"])"), "$1") // unescape quotes
        .replace(Regex("%s|%\\d\\\$s"), "[…]") // replace variables
        .replace("([…])", "[…]")
        .replace("[…] […]", "[…]")

    val stringRegex = Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
    val stringsContent = stringsFile.readText()

    return stringRegex.findAll(stringsContent).map { it.groupValues[1] to normalizeString(it.groupValues[2]) }.toMap()
}

fun getQuestWiki(
    questName: String,
    defaultPriority: Int,
    questFiles: List<File>,
    strings: Map<String, String>,
    wikiQuests: List<WikiQuest>
): RepoQuest {
    val file = getQuestFile(questName, questFiles)
    val questFileContent = file.readText()

    val questions = getQuestTitleStringNames(questName, questFileContent).map { strings[it]!! }
    val wikiOrder = wikiQuests.indexOfFirst { questions.contains(it.question) }
    val title = if (wikiOrder > -1) wikiQuests[wikiOrder].question else questions.last()

    val icon = getQuestIcon(questName, questFileContent)

    return RepoQuest(questName, file, icon, title, defaultPriority, wikiOrder)
}

fun getQuestTaginfo(
    questName: String,
    questFiles: List<File>,
    strings: Map<String, String>
): List<TaginfoTag> {
    val file = getQuestFile(questName, questFiles)
    println(file)
    val questFileContent = file.readText()

    val questConstants = getQuestConstants(questFileContent)
    println(questConstants)
    var questChanges = getQuestChanges(questFileContent)
    println(questChanges)

    val stringCheck = Regex("^\".+\"$")
    val fieldRegex = Regex("answer\\.(.+)$")

    val allChanges = mutableListOf<TaginfoChange>()
    for (questChange in questChanges) {
      println(questChange)
      var change = questChange[0]
      if (change.startsWith("get")) {
        // It's not actually a change, so discard it
        println("Discarding a get only change " + change)
        continue
      }
      // TODO: Rather than just dropping non-quote ones, ideally we need to deal with their variable names
      // Substitute any quest constants
      var key = questConstants.getOrDefault(questChange[1], questChange[1])
      if (!stringCheck.matches(key)) {
        println("Key '" + key + "' is not a string")
        continue
      } else {
        key = key.trim('"')
      }
      if (change.startsWith("delete")) {
        // It's a delete, so we don't expect, or care about the value
        allChanges.add(TaginfoChange(key, "", change))
        continue
      }
      // Tidy up some dodgy capturing
      // TODO(Peter): Fix this at source
      var value = questChange[2].replace("^,\\s+".toRegex(), "").trim('(')
      if (Regex("\\.toYesNo$").containsMatchIn(value)) {
        // Add both variants
        allChanges.add(TaginfoChange(key, "yes", change))
        allChanges.add(TaginfoChange(key, "no", change))
      } else if (Regex("\\.toString$").containsMatchIn(value) || Regex("\\.joinToString\\(").containsMatchIn(value)) {
        // No value to add
        // TODO: Check what value we should be setting
        allChanges.add(TaginfoChange(key, "", change))
      } else if (Regex("^when\\(answer").containsMatchIn(value)) {
        // TODO: Unpick this
        println(value + " is unknown when")
        //allChanges.add(TaginfoChange(key, "", change))
      } else {
        if (!stringCheck.matches(value)) {
          println("Value '" + value + "' is not a string")
          if (value.startsWith("if (")) {
            // TODO: Fix me!
            println("Skipping if statement")
            allChanges.add(TaginfoChange(key, "", change))
            continue
          }
          var questAnswerType = getQuestAnswerType(questFileContent)
          println(questAnswerType)
          for (it in questAnswerType) {
            println(it)
            if (it == "Boolean" || it == "String") {
              println("Skipping generic " + it + "...")
              allChanges.add(TaginfoChange(key, "", change))
              continue
            } else if (Regex("^List<").containsMatchIn(it)) {
              println("Skipping generic List...")
              allChanges.add(TaginfoChange(key, "", change))
              continue
            } else if (it == "SuspectedOnewayAnswer" || it == "CyclewayAnswer" || it == "SidewalkAnswer" || it == "ShopTypeAnswer" || it == "CompletedConstructionAnswer") {
              println("Skipping, it's complicated " + it + "...")
              allChanges.add(TaginfoChange(key, "", change))
              continue
            } else if (it == "RecyclingContainerMaterialsAnswer" || it == "SurfaceAnswer" || it == "MaxSpeedAnswer" || it == "MaxHeightAnswer" || it == "LanesAnswer") {
              // TODO: Fix me!
              println("Skipping, needs investigation " + it + "...")
              allChanges.add(TaginfoChange(key, "", change))
              continue
//            } else if (it == "BuildingType" || it == "DrinkingWater" || it == "PoliceType") {
//              // TODO: Fix me!
//              println("Skipping, complex enum " + it + "...")
//              allChanges.add(TaginfoChange(key, "", change))
//              continue
            } else if (it == "BusStopShelterAnswer" || it == "StepsRampAnswer") {
              // TODO: Fix me!
              println("Skipping, simple enum " + it + "...")
              allChanges.add(TaginfoChange(key, "", change))
              continue
            } else if (it == "RoadNameAnswer" || it == "PlaceNameAnswer" || it == "BusStopRefAnswer" || it == "HousenumberAnswer" || it == "PostboxRefAnswer") {
              println("Skipping just a name " + it + "...")
              allChanges.add(TaginfoChange(key, "", change))
              continue
            } else {
              val answerFile = getQuestFile("/" + it, questFiles)
              println("Checking " + answerFile + " for enums")
              val questAnswerTypeFileContent = answerFile.readText()

              //println(questAnswerTypeFileContent)

              if (fieldRegex.matches(value)) {
                val fieldName = fieldRegex.find(value)!!.groupValues[1]
                //if (fieldName != null) {
                  for (answerEnum in getQuestAnswerTypeEnums(questAnswerTypeFileContent).values) {
                    if (answerEnum.containsKey(fieldName)) {
                      allChanges.add(TaginfoChange(key, answerEnum.getOrDefault(fieldName, ""), change))
                    }
                  }
                //}
              }
            }
          }

          continue
        } else {
          value = value.trim('"')
        }
        allChanges.add(TaginfoChange(key, value, change))
      }
    }
    println(allChanges)

    val questions = getQuestTitleStringNames(questName, questFileContent).map { strings[it]!! }
    val title = questions.last()

    val iconUrl = getIconUrl(getQuestIcon(questName, questFileContent))

    return allChanges.distinct().map { TaginfoTag(it.key, it.value, it.change + " - " + title + " - " + questName, file.toString(), iconUrl)}
}

fun getQuestFile(questName: String, questFiles: List<File>): File {
    if (questName === noteQuestName) {
        return noteQuestFile
    }

    return questFiles.find { it.path.endsWith("$questName.kt") }
        ?: throw Error("Could not find quest file for quest $questName.")
}

fun getQuestConstants(questFileContent: String): Map<String, String> {
//private const val SOUND_SIGNALS = "traffic_signals:sound"
    val regex = Regex("const val ([^\\s]+)\\s*=\\s*(\"([^\"]+)\")")

    return regex.findAll(questFileContent).associate { it -> it.groupValues[1] to it.groupValues[2] }
}

fun getQuestChanges(questFileContent: String): List<List<String>> {
    //val regex = Regex("changes\\.[^\\(]\"([^\"])\"")
    //val regex = Regex("changes\\.[^\\(]+\\(\"([^\"]+)\"")
    val regex = Regex("\\s+changes\\.([^\\(]+)\\(((?>\"[^\"]+\"|[^,\\)]+))(,\\s(?>\"[^\"]+\"|[^\\)]+)|)\\)")

    //return regex.findAll(questFileContent).map { it.groupValues[1], it.groupValues[2], "" }.toList()
    return regex.findAll(questFileContent).map { it.groupValues.drop(1) }.toList()
}

fun getQuestAnswerType(questFileContent: String): List<String> {
//override fun applyAnswerTo(answer: PostboxRoyalCypher, changes: StringMapChangesBuilder) {
    val regex = Regex("applyAnswerTo\\(answer: ([^,]+), changes: StringMapChangesBuilder\\)")

    return regex.findAll(questFileContent).map { it.groupValues[1] }.toList()
}

fun getQuestAnswerTypeEnums(questFileContent: String): Map<String, Map<String, String>> {
//enum class ParkingType(val osmValue: String) {
//    SURFACE("surface"),
//    MULTI_STOREY("multi-storey"),
//}
    val enumRegex = Regex("enum class [^\\(]+\\(([^\\)]+)\\) \\{\\s*([^\\}]+)\\}")
    val fieldsRegex = Regex("val ([^:]+): [^,]+")
    val valsRegex = Regex("\\s*([^\\(]+)\\(([^\\)]+)\\),?")
    val valRegex = Regex("\\s*\"([^\"]+)\",?\\s*")

    val enumVals = enumRegex.find(questFileContent)?.groupValues
    val data: MutableMap<String, Map<String, String>> = mutableMapOf<String, Map<String, String>>()
    if (enumVals != null) {
      //println(fieldsRegex.findAll(enumVals[1]).map { it.groupValues[1] }.toList())
      for (it in valsRegex.findAll(enumVals[2])) {
        //println(it.groupValues[1] + " = " + it.groupValues[2])
        //println(valRegex.findAll(it.groupValues[2]).map { it.groupValues[1] }.toList())
        data.put(it.groupValues[1],
                 fieldsRegex.findAll(enumVals[1]).map { it.groupValues[1] }.toList().zip(
                   valRegex.findAll(it.groupValues[2]).map { it.groupValues[1] }.toList()
                 ).toMap())
      }
    }
    println(data)
    return data
}

fun getQuestTitleStringNames(questName: String, questFileContent: String): List<String> {
    val regex = Regex("(?<=R\\.string\\.)quest_\\w+")
    val stringResourceNames = regex.findAll(questFileContent).toList().map { it.value }

    if (stringResourceNames.isEmpty()) {
        throw Error("Could not find the title string reference for quest $questName.")
    }

    if (stringResourceNames.size == 1) {
        return stringResourceNames
    }

    return stringResourceNames.filter { it.contains("title") }
}

fun getQuestIcon(questName: String, questFileContent: String): File {
    val regex = Regex("(?<=override val icon = R\\.drawable\\.ic_quest_)\\w+")
    val iconName = regex.find(questFileContent)?.value
        ?: throw Error("Could not find the icon reference for quest $questName")

    val svgFile = iconsDirectory.resolve("$iconName.svg")

    if (svgFile.exists()) {
        return svgFile
    }

    throw Error("Could not find the SVG for icon '$iconName' (quest $questName).")
}

@Serializable
data class ApiWikiPage(val title: String, val pageid: Long, val wikitext: String)

@Serializable
data class ApiResult(val parse: ApiWikiPage)

fun getWikiTableContent(): String {
    val page = "StreetComplete/Quests"
    val section = 1 // "Released quest types" section

    val apiUrl = URL("https://wiki.openstreetmap.org/w/api.php?action=parse&format=json&prop=wikitext&formatversion=2&page=$page&section=$section")

    val jsonString = apiUrl.openStream().bufferedReader().use { it.readText() }

    return Json.decodeFromString<ApiResult>(jsonString).parse.wikitext
}

fun parseWikiTable(wikiPageContent: String): List<WikiQuest> {
    val tableRows = wikiPageContent.split("|-").toMutableList()

    tableRows.removeFirst() // Drop table header and everything before the table
    tableRows[tableRows.size - 1] = tableRows.last().split("|}")[0] // Drop everything after the table

    val cells = mutableListOf<List<String>>()

    // Fill "rowspan"ed columns
    tableRows.forEachIndexed { rowIndex, row ->
        val rowCells = row.split("\n|").toMutableList()
        rowCells.removeFirst() // Drop empty string between table row start `|-` and first column start `|`

        if (rowIndex > 0) {
            cells[rowIndex - 1].forEachIndexed { cellIndex, cell ->
                if (cell.startsWith(wikiRowSpan2)) {
                    rowCells.add(cellIndex, cell.substring(wikiRowSpan2.length))
                }
            }
        }

        cells += rowCells
    }

    return cells.mapIndexed { rowIndex, rowCells -> WikiQuest(rowCells, rowIndex) }
}

fun writeCsvFile(repoQuests: List<RepoQuest>, wikiQuests: List<WikiQuest>) {
    val (newQuests, oldQuests) = repoQuests.partition { it.wikiOrder == -1 }

    val csvLines =
        listOf("\"Quest Name\", \"Question\", \"SVG Icon URL\", \"Default Priority\", \"Wiki Order\"") +
        wikiQuests.filter { it.isOutdated(repoQuests) }.map { it.csvString } +
        listOf(",,,") +
        newQuests.map { it.csvString } +
        listOf(",,,") +
        oldQuests.map { it.csvString }

    csvFile.writeText(csvLines.joinToString("\n"))
}
